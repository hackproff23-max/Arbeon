package com.arbeon.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/** Tela de downloads (menu ⋮ → Downloads) e entrada dos downloads vindos do navegador. */
final class DownloadsUi {
    private static final int BLUE = 0xFF1A73E8;
    private static final int GRAY = 0xFF5F6368;
    private static final int RED = 0xFFD93025;

    private DownloadsUi() {
    }

    // ------------------------------------------------------------------ entrada vinda do WebView

    static void onDownloadRequested(Activity a, String url, String ua, String cd, String mime, long len) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            Toast.makeText(a, "Este tipo de link não é suportado para download", Toast.LENGTH_SHORT).show();
            return;
        }
        askPermissions(a);
        String name = URLUtil.guessFileName(url, cd, mime);
        DownloadEngine.get(a).add(url, ua, null, mime, name);
        Toast.makeText(a, "Download adicionado: " + name + "\nMenu ⋮ → Downloads", Toast.LENGTH_LONG).show();
    }

    private static void askPermissions(Activity a) {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
            } else if (Build.VERSION.SDK_INT < 29
                    && a.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 78);
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    static String human(long b) {
        if (b < 0) b = 0;
        if (b < 1024) return b + " B";
        double v = b / 1024.0;
        if (v < 1024) return String.format(Locale.US, "%.0f KB", v);
        v /= 1024;
        if (v < 1024) return String.format(Locale.US, "%.1f MB", v);
        v /= 1024;
        return String.format(Locale.US, "%.2f GB", v);
    }

    private static String eta(long secs) {
        if (secs < 0 || secs > 99 * 3600) return "--";
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return h > 0 ? String.format(Locale.US, "%dh %02dmin", h, m)
                : String.format(Locale.US, "%d:%02d", m, s);
    }

    private static TextView btn(Context c, String text, int color, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        t.setOnClickListener(l);
        return t;
    }

    // ------------------------------------------------------------------ tela

    private static class Row {
        DlItem it;
        LinearLayout box;
        TextView name, status, bToggle, bLink, bDel;
        ProgressBar bar;
    }

    static void show(final Activity a) {
        final DownloadEngine eng = DownloadEngine.get(a);
        final Handler h = new Handler(Looper.getMainLooper());
        final ArrayList<Row> rows = new ArrayList<>();
        final String[] key = {null};

        ScrollView sv = new ScrollView(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));
        sv.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- simultâneos 0..10
        final TextView lblMax = new TextView(a);
        lblMax.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        lblMax.setTextColor(0xFF202124);
        SeekBar sbMax = new SeekBar(a);
        sbMax.setMax(10);
        sbMax.setProgress(eng.maxSimultaneous());
        maxLabel(lblMax, eng.maxSimultaneous());
        sbMax.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                maxLabel(lblMax, p);
                if (user) eng.setMaxSimultaneous(p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(lblMax);
        root.addView(sbMax);

        // --- conexões por download 1..16
        final TextView lblConn = new TextView(a);
        lblConn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        lblConn.setTextColor(0xFF202124);
        lblConn.setPadding(0, dp(a, 10), 0, 0);
        SeekBar sbConn = new SeekBar(a);
        sbConn.setMax(15);
        sbConn.setProgress(eng.connections() - 1);
        lblConn.setText("Conexões por download: " + eng.connections() + "  (mais = mais veloz)");
        sbConn.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                lblConn.setText("Conexões por download: " + (p + 1) + "  (mais = mais veloz)");
                if (user) eng.setConnections(p + 1);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(lblConn);
        root.addView(sbConn);

        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 14);
        root.addView(list, lp);

        final TextView empty = new TextView(a);
        empty.setText("Nenhum download ainda.\nToque em um link de arquivo numa página para baixar.");
        empty.setTextColor(GRAY);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(a, 24), 0, dp(a, 24));

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            StringBuilder k = new StringBuilder();
            for (DlItem it : eng.items) k.append(it.id).append(',');
            if (!k.toString().equals(key[0])) {
                key[0] = k.toString();
                list.removeAllViews();
                rows.clear();
                if (eng.items.isEmpty()) {
                    list.addView(empty);
                } else {
                    for (DlItem it : eng.items) {
                        Row r = buildRow(a, eng, it);
                        rows.add(r);
                        list.addView(r.box);
                    }
                }
            }
            for (Row r : rows) update(r);
            h.postDelayed(refresh[0], 500);
        };

        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("Downloads")
                .setView(sv)
                .setPositiveButton("Fechar", null)
                .setNeutralButton("Limpar concluídos", (dd, w) -> eng.clearDone())
                .create();
        d.setOnDismissListener(x -> h.removeCallbacks(refresh[0]));
        d.show();
        refresh[0].run();
    }

    private static void maxLabel(TextView t, int n) {
        t.setText(n == 0
                ? "Downloads simultâneos: 0  (manual: nada começa sozinho, toque em ▶)"
                : "Downloads simultâneos: " + n);
    }

    private static Row buildRow(final Activity a, final DownloadEngine eng, final DlItem it) {
        final Row r = new Row();
        r.it = it;
        r.box = new LinearLayout(a);
        r.box.setOrientation(LinearLayout.VERTICAL);
        r.box.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(a, 12));
        bg.setStroke(dp(a, 1), 0xFFDADCE0);
        r.box.setBackground(bg);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.bottomMargin = dp(a, 8);
        r.box.setLayoutParams(bp);

        r.name = new TextView(a);
        r.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        r.name.setTypeface(Typeface.DEFAULT_BOLD);
        r.name.setTextColor(0xFF202124);
        r.name.setSingleLine(true);
        r.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        r.box.addView(r.name);

        r.bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        r.bar.setMax(1000);
        r.bar.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 6));
        pp.topMargin = dp(a, 8);
        r.box.addView(r.bar, pp);

        r.status = new TextView(a);
        r.status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        r.status.setTextColor(GRAY);
        r.status.setPadding(0, dp(a, 6), 0, 0);
        r.box.addView(r.status);

        LinearLayout actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        r.bToggle = btn(a, "", BLUE, v -> {
            switch (it.state) {
                case DlItem.RUNNING:
                case DlItem.QUEUED:
                    eng.pause(it);
                    break;
                case DlItem.DONE:
                    openFile(a, it);
                    break;
                default:
                    eng.resume(it);
                    break;
            }
        });
        r.bLink = btn(a, "🔗 Atualizar link", BLUE, v -> editLink(a, eng, it));
        r.bDel = btn(a, "✕ Remover", RED, v -> {
            if (it.state == DlItem.DONE) {
                eng.remove(it);
            } else {
                new AlertDialog.Builder(a)
                        .setTitle("Remover download?")
                        .setMessage("O progresso parcial será apagado.")
                        .setPositiveButton("Remover", (d, w) -> eng.remove(it))
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });
        actions.addView(r.bToggle);
        actions.addView(r.bLink);
        actions.addView(r.bDel);
        r.box.addView(actions);
        return r;
    }

    private static void update(Row r) {
        DlItem it = r.it;
        long done = it.done(), total = it.total;
        int st = it.state;
        r.name.setText(it.fileName);

        boolean indet = st == DlItem.RUNNING && total <= 0 && !it.finalizing;
        r.bar.setIndeterminate(indet);
        if (!indet) {
            int p = st == DlItem.DONE ? 1000 : (total > 0 ? (int) (done * 1000 / total) : 0);
            r.bar.setProgress(p);
        }
        String pct = total > 0 ? (done * 100 / total) + "%" : human(done);
        String size = total > 0 ? human(done) + " / " + human(total) : human(done);
        String text;
        int color = GRAY;
        switch (st) {
            case DlItem.RUNNING:
                if (it.finalizing) {
                    text = "Salvando em Downloads…";
                } else if (it.stop) {
                    text = "Pausando…";
                } else {
                    long sp = (long) it.speed;
                    String e = (total > 0 && sp > 0) ? " · faltam " + eta((total - done) / sp) : "";
                    text = pct + " · " + human(sp) + "/s · " + size + e;
                }
                break;
            case DlItem.QUEUED:
                text = "Na fila · " + size;
                break;
            case DlItem.PAUSED:
                text = "Pausado · " + size
                        + (it.error != null && !it.error.isEmpty() && !it.error.equals("Pausado")
                        ? "\n" + it.error : "");
                break;
            case DlItem.FAILED:
                text = "Falhou · " + size + "\n" + it.error
                        + "\nToque em ▶ para continuar ou 🔗 para trocar o link.";
                color = RED;
                break;
            default:
                text = "Concluído · " + human(total > 0 ? total : done);
                break;
        }
        r.status.setTextColor(color);
        r.status.setText(text);

        String t;
        switch (st) {
            case DlItem.RUNNING:
            case DlItem.QUEUED:
                t = it.stop ? "…" : "⏸ Pausar";
                break;
            case DlItem.DONE:
                t = "📂 Abrir";
                break;
            case DlItem.FAILED:
                t = "▶ Tentar de novo";
                break;
            default:
                t = "▶ Continuar";
                break;
        }
        r.bToggle.setText(t);
        r.bLink.setVisibility(st == DlItem.DONE ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ atualizar link / abrir

    private static void editLink(final Activity a, final DownloadEngine eng, final DlItem it) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMaxLines(5);
        String initial = it.url;
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData cd = cm.getPrimaryClip();
                if (cd != null && cd.getItemCount() > 0 && cd.getItemAt(0).getText() != null) {
                    String c = cd.getItemAt(0).getText().toString().trim();
                    if ((c.startsWith("http://") || c.startsWith("https://")) && !c.equals(it.url)) {
                        initial = c; // já preenche com o link copiado
                    }
                }
            }
        } catch (Exception ignored) {
        }
        e.setText(initial);
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(a)
                .setTitle("Atualizar link")
                .setMessage("Cole o novo link do mesmo arquivo. O progresso é mantido e o download continua de onde parou.")
                .setView(e)
                .setPositiveButton("Atualizar e continuar", (d, w) -> {
                    String u = e.getText().toString().trim();
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        eng.updateLink(it, u);
                        Toast.makeText(a, "Link atualizado", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(a, "Link inválido", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static void openFile(Activity a, DlItem it) {
        try {
            if (it.savedUri != null && it.savedUri.startsWith("content:")) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                String mime = it.mime == null || it.mime.isEmpty() ? "*/*" : it.mime;
                int semi = mime.indexOf(';');
                if (semi > 0) mime = mime.substring(0, semi).trim();
                i.setDataAndType(Uri.parse(it.savedUri), mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                a.startActivity(i);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            a.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (Exception e) {
            Toast.makeText(a, "Arquivo salvo na pasta Downloads", Toast.LENGTH_SHORT).show();
        }
    }
}
