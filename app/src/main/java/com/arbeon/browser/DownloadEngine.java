package com.arbeon.browser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Motor de downloads do Arbeon.
 *  - Velocidade: o arquivo é dividido em pedaços e baixado por várias conexões ao mesmo tempo.
 *  - Retomada: cada pedaço guarda quanto já foi gravado; falhas tentam de novo sozinhas
 *    e o progresso fica salvo no disco (sobrevive a fechar o app).
 *  - Atualizar link: troca a URL mantendo o progresso.
 *  - Simultâneos: 0 a 10 downloads ao mesmo tempo (0 = manual).
 *  - Notificações: uma por download (nome, %, velocidade, tempo restante, tamanho, barra e Cancelar).
 *    Se falhar, aparece o ícone "!" na barra de status até você atualizar o link.
 */
class DownloadEngine {
    static final int NOTIF_ID = 4711;
    private static final String CHANNEL = "arbeon_dl";
    private static final String CHANNEL_FAIL = "arbeon_dl_fail";
    private static final int MAX_RETRY = 8;
    private static final int BLUE = 0xFF1A73E8;
    private static final int RED = 0xFFD93025;

    /** Erro que não adianta repetir (link expirado, sem espaço...). */
    static class FatalEx extends IOException {
        FatalEx(String m) {
            super(m);
        }
    }

    private static DownloadEngine inst;

    static synchronized DownloadEngine get(Context c) {
        if (inst == null) inst = new DownloadEngine(c.getApplicationContext());
        return inst;
    }

    final CopyOnWriteArrayList<DlItem> items = new CopyOnWriteArrayList<>();
    private final Context ctx;
    private final SharedPreferences prefs;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object fileLock = new Object();
    private final Set<Integer> shown = new HashSet<>(); // só na thread principal
    private boolean ticking, serviceOn;
    private long lastSave;
    private PowerManager.WakeLock wake;

    private DownloadEngine(Context c) {
        ctx = c;
        prefs = c.getSharedPreferences("arbeon_dl", Context.MODE_PRIVATE);
        load();
    }

    // ------------------------------------------------------------------ configurações

    int maxSimultaneous() {
        return Math.max(0, Math.min(10, prefs.getInt("max", 3)));
    }

    void setMaxSimultaneous(int n) {
        prefs.edit().putInt("max", Math.max(0, Math.min(10, n))).apply();
        schedule();
    }

    int connections() {
        return Math.max(1, Math.min(16, prefs.getInt("conn", 8)));
    }

    void setConnections(int n) {
        prefs.edit().putInt("conn", Math.max(1, Math.min(16, n))).apply();
    }

    // ------------------------------------------------------------------ ações

    DlItem find(String id) {
        if (id == null) return null;
        for (DlItem i : items) if (id.equals(i.id)) return i;
        return null;
    }

    DlItem add(String url, String ua, String referer, String mime, String name) {
        DlItem it = new DlItem();
        it.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        it.url = url;
        it.ua = ua;
        it.referer = referer;
        it.mime = mime;
        it.fileName = safeName(name);
        it.added = System.currentTimeMillis();
        if (maxSimultaneous() == 0) {
            it.state = DlItem.PAUSED;
            it.error = "Modo manual (0 simultâneos): toque em ▶ para iniciar.";
        } else {
            it.state = DlItem.QUEUED;
        }
        items.add(0, it);
        save();
        askNotifPermission();
        schedule();
        return it;
    }

    synchronized void resume(DlItem it) {
        if (it.state != DlItem.PAUSED && it.state != DlItem.FAILED) return;
        it.error = "";
        it.stop = false;
        it.state = DlItem.QUEUED;
        it.force = maxSimultaneous() == 0;
        clearAlert(it);
        schedule();
    }

    void pause(final DlItem it) {
        if (it.state == DlItem.RUNNING) {
            it.stop = true;
            pool.execute(() -> disconnectAll(it));
        } else if (it.state == DlItem.QUEUED) {
            it.state = DlItem.PAUSED;
            it.force = false;
            it.error = "Pausado";
            save();
        }
    }

    void remove(final DlItem it) {
        if (it.state == DlItem.RUNNING) {
            it.removeAfter = true;
            it.stop = true;
            pool.execute(() -> disconnectAll(it));
        } else {
            items.remove(it);
            if (it.state != DlItem.DONE) part(it).delete();
            save();
            schedule();
        }
        clearAlert(it);
    }

    void clearDone() {
        for (DlItem it : items) if (it.state == DlItem.DONE) items.remove(it);
        save();
    }

    /** Troca o link mantendo o progresso e continua o download. */
    void updateLink(DlItem it, String newUrl) {
        it.url = newUrl;
        it.error = "";
        if (it.state == DlItem.RUNNING) {
            it.restartAfter = true;
            it.stop = true;
            final DlItem x = it;
            pool.execute(() -> disconnectAll(x));
        } else if (it.state != DlItem.DONE) {
            if (it.state == DlItem.QUEUED) it.state = DlItem.PAUSED;
            resume(it);
        }
        clearAlert(it);
        save();
    }

    // ------------------------------------------------------------------ fila

    synchronized void schedule() {
        int max = maxSimultaneous();
        int running = 0;
        for (DlItem i : items) if (i.state == DlItem.RUNNING) running++;
        for (int k = items.size() - 1; k >= 0; k--) {
            DlItem i = items.get(k);
            if (i.state != DlItem.QUEUED) continue;
            if (i.force || running < max) {
                i.force = false;
                start(i);
                running++;
            }
        }
        updateService();
        ensureTick();
    }

    private void start(final DlItem it) {
        it.stop = false;
        it.error = "";
        it.state = DlItem.RUNNING;
        clearAlert(it);
        pool.execute(() -> run(it));
    }

    private void disconnectAll(DlItem it) {
        synchronized (it.conns) {
            for (HttpURLConnection c : it.conns) {
                try {
                    c.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------------ execução

    private void run(DlItem it) {
        try {
            work(it);
            if (it.stop) {
                it.state = DlItem.PAUSED;
                it.error = "Pausado";
            } else {
                it.finalizing = true;
                publish(it);
                it.finalizing = false;
                it.state = DlItem.DONE;
                it.error = "";
                notifyDone(it);
            }
        } catch (Exception e) {
            it.finalizing = false;
            if (it.stop) {
                it.state = DlItem.PAUSED;
                it.error = "Pausado";
            } else {
                it.state = DlItem.FAILED;
                it.error = friendly(e);
                notifyFailed(it);
            }
        } finally {
            it.speed = 0;
            boolean again = it.restartAfter;
            it.restartAfter = false;
            if (it.removeAfter) {
                items.remove(it);
                part(it).delete();
                clearAlert(it);
            } else if (again && it.state != DlItem.DONE) {
                it.error = "";
                it.state = DlItem.QUEUED;
                it.force = true;
                clearAlert(it);
            }
            it.stop = false;
            save();
            schedule();
        }
    }

    private void work(final DlItem it) throws Exception {
        probe(it);
        if (it.cStart == null) plan(it);
        final File part = part(it);
        if (it.total > 0) {
            long remaining = it.total - it.done();
            File dir = part.getParentFile();
            if (dir != null && dir.getUsableSpace() < remaining + (8L << 20)) {
                throw new FatalEx("Sem espaço livre no aparelho.");
            }
            RandomAccessFile pre = new RandomAccessFile(part, "rw");
            try {
                if (pre.length() != it.total) pre.setLength(it.total);
            } finally {
                pre.close();
            }
        } else if (!part.exists()) {
            part.createNewFile();
        }

        final int chunks = it.cStart.length;
        int workers = it.ranges ? Math.min(connections(), Math.max(1, chunks)) : 1;
        final AtomicInteger next = new AtomicInteger(0);
        final AtomicReference<Exception> err = new AtomicReference<>();
        Thread[] ts = new Thread[workers];
        for (int w = 0; w < workers; w++) {
            ts[w] = new Thread(() -> {
                try {
                    while (!it.stop && err.get() == null) {
                        int idx = next.getAndIncrement();
                        if (idx >= chunks) break;
                        fetchChunk(it, part, idx);
                    }
                } catch (Exception e) {
                    if (!it.stop && err.compareAndSet(null, e)) disconnectAll(it);
                }
            }, "arbeon-dl-" + w);
            ts[w].start();
        }
        for (Thread t : ts) {
            try {
                t.join();
            } catch (InterruptedException ie) {
                it.stop = true;
            }
        }
        Exception e = err.get();
        if (e != null) throw e;
        if (!it.stop && it.total > 0 && it.done() < it.total) {
            throw new IOException("Download incompleto, tente continuar.");
        }
    }

    /** Descobre tamanho e se o servidor aceita continuar (Range). Roda a cada início/retomada. */
    private void probe(DlItem it) throws IOException {
        HttpURLConnection c = open(it, 0, 0);
        try {
            int code = c.getResponseCode();
            long total = -1;
            boolean ranges = false;
            if (code == 206) {
                String cr = c.getHeaderField("Content-Range");
                if (cr != null && cr.indexOf('/') >= 0) {
                    String t = cr.substring(cr.indexOf('/') + 1).trim();
                    if (!t.equals("*")) {
                        try {
                            total = Long.parseLong(t);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                ranges = total > 0;
            } else if (code == 200) {
                total = c.getContentLengthLong();
            } else if (code == 416) {
                total = 0;
            } else {
                throwHttp(code);
            }
            if (it.cStart != null) {
                boolean sizeChanged = it.total > 0 && total > 0 && total != it.total;
                boolean lostRange = it.done() > 0 && !ranges;
                if (sizeChanged || lostRange) {
                    it.cStart = null;
                    it.cEnd = null;
                    it.cDone = null;
                    part(it).delete();
                }
            }
            it.total = total;
            it.ranges = ranges;
            if (it.mime == null || it.mime.isEmpty()) it.mime = c.getContentType();
        } finally {
            c.disconnect();
        }
    }

    private void plan(DlItem it) {
        long total = it.total;
        int count;
        long[] cs, ce;
        if (total == 0) {
            cs = new long[0];
            ce = new long[0];
        } else if (total < 0 || !it.ranges) {
            cs = new long[]{0};
            ce = new long[]{total > 0 ? total - 1 : -1};
        } else {
            int n = connections();
            long chunk = Math.max(1L << 20, Math.min(16L << 20, total / (n * 4L)));
            count = (int) ((total + chunk - 1) / chunk);
            cs = new long[count];
            ce = new long[count];
            for (int i = 0; i < count; i++) {
                cs[i] = i * chunk;
                ce[i] = Math.min(total - 1, (i + 1) * chunk - 1);
            }
        }
        it.cEnd = ce;
        it.cDone = new AtomicLongArray(cs.length);
        it.cStart = cs;
    }

    private void fetchChunk(DlItem it, File part, int idx) throws IOException {
        long cs = it.cStart[idx], ce = it.cEnd[idx];
        int attempt = 0;
        byte[] buf = new byte[128 * 1024];
        while (!it.stop) {
            long have = it.cDone.get(idx);
            if (ce >= 0 && cs + have > ce) return;
            if (!it.ranges && have > 0) {
                it.cDone.set(idx, 0);
                have = 0;
            }
            HttpURLConnection c = null;
            boolean ok = false;
            try {
                c = open(it, it.ranges ? cs + have : -1, it.ranges ? ce : -1);
                synchronized (it.conns) {
                    it.conns.add(c);
                }
                int code = c.getResponseCode();
                if (it.ranges && code == 200) {
                    throw new FatalEx("O servidor não aceitou continuar o download. Remova e baixe de novo.");
                }
                if (code == 416) {
                    throw new FatalEx("O arquivo mudou no servidor. Remova e baixe de novo.");
                }
                if (code != 200 && code != 206) throwHttp(code);

                long want = ce >= 0 ? (ce - cs + 1 - have) : Long.MAX_VALUE;
                InputStream in = c.getInputStream();
                RandomAccessFile raf = new RandomAccessFile(part, "rw");
                try {
                    raf.seek(cs + have);
                    int r;
                    while (!it.stop && want > 0
                            && (r = in.read(buf, 0, (int) Math.min(buf.length, want))) != -1) {
                        raf.write(buf, 0, r);
                        it.cDone.addAndGet(idx, r);
                        want -= r;
                    }
                } finally {
                    raf.close();
                    in.close();
                }
                if (it.stop) return;
                if (ce >= 0 && want > 0) throw new IOException("Conexão encerrada antes do fim");
                if (ce < 0) it.total = it.cDone.get(idx);
                ok = true;
                return;
            } catch (FatalEx e) {
                throw e;
            } catch (IOException e) {
                if (it.stop) return;
                if (it.cDone.get(idx) > have) attempt = 0;
                if (++attempt > MAX_RETRY) throw e;
                long wait = Math.min(1000L << (attempt - 1), 10000L);
                for (long t = 0; t < wait && !it.stop; t += 100) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            } finally {
                if (c != null) {
                    synchronized (it.conns) {
                        it.conns.remove(c);
                    }
                    if (!ok) c.disconnect(); // sucesso: mantém a conexão viva para o próximo pedaço
                }
            }
        }
    }

    private void throwHttp(int code) throws IOException {
        if (code == 401 || code == 403 || code == 404 || code == 410) {
            throw new FatalEx("Link expirado ou recusado (HTTP " + code + "). Toque em 🔗 Atualizar link.");
        }
        throw new IOException("HTTP " + code);
    }

    private HttpURLConnection open(DlItem it, long from, long to) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(it.url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("Accept", "*/*");
        if (it.ua != null && !it.ua.isEmpty()) c.setRequestProperty("User-Agent", it.ua);
        if (it.referer != null && !it.referer.isEmpty()) c.setRequestProperty("Referer", it.referer);
        String ck = null;
        try {
            ck = CookieManager.getInstance().getCookie(it.url);
        } catch (Exception ignored) {
        }
        if (ck != null) c.setRequestProperty("Cookie", ck);
        if (from >= 0) c.setRequestProperty("Range", "bytes=" + from + "-" + (to >= 0 ? String.valueOf(to) : ""));
        return c;
    }

    private String friendly(Exception e) {
        if (e instanceof UnknownHostException) return "Sem internet ou servidor não encontrado.";
        if (e instanceof SocketTimeoutException) return "Tempo esgotado. Verifique a conexão.";
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    // ------------------------------------------------------------------ arquivo final

    File part(DlItem it) {
        File dir = ctx.getExternalFilesDir("downloads");
        if (dir == null) dir = new File(ctx.getFilesDir(), "downloads");
        dir.mkdirs();
        return new File(dir, it.id + ".part");
    }

    static String safeName(String n) {
        if (n == null || n.trim().isEmpty()) n = "download.bin";
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int i = 1;
        while (f.exists()) f = new File(dir, base + " (" + (i++) + ")" + ext);
        return f;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[256 * 1024];
        int r;
        while ((r = in.read(b)) != -1) out.write(b, 0, r);
        out.flush();
    }

    private void publish(DlItem it) throws IOException {
        File part = part(it);
        String name = safeName(it.fileName);
        String mime = it.mime == null || it.mime.isEmpty() ? "application/octet-stream" : it.mime;
        int semi = mime.indexOf(';');
        if (semi > 0) mime = mime.substring(0, semi).trim();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("Não foi possível criar o arquivo em Downloads.");
            OutputStream out = cr.openOutputStream(uri);
            if (out == null) throw new IOException("Não foi possível gravar em Downloads.");
            FileInputStream in = new FileInputStream(part);
            try {
                copy(in, out);
            } finally {
                in.close();
                out.close();
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            it.savedUri = uri.toString();
            part.delete();
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            dir.mkdirs();
            File dest = unique(dir, name);
            if (!part.renameTo(dest)) {
                FileInputStream in = new FileInputStream(part);
                FileOutputStream out = new FileOutputStream(dest);
                try {
                    copy(in, out);
                } finally {
                    in.close();
                    out.close();
                }
                part.delete();
            }
            it.savedUri = dest.getAbsolutePath();
        }
    }

    // ------------------------------------------------------------------ persistência

    void save() {
        synchronized (fileLock) {
            try {
                JSONArray a = new JSONArray();
                for (DlItem it : items) {
                    JSONObject o = it.toJson();
                    if (o != null) a.put(o);
                }
                File f = new File(ctx.getFilesDir(), "downloads.json");
                File tmp = new File(ctx.getFilesDir(), "downloads.json.tmp");
                FileOutputStream out = new FileOutputStream(tmp);
                try {
                    out.write(a.toString().getBytes("UTF-8"));
                } finally {
                    out.close();
                }
                tmp.renameTo(f);
            } catch (Exception ignored) {
            }
        }
    }

    private void load() {
        try {
            File f = new File(ctx.getFilesDir(), "downloads.json");
            if (!f.exists()) return;
            FileInputStream in = new FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            copy(in, bo);
            in.close();
            JSONArray a = new JSONArray(bo.toString("UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) items.add(DlItem.fromJson(o));
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ velocidade e serviço

    private void ensureTick() {
        main.post(() -> {
            if (!ticking) {
                ticking = true;
                tick.run();
            }
        });
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            int active = 0;
            for (DlItem it : items) {
                if (it.state == DlItem.RUNNING) {
                    long d = it.done();
                    long dt = now - it.lastTime;
                    if (it.lastTime > 0 && dt > 0) {
                        double inst = (d - it.lastBytes) * 1000.0 / dt;
                        it.speed = it.speed <= 0 ? inst : it.speed * 0.6 + inst * 0.4;
                    }
                    it.lastBytes = d;
                    it.lastTime = now;
                    active++;
                } else {
                    it.speed = 0;
                    it.lastTime = 0;
                }
            }
            if (active > 0) {
                if (now - lastSave > 3000) {
                    lastSave = now;
                    final DownloadEngine self = DownloadEngine.this;
                    pool.execute(self::save);
                }
                refreshNotifications();
                main.postDelayed(this, 1000);
            } else {
                ticking = false;
                clearShown();
            }
        }
    };

    private synchronized void updateService() {
        boolean any = false;
        for (DlItem it : items) if (it.state == DlItem.RUNNING) any = true;
        if (any && !serviceOn) {
            try {
                Intent i = new Intent(ctx, DlService.class);
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
                else ctx.startService(i);
                serviceOn = true;
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "arbeon:dl");
                    wake.setReferenceCounted(false);
                    wake.acquire(6 * 60 * 60 * 1000L);
                }
            } catch (Exception ignored) {
            }
        } else if (!any && serviceOn) {
            try {
                ctx.stopService(new Intent(ctx, DlService.class));
            } catch (Exception ignored) {
            }
            serviceOn = false;
            try {
                if (wake != null && wake.isHeld()) wake.release();
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ notificações

    /** Id da notificação de progresso de um download. */
    static int nid(DlItem it) {
        return 20000 + (it.id.hashCode() & 0xFFFFF);
    }

    /** Id da notificação de resultado (concluído ou falha) de um download. */
    static int rid(DlItem it) {
        return 2000000 + (it.id.hashCode() & 0xFFFFF);
    }

    private NotificationManager nm() {
        return (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void channels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = nm();
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Downloads",
                NotificationManager.IMPORTANCE_LOW));
        NotificationChannel f = new NotificationChannel(CHANNEL_FAIL, "Falhas de download",
                NotificationManager.IMPORTANCE_DEFAULT);
        f.setSound(null, null);
        f.enableVibration(false);
        nm.createNotificationChannel(f);
    }

    private Notification.Builder builder(String channel) {
        channels();
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(ctx, channel);
        return new Notification.Builder(ctx);
    }

    private PendingIntent openApp(int req) {
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, req, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification.Action cancelAction(DlItem it) {
        Intent i = new Intent(ctx, DlActionReceiver.class)
                .setAction(DlActionReceiver.CANCEL)
                .putExtra("id", it.id);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, nid(it), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(
                Icon.createWithResource(ctx, R.drawable.ic_stat_cancel), "Cancelar", pi).build();
    }

    private static String clock(long secs) {
        if (secs < 0) secs = 0;
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        if (h > 0) return h + ":" + (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    /** Notificação de progresso: nome, "% • velocidade • faltam mm:ss", "feito / total" e barra. */
    private Notification progressNotification(DlItem it) {
        long done = it.done(), total = it.total;
        boolean known = total > 0;
        int pct = known ? (int) Math.min(100, done * 100 / total) : 0;
        double speed = it.speed;

        StringBuilder line = new StringBuilder();
        if (it.finalizing) {
            line.append("Finalizando…");
        } else {
            line.append(known ? pct + "%" : "Baixando");
            if (speed > 0) line.append(" • ").append(Formatter.formatFileSize(ctx, (long) speed)).append("/s");
            if (known && speed > 1) {
                line.append(" • faltam ").append(clock((long) ((total - done) / speed)));
            }
        }
        String sizes = known
                ? Formatter.formatFileSize(ctx, done) + " / " + Formatter.formatFileSize(ctx, total)
                : Formatter.formatFileSize(ctx, done);

        Notification.Builder b = builder(CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setColor(BLUE)
                .setContentTitle(it.fileName)
                .setContentText(line.toString())
                .setSubText(sizes)
                .setProgress(100, pct, !known || it.finalizing)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(openApp(1))
                .addAction(cancelAction(it));
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    /** Notificação do serviço em primeiro plano: mostra o primeiro download em andamento. */
    Notification notification(String text) {
        for (DlItem it : items) {
            if (it.state == DlItem.RUNNING) return progressNotification(it);
        }
        return builder(CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setColor(BLUE)
                .setContentTitle("Arbeon")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(0))
                .build();
    }

    /** Atualiza uma notificação por download (a primeira fica no serviço em primeiro plano). */
    private void refreshNotifications() {
        NotificationManager nm = nm();
        Set<Integer> now = new HashSet<>();
        boolean primary = true;
        for (DlItem it : items) {
            if (it.state != DlItem.RUNNING) continue;
            int id = primary ? NOTIF_ID : nid(it);
            primary = false;
            try {
                nm.notify(id, progressNotification(it));
            } catch (Exception ignored) {
            }
            if (id != NOTIF_ID) now.add(id);
        }
        for (Integer id : shown) if (!now.contains(id)) nm.cancel(id);
        shown.clear();
        shown.addAll(now);
    }

    private void clearShown() {
        try {
            NotificationManager nm = nm();
            for (Integer id : shown) nm.cancel(id);
        } catch (Exception ignored) {
        }
        shown.clear();
    }

    /** Remove o aviso de falha ou de concluído de um download. */
    private void clearAlert(final DlItem it) {
        main.post(() -> {
            try {
                nm().cancel(rid(it));
            } catch (Exception ignored) {
            }
        });
    }

    private void notifyDone(final DlItem it) {
        main.post(() -> {
            try {
                String mime = it.mime == null ? "" : it.mime;
                int semi = mime.indexOf(';');
                if (semi > 0) mime = mime.substring(0, semi).trim();
                PendingIntent pi = openApp(2);
                if (Build.VERSION.SDK_INT >= 29 && it.savedUri != null && it.savedUri.startsWith("content:")
                        && !mime.isEmpty() && !mime.equals("application/octet-stream")) {
                    Intent v = new Intent(Intent.ACTION_VIEW);
                    v.setDataAndType(Uri.parse(it.savedUri), mime);
                    v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    pi = PendingIntent.getActivity(ctx, rid(it), v,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                }
                String text = "Download concluído"
                        + (it.total > 0 ? " • " + Formatter.formatFileSize(ctx, it.total) : "");
                Notification.Builder b = builder(CHANNEL)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setColor(BLUE)
                        .setContentTitle(it.fileName)
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setContentIntent(pi);
                nm().notify(rid(it), b.build());
            } catch (Exception ignored) {
            }
        });
    }

    /** Falha: ícone "!" na barra de status até você atualizar o link (ou remover o download). */
    private void notifyFailed(final DlItem it) {
        main.post(() -> {
            try {
                if (it.state != DlItem.FAILED) return;
                String err = it.error == null || it.error.isEmpty() ? "Erro desconhecido." : it.error;
                Notification.Builder b = builder(CHANNEL_FAIL)
                        .setSmallIcon(R.drawable.ic_stat_fail)
                        .setColor(RED)
                        .setContentTitle(it.fileName)
                        .setContentText("Falha no download • toque para abrir")
                        .setStyle(new Notification.BigTextStyle()
                                .bigText(err + "\nAbra ⬇ Downloads e toque em Atualizar link."))
                        .setCategory(Notification.CATEGORY_ERROR)
                        .setOngoing(false)
                        .setAutoCancel(false)
                        .setContentIntent(openApp(3));
                nm().notify(rid(it), b.build());
            } catch (Exception ignored) {
            }
        });
    }

    /** Android 13+: pede a permissão de notificações (no máximo 2 vezes). */
    private void askNotifPermission() {
        try {
            if (Build.VERSION.SDK_INT < 33) return;
            if (ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED) return;
            int asked = prefs.getInt("notif_ask", 0);
            if (asked >= 2) return;
            prefs.edit().putInt("notif_ask", asked + 1).apply();
            Intent i = new Intent(ctx, NotifPermActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }
}
