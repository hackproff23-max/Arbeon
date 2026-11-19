package com.arbeon.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DNS de bloqueio (DNS sobre HTTPS): cada domínio pedido pela página é consultado na DNS escolhida;
 * se ela responder bloqueado (0.0.0.0, NXDOMAIN ou REFUSED) o pedido é barrado.
 * Também segura redirecionamentos automáticos entre sites e a abertura automática de outros apps.
 */
final class AdDns {
    static final AtomicInteger blocked = new AtomicInteger();

    static final String[] NAMES = {
            "AdGuard DNS (recomendado)",
            "AdGuard DNS Família (bloqueia adultos)",
            "Mullvad (anúncios e rastreadores)",
            "Personalizada"
    };
    static final String[] URLS = {
            "https://dns.adguard-dns.com/dns-query",
            "https://family.adguard-dns.com/dns-query",
            "https://adblock.dns.mullvad.net/dns-query",
            ""
    };

    private static final class Entry {
        final boolean blocked;
        final long exp;

        Entry(boolean b, long e) {
            blocked = b;
            exp = e;
        }
    }

    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static volatile long pauseUntil;
    private static int fails;
    private static long lastToast;

    private AdDns() {
    }

    // ------------------------------------------------------------------ opções

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("arbeon", Context.MODE_PRIVATE);
    }

    static String url(Context c) {
        return p(c).getString("dns_url", "");
    }

    static boolean on(Context c) {
        return p(c).getBoolean("dns_on", false);
    }

    static boolean enabled(Context c) {
        return on(c) && !url(c).isEmpty();
    }

    static boolean guardRedirects(Context c) {
        return p(c).getBoolean("redir_guard", true);
    }

    static void save(Context c, boolean on, String url, boolean guard) {
        p(c).edit().putBoolean("dns_on", on).putString("dns_url", url == null ? "" : url)
                .putBoolean("redir_guard", guard).apply();
        CACHE.clear();
        fails = 0;
        pauseUntil = 0;
    }

    /** Aceita https://.../dns-query ou só o nome (dns.exemplo.com). Devolve null se inválido. */
    static String normalize(String in) {
        if (in == null) return null;
        String s = in.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty() || s.contains(" ")) return null;
        if (s.contains("://")) {
            if (!s.startsWith("https://")) return null;
        } else if (s.indexOf('/') < 0) {
            s = "https://" + s + "/dns-query";
        } else {
            s = "https://" + s;
        }
        String host = Uri.parse(s).getHost();
        if (host == null || !host.contains(".")) return null;
        return s;
    }

    // ------------------------------------------------------------------ consulta DoH

    private static byte[] buildQuery(String host) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0);
        o.write(0);   // id
        o.write(1);
        o.write(0);   // flags: RD
        o.write(0);
        o.write(1);   // 1 pergunta
        for (int i = 0; i < 6; i++) o.write(0);
        for (String label : host.split("\\.")) {
            byte[] b = label.getBytes(StandardCharsets.US_ASCII);
            if (b.length == 0 || b.length > 63) throw new IllegalArgumentException("label");
            o.write(b.length);
            o.write(b, 0, b.length);
        }
        o.write(0);
        o.write(0);
        o.write(1);   // tipo A
        o.write(0);
        o.write(1);   // classe IN
        return o.toByteArray();
    }

    private static int skipName(byte[] r, int p) {
        while (p < r.length) {
            int l = r[p] & 255;
            if (l == 0) return p + 1;
            if ((l & 0xC0) == 0xC0) return p + 2;
            p += l + 1;
        }
        return -1;
    }

    /** true = bloqueado, false = liberado, null = não deu para saber. */
    private static Boolean parse(byte[] r) {
        if (r.length < 12) return null;
        int rcode = r[3] & 0x0F;
        if (rcode == 3 || rcode == 5) return Boolean.TRUE;
        if (rcode != 0) return null;
        int qd = ((r[4] & 255) << 8) | (r[5] & 255);
        int an = ((r[6] & 255) << 8) | (r[7] & 255);
        int p = 12;
        for (int i = 0; i < qd; i++) {
            p = skipName(r, p);
            if (p < 0) return null;
            p += 4;
        }
        for (int i = 0; i < an; i++) {
            p = skipName(r, p);
            if (p < 0 || p + 10 > r.length) return null;
            int type = ((r[p] & 255) << 8) | (r[p + 1] & 255);
            int len = ((r[p + 8] & 255) << 8) | (r[p + 9] & 255);
            p += 10;
            if (p + len > r.length) return null;
            if (type == 1 && len == 4 && r[p] == 0 && r[p + 1] == 0 && r[p + 2] == 0 && r[p + 3] == 0) {
                return Boolean.TRUE;
            }
            p += len;
        }
        return Boolean.FALSE;
    }

    static Boolean query(String endpoint, String host) {
        HttpURLConnection c = null;
        try {
            byte[] q = buildQuery(host);
            c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setConnectTimeout(2500);
            c.setReadTimeout(2500);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/dns-message");
            c.setRequestProperty("Accept", "application/dns-message");
            OutputStream os = c.getOutputStream();
            os.write(q);
            os.close();
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 8192) break;
            }
            in.close();
            return parse(bo.toByteArray());
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static boolean isBlocked(Context c, String host) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (!h.matches("[a-z0-9.-]+") || h.equals("localhost") || h.matches("[0-9.]+")) return false;
        String ep = url(c);
        if (ep.isEmpty()) return false;
        String dohHost = Uri.parse(ep).getHost();
        if (dohHost != null && dohHost.equalsIgnoreCase(h)) return false;
        long now = System.currentTimeMillis();
        Entry e = CACHE.get(h);
        if (e != null && e.exp > now) return e.blocked;
        if (now < pauseUntil) return false;
        Boolean r = query(ep, h);
        if (r == null) {
            if (++fails >= 3) {
                pauseUntil = now + 60000;
                fails = 0;
            }
            return false;
        }
        fails = 0;
        CACHE.put(h, new Entry(r, now + 30L * 60 * 1000));
        return r;
    }

    // ------------------------------------------------------------------ ganchos do WebView

    private static WebResourceResponse page(String host) {
        String html = "<html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='font-family:sans-serif;background:#F4F7FF;color:#0B1B4D;"
                + "text-align:center;padding:64px 24px'>"
                + "<h2>Bloqueado pela DNS</h2><p>" + host + "</p>"
                + "<p style='color:#5F6368'>Anúncio, rastreador ou redirecionamento barrado "
                + "(ou o endereço não existe).</p>"
                + "<button onclick='history.back()' style='margin-top:24px;padding:12px 28px;"
                + "border:0;border-radius:24px;background:#1457E8;color:#fff;font-size:16px'>Voltar</button>"
                + "</body></html>";
        return new WebResourceResponse("text/html", "utf-8",
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
    }

    /** Chamado em shouldInterceptRequest (thread de fundo). null = deixa passar. */
    static WebResourceResponse intercept(Context c, WebResourceRequest req) {
        if (!enabled(c)) return null;
        String scheme = req.getUrl().getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) return null;
        String host = req.getUrl().getHost();
        if (!isBlocked(c, host)) return null;
        blocked.incrementAndGet();
        if (req.isForMainFrame()) return page(host);
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    private static String base(String host) {
        if (host == null) return null;
        String[] p = host.toLowerCase(Locale.ROOT).split("\\.");
        if (p.length <= 2) return host.toLowerCase(Locale.ROOT);
        String last = p[p.length - 1], mid = p[p.length - 2];
        boolean second = last.length() == 2 && (mid.equals("co") || mid.equals("com") || mid.equals("org")
                || mid.equals("net") || mid.equals("gov") || mid.equals("edu"));
        return second ? p[p.length - 3] + "." + mid + "." + last : mid + "." + last;
    }

    private static void note(Activity a, String msg) {
        long now = System.currentTimeMillis();
        if (now - lastToast < 1500) return;
        lastToast = now;
        ArbeonUi.toast(a, msg);
    }

    /**
     * Chamado em shouldOverrideUrlLoading (thread principal). true = cancela a navegação.
     * Barra: anúncio/popup da lista abrindo sozinho, redirecionamento automático para outro site
     * e abertura automática de outros apps (intent://, market://...).
     */
    static boolean blockNavigation(Activity a, WebView view, WebResourceRequest req) {
        try {
            Uri u = req.getUrl();
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            boolean web = scheme.equals("http") || scheme.equals("https");
            if (!web) {
                boolean local = scheme.equals("about") || scheme.equals("file") || scheme.equals("data")
                        || scheme.equals("blob") || scheme.equals("javascript");
                if (!local && !req.hasGesture()) {
                    note(a, "Abertura automática de app bloqueada");
                    return true;
                }
                return false;
            }
            if (!req.isForMainFrame()) return false;
            boolean auto = !req.hasGesture() || req.isRedirect();
            if (auto && AdBlock.enabled(a) && AdBlock.isBlocked(u.getHost())) {
                AdBlock.blocked.incrementAndGet();
                note(a, "Anúncio/pop-up bloqueado");
                return true;
            }
            if (!req.hasGesture() && !req.isRedirect() && guardRedirects(a)) {
                String cur = view.getUrl();
                if (cur != null && cur.startsWith("http")) {
                    String x = base(Uri.parse(cur).getHost());
                    String y = base(u.getHost());
                    if (x != null && y != null && !x.equals(y)) {
                        note(a, "Redirecionamento automático bloqueado");
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
