package com.arbeon.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
import android.view.Window;
import android.view.WindowManager;
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

/** Menu de downloads (painel claro estilo GBA Emu) e entrada dos downloads vindos do navegador. */
final class DownloadsUi {
    private static final int BLUE = 0xFF1A73E8;
    private static final int GRAY = 0xFF6B7280;
    private static final int RED = 0xFFD93025;
    private static final int INK = 0xFF15161A;
    private static final Typeface COND_B = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private static final Typeface COND = Typeface.create("sans-serif-condensed", Typeface.NORMAL);

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
        Toast.makeText(a, "Download adicionado: " + name + "\nToque na pasta ⬇ da barra", Toast.LENGTH_LONG).show();
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

    private static GradientDrawable pill(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, 22));
        g.setStroke(dp(c, 1), 0x33FFFFFF);
        return g;
    }

    private static StateListDrawable pillPress(Context c) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, pill(c, 0xFFE3E7EF));
        sl.addState(new int[]{}, pill(c, 0xD9FFFFFF));
        return sl;
    }

    private static TextView txt(Context c, String s, Typeface tf, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLetterSpacing(0.02f);
        t.setTextColor(color);
        return t;
    }

    private static TextView textBtn(Context c, String text, int color, View.OnClickListener l) {
        TextView t = txt(c, text, COND_B, 14, color);
        t.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        t.setOnClickListener(l);
        return t;
    }

    private static TextView pillButton(Context c, String text, View.OnClickListener l) {
        TextView t = txt(c, text, COND_B, 15, INK);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 8), dp(c, 13), dp(c, 8), dp(c, 13));
        t.setBackground(pillPress(c));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    /** ScrollView que não passa de uma altura máxima. */
    private static class MaxScroll extends ScrollView {
        private final int maxH;

        MaxScroll(Context c, int maxH) {
            super(c);
            this.maxH = maxH;
            setVerticalScrollBarEnabled(false);
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
        }
    }

    private static class Row {
        DlItem it;
        LinearLayout box;
        TextView name, status, bToggle, bLink, bDel;
        ProgressBar bar;
    }

    private static String clipLink(Context c, String exclude) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData cd = cm.getPrimaryClip();
                if (cd != null && cd.getItemCount() > 0 && cd.getItemAt(0).getText() != null) {
                    String s = cd.getItemAt(0).getText().toString().trim();
                    if ((s.startsWith("http://") || s.startsWith("https://")) && !s.equals(exclude)) return s;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ menu de downloads

    static void show(final Activity a) {
        final DownloadEngine eng = DownloadEngine.get(a);
        final Handler h = new Handler(Looper.getMainLooper());
        final ArrayList<Row> rows = new ArrayList<>();
        final String[] key = {null};

        final Dialog d = new Dialog(a, android.R.style.Theme_Material_Dialog_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a, 18), dp(a, 20), dp(a, 18), dp(a, 18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF7FFFFFF, 0xF2DCE1E9});
        bg.setCornerRadius(dp(a, 30));
        bg.setStroke(dp(a, 2), 0xFFFFFFFF);
        panel.setBackground(bg);

        TextView title = txt(a, "DOWNLOADS", COND_B, 24, INK);
        title.setLetterSpacing(0.03f);
        title.setPadding(dp(a, 4), 0, 0, dp(a, 12));
        panel.addView(title);

        // --- ações rápidas
        LinearLayout acts = new LinearLayout(a);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a1.rightMargin = dp(a, 5);
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a2.leftMargin = dp(a, 5);
        acts.addView(pillButton(a, "+ BAIXAR POR LINK", v -> newLinkDialog(a, eng)), a1);
        acts.addView(pillButton(a, "LIMPAR CONCLUÍDOS", v -> eng.clearDone()), a2);
        panel.addView(acts);

        // --- velocidade / fila
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 18), dp(a, 12), dp(a, 18), dp(a, 8));
        card.setBackground(pill(a, 0xD9FFFFFF));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(a, 10);
        panel.addView(card, cp);

        final TextView lblMax = txt(a, "", COND_B, 16, INK);
        SeekBar sbMax = seek(a, 10, eng.maxSimultaneous());
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
        card.addView(lblMax);
        card.addView(sbMax);

        final TextView lblConn = txt(a, "", COND_B, 16, INK);
        lblConn.setPadding(0, dp(a, 6), 0, 0);
        SeekBar sbConn = seek(a, 15, eng.connections() - 1);
        lblConn.setText("CONEXÕES POR DOWNLOAD: " + eng.connections() + "  (MAIS = MAIS VELOZ)");
        sbConn.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                lblConn.setText("CONEXÕES POR DOWNLOAD: " + (p + 1) + "  (MAIS = MAIS VELOZ)");
                if (user) eng.setConnections(p + 1);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        card.addView(lblConn);
        card.addView(sbConn);

        // --- lista
        int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.46f);
        MaxScroll sv = new MaxScroll(a, maxH);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(a, 12);
        panel.addView(sv, sp);

        final TextView empty = txt(a, "NENHUM DOWNLOAD AINDA", COND_B, 16, GRAY);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(a, 22), 0, dp(a, 14));

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

        d.setContentView(panel);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw = a.getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (sw * 0.94f), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.45f);
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    WindowManager.LayoutParams lp = w.getAttributes();
                    lp.setBlurBehindRadius(40);
                    w.setAttributes(lp);
                } catch (Exception ignored) {
                }
            }
        }
        d.setCanceledOnTouchOutside(true);
        d.setOnDismissListener(x -> h.removeCallbacks(refresh[0]));
        d.show();
        refresh[0].run();
    }

    private static SeekBar seek(Context c, int max, int progress) {
        SeekBar s = new SeekBar(c);
        s.setMax(max);
        s.setProgress(progress);
        s.setProgressTintList(ColorStateList.valueOf(BLUE));
        s.setThumbTintList(ColorStateList.valueOf(BLUE));
        return s;
    }

    private static void maxLabel(TextView t, int n) {
        t.setText(n == 0
                ? "SIMULTÂNEOS: 0  (MANUAL: TOQUE EM CONTINUAR)"
                : "DOWNLOADS SIMULTÂNEOS: " + n);
    }

    private static Row buildRow(final Activity a, final DownloadEngine eng, final DlItem it) {
        final Row r = new Row();
        r.it = it;
        r.box = new LinearLayout(a);
        r.box.setOrientation(LinearLayout.VERTICAL);
        r.box.setPadding(dp(a, 16), dp(a, 12), dp(a, 12), dp(a, 4));
        r.box.setBackground(pill(a, 0xD9FFFFFF));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.bottomMargin = dp(a, 9);
        r.box.setLayoutParams(bp);

        r.name = txt(a, "", COND_B, 17, INK);
        r.name.setSingleLine(true);
        r.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        r.box.addView(r.name);

        r.bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        r.bar.setMax(1000);
        r.bar.setProgressTintList(ColorStateList.valueOf(BLUE));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 6));
        pp.topMargin = dp(a, 8);
        pp.rightMargin = dp(a, 4);
        r.box.addView(r.bar, pp);

        r.status = txt(a, "", COND, 14, GRAY);
        r.status.setPadding(0, dp(a, 6), 0, 0);
        r.box.addView(r.status);

        LinearLayout actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        r.bToggle = textBtn(a, "", BLUE, v -> {
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
        r.bLink = textBtn(a, "ATUALIZAR LINK", BLUE, v -> editLink(a, eng, it));
        r.bDel = textBtn(a, "REMOVER", RED, v -> {
            if (it.state == DlItem.DONE) {
                eng.remove(it);
            } else {
                new AlertDialog.Builder(a)
                        .setTitle("Remover download?")
                        .setMessage("O progresso parcial será apagado.")
                        .setPositiveButton("Remover", (dd, w) -> eng.remove(it))
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
                        + "\nToque em TENTAR DE NOVO ou ATUALIZAR LINK.";
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
                t = it.stop ? "…" : "PAUSAR";
                break;
            case DlItem.DONE:
                t = "ABRIR";
                break;
            case DlItem.FAILED:
                t = "TENTAR DE NOVO";
                break;
            default:
                t = "CONTINUAR";
                break;
        }
        r.bToggle.setText(t);
        r.bLink.setVisibility(st == DlItem.DONE ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ baixar por link / atualizar link / abrir

    private static void newLinkDialog(final Activity a, final DownloadEngine eng) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setHint("https://…");
        String c = clipLink(a, null);
        if (c != null) e.setText(c);
        new AlertDialog.Builder(a)
                .setTitle("Baixar por link")
                .setMessage("Cole o link direto do arquivo.")
                .setView(e)
                .setPositiveButton("Baixar", (dd, w) -> {
                    String u = e.getText().toString().trim();
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        askPermissions(a);
                        eng.add(u, null, null, null, URLUtil.guessFileName(u, null, null));
                    } else {
                        Toast.makeText(a, "Link inválido", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static void editLink(final Activity a, final DownloadEngine eng, final DlItem it) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMaxLines(5);
        String c = clipLink(a, it.url);
        e.setText(c != null ? c : it.url);
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(a)
                .setTitle("Atualizar link")
                .setMessage("Cole o novo link do mesmo arquivo. O progresso é mantido e o download continua de onde parou.")
                .setView(e)
                .setPositiveButton("Atualizar e continuar", (dd, w) -> {
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
