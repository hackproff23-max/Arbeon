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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/** Tela cheia de downloads (estilo "Transferências", visual azul/vidro) e entrada dos downloads vindos do navegador. */
final class DownloadsUi {
    private static final int BG = 0xFFF4F7FF;
    private static final int BG2 = 0xFFE9EFFF;
    private static final int INK = 0xFF1B1B1F;
    private static final int NAVY = 0xFF0B1B4D;
    private static final int SUB = 0xFF44474F;
    private static final int LINE = 0xFFDADCE5;
    private static final int CIRCLE = 0xFFECEEF6;
    private static final int BLUE = 0xFF1A73E8;
    private static final int ACCENT = 0xFF1457E8;
    private static final int PILL = 0xFFE3E9FA;
    private static final int RED = 0xFFD93025;
    private static final Locale PT = new Locale("pt", "BR");
    private static final String[] CHIPS = {"Todas", "Em andamento", "Imagens", "Vídeos", "Áudio", "Outro"};
    private static final int[] CHIP_ICON = {Ic.DB, Ic.ARROWDOWN, Ic.IMAGE, Ic.PLAY, Ic.NOTE, Ic.FOLDER};

    private DownloadsUi() {
    }

    // ------------------------------------------------------------------ entrada vinda do WebView

    static void onDownloadRequested(Activity a, String url, String ua, String cd, String mime, long len) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            ArbeonUi.toast(a, "Este tipo de link não é suportado para download");
            return;
        }
        askPermissions(a);
        String name = URLUtil.guessFileName(url, cd, mime);
        DownloadEngine.get(a).add(url, ua, null, mime, name);
        ArbeonUi.toast(a, "Download adicionado: " + name + "\nToque na pasta ⬇ da barra");
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

    /** Formato em inglês, usado nas notificações. */
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

    /** Formato brasileiro: 528 kB, 167 MB, 113,51 GB. */
    private static String humanPt(long b) {
        if (b < 0) b = 0;
        if (b < 1000) return b + " B";
        double v = b / 1024.0;
        if (v < 1000) return String.format(PT, "%.0f kB", v);
        v /= 1024;
        if (v < 1000) return String.format(PT, v < 10 ? "%.1f MB" : "%.0f MB", v);
        v /= 1024;
        return String.format(PT, "%.2f GB", v);
    }

    private static String eta(long secs) {
        if (secs < 0 || secs > 99 * 3600) return "--";
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return h > 0 ? String.format(Locale.US, "%dh %02dmin", h, m)
                : String.format(Locale.US, "%d:%02d", m, s);
    }

    private static TextView txt(Context c, String s, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private static String host(String url) {
        try {
            return Uri.parse(url).getHost();
        } catch (Exception e) {
            return null;
        }
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

    // 2 imagens, 3 vídeos, 4 áudio, 5 outro
    private static int kind(DlItem it) {
        String m = it.mime == null ? "" : it.mime.toLowerCase(Locale.ROOT);
        String n = it.fileName == null ? "" : it.fileName.toLowerCase(Locale.ROOT);
        if (m.startsWith("image/") || n.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|heic|svg)$")) return 2;
        if (m.startsWith("video/") || n.matches(".*\\.(mp4|mkv|webm|avi|mov|3gp|flv)$")) return 3;
        if (m.startsWith("audio/") || n.matches(".*\\.(mp3|m4a|aac|flac|wav|ogg|opus)$")) return 4;
        return 5;
    }

    private static String dayLabel(long ts) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(ts);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        long diff = Math.round((today.getTimeInMillis() - day.getTimeInMillis()) / 86400000.0);
        String date = new SimpleDateFormat("d 'de' MMM 'de' yyyy", PT).format(new Date(ts));
        if (diff == 0) return "Hoje - " + date;
        if (diff == 1) return "Ontem - " + date;
        return date;
    }

    // ------------------------------------------------------------------ ícones desenhados

    private static class Ic extends View {
        static final int GEAR = 0, SEARCH = 1, CLOSE = 2, DOTS = 3, FILE = 4, CHECK = 5,
                DB = 6, IMAGE = 7, PLAY = 8, NOTE = 9, ARROWDOWN = 10, FOLDER = 11;
        private final int type;
        private int color;
        private final float iconDp;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Ic(Context c, int type, int color, int iconDp) {
            super(c);
            this.type = type;
            this.color = color;
            this.iconDp = iconDp;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            f.setStyle(Paint.Style.FILL);
        }

        void setColor(int c) {
            if (c != color) {
                color = c;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float size = iconDp * getResources().getDisplayMetrics().density;
            float u = size / 24f;
            c.save();
            c.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            p.setStrokeWidth(2f * u);
            p.setColor(color);
            f.setColor(color);
            switch (type) {
                case CLOSE:
                    c.drawLine(6 * u, 6 * u, 18 * u, 18 * u, p);
                    c.drawLine(18 * u, 6 * u, 6 * u, 18 * u, p);
                    break;
                case SEARCH:
                    c.drawCircle(10 * u, 10 * u, 6 * u, p);
                    c.drawLine(14.5f * u, 14.5f * u, 20 * u, 20 * u, p);
                    break;
                case DOTS:
                    c.drawCircle(12 * u, 5 * u, 2 * u, f);
                    c.drawCircle(12 * u, 12 * u, 2 * u, f);
                    c.drawCircle(12 * u, 19 * u, 2 * u, f);
                    break;
                case CHECK:
                    path.reset();
                    path.moveTo(5 * u, 12.5f * u);
                    path.lineTo(10 * u, 17.5f * u);
                    path.lineTo(19 * u, 7 * u);
                    c.drawPath(path, p);
                    break;
                case GEAR:
                    c.translate(12 * u, 12 * u);
                    for (int i = 0; i < 8; i++) {
                        c.save();
                        c.rotate(i * 45f);
                        c.drawRoundRect(-1.9f * u, -10.4f * u, 1.9f * u, -6f * u, 0.8f * u, 0.8f * u, f);
                        c.restore();
                    }
                    path.reset();
                    path.setFillType(Path.FillType.EVEN_ODD);
                    path.addCircle(0, 0, 8f * u, Path.Direction.CW);
                    path.addCircle(0, 0, 3.4f * u, Path.Direction.CW);
                    c.drawPath(path, f);
                    break;
                case DB:
                    for (int i = 0; i < 3; i++) {
                        c.drawRoundRect(4 * u, (3 + i * 6.5f) * u, 20 * u, (8.5f + i * 6.5f) * u,
                                3 * u, 3 * u, f);
                    }
                    break;
                case IMAGE:
                    c.drawRoundRect(3 * u, 4 * u, 21 * u, 20 * u, 3 * u, 3 * u, p);
                    path.reset();
                    path.moveTo(5.5f * u, 17.5f * u);
                    path.lineTo(10 * u, 11.5f * u);
                    path.lineTo(13.5f * u, 15.5f * u);
                    path.lineTo(15.5f * u, 13 * u);
                    path.lineTo(18.5f * u, 17.5f * u);
                    path.close();
                    c.drawPath(path, f);
                    c.drawCircle(16.5f * u, 8.5f * u, 1.8f * u, f);
                    break;
                case PLAY:
                    c.drawCircle(12 * u, 12 * u, 9.5f * u, p);
                    path.reset();
                    path.moveTo(10 * u, 8 * u);
                    path.lineTo(16 * u, 12 * u);
                    path.lineTo(10 * u, 16 * u);
                    path.close();
                    c.drawPath(path, f);
                    break;
                case NOTE:
                    c.drawCircle(9 * u, 17 * u, 3 * u, f);
                    c.drawLine(12 * u, 17 * u, 12 * u, 5 * u, p);
                    c.drawLine(12 * u, 5 * u, 18 * u, 7.5f * u, p);
                    break;
                case ARROWDOWN:
                    c.drawLine(12 * u, 4 * u, 12 * u, 16 * u, p);
                    path.reset();
                    path.moveTo(6.5f * u, 11.5f * u);
                    path.lineTo(12 * u, 17 * u);
                    path.lineTo(17.5f * u, 11.5f * u);
                    c.drawPath(path, p);
                    c.drawLine(5 * u, 20.5f * u, 19 * u, 20.5f * u, p);
                    break;
                case FOLDER:
                    path.reset();
                    path.moveTo(3 * u, 6 * u);
                    path.lineTo(10 * u, 6 * u);
                    path.lineTo(12 * u, 8.5f * u);
                    path.lineTo(21 * u, 8.5f * u);
                    path.lineTo(21 * u, 19 * u);
                    path.lineTo(3 * u, 19 * u);
                    path.close();
                    c.drawPath(path, f);
                    break;
                default: // FILE
                    path.reset();
                    path.moveTo(6 * u, 2 * u);
                    path.lineTo(14 * u, 2 * u);
                    path.lineTo(20 * u, 8 * u);
                    path.lineTo(20 * u, 22 * u);
                    path.lineTo(6 * u, 22 * u);
                    path.close();
                    c.drawPath(path, f);
                    f.setColor(Fx.WHITE);
                    f.setAlpha(110);
                    path.reset();
                    path.moveTo(14 * u, 2 * u);
                    path.lineTo(14 * u, 8 * u);
                    path.lineTo(20 * u, 8 * u);
                    path.close();
                    c.drawPath(path, f);
                    break;
            }
            c.restore();
        }
    }

    /** Barra fina de uso do armazenamento. */
    private static class Meter extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float frac;

        Meter(Context c) {
            super(c);
        }

        void setFrac(float f) {
            f = Math.max(0f, Math.min(1f, f));
            if (f != frac) {
                frac = f;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x331457E8);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, h / 2f, h / 2f, p);
            if (frac > 0f) {
                p.setColor(ACCENT);
                r.set(0, 0, Math.max(h, w * frac), h);
                c.drawRoundRect(r, h / 2f, h / 2f, p);
            }
        }
    }

    /** Ilustração do estado vazio: seta de download entrando na bandeja. */
    private static class EmptyArt extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();

        EmptyArt(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            float u = getWidth() / 220f;
            float cx = getWidth() / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, 120 * u, 95 * u, 0x441A5CFF, 0x001A5CFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, 120 * u, 95 * u, p);
            p.setShader(null);
            // bandeja: borda azul, corpo branco e vão
            p.setColor(0xFF1F5FF0);
            r.set(cx - 72 * u, 112 * u, cx + 72 * u, 158 * u);
            c.drawRoundRect(r, 20 * u, 20 * u, p);
            p.setColor(Fx.WHITE);
            r.set(cx - 72 * u, 106 * u, cx + 72 * u, 152 * u);
            c.drawRoundRect(r, 20 * u, 20 * u, p);
            p.setColor(0xFF2A6BFF);
            r.set(cx - 54 * u, 110 * u, cx + 54 * u, 124 * u);
            c.drawRoundRect(r, 7 * u, 7 * u, p);
            // seta
            p.setShader(new LinearGradient(0, 28 * u, 0, 124 * u, 0xFF66A9FF, 0xFF0A3CD0, Shader.TileMode.CLAMP));
            r.set(cx - 15 * u, 28 * u, cx + 15 * u, 82 * u);
            c.drawRoundRect(r, 8 * u, 8 * u, p);
            path.reset();
            path.moveTo(cx - 40 * u, 80 * u);
            path.lineTo(cx + 40 * u, 80 * u);
            path.lineTo(cx, 124 * u);
            path.close();
            c.drawPath(path, p);
            p.setShader(null);
            // faíscas
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(5 * u);
            p.setColor(0xFF4D9BFF);
            c.drawLine(cx - 84 * u, 58 * u, cx - 72 * u, 70 * u, p);
            c.drawLine(cx - 90 * u, 84 * u, cx - 74 * u, 84 * u, p);
            c.drawLine(cx + 84 * u, 58 * u, cx + 72 * u, 70 * u, p);
            c.drawLine(cx + 90 * u, 84 * u, cx + 74 * u, 84 * u, p);
        }
    }

    private static Ic iconButton(Context c, int type, int color, View.OnClickListener l) {
        Ic i = new Ic(c, type, color, 24);
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        if (tv.resourceId != 0) i.setBackgroundResource(tv.resourceId);
        i.setClickable(true);
        i.setOnClickListener(l);
        return i;
    }

    /** Botão redondo "vidro" do cabeçalho (engrenagem, lupa, fechar). */
    private static Ic headerButton(Context c, int type, View.OnClickListener l) {
        Ic i = new Ic(c, type, ACCENT, 22);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0xFFF8FAFF);
        oval.setStroke(dp(c, 2), 0x551457E8);
        i.setBackground(Fx.button(c, true));
        Fx.glow(i, 4);
        i.setClickable(true);
        i.setOnClickListener(l);
        return i;
    }

    private static View emptyState(Context c, String msg) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(0, dp(c, 56), 0, 0);
        l.addView(new EmptyArt(c), new LinearLayout.LayoutParams(dp(c, 220), dp(c, 170)));
        TextView t = txt(c, msg, 20, NAVY);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 28), dp(c, 14), dp(c, 28), dp(c, 14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 28));
        g.setColor(Fx.FIELD2);
        g.setStroke(dp(c, 1), 0xFFDDE4F8);
        t.setBackground(Fx.button(c, false)); Fx.glow(t, 3);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(c, 8);
        l.addView(t, tp);
        return l;
    }

    // ------------------------------------------------------------------ tela cheia

    private static class Row {
        DlItem it;
        LinearLayout box;
        Ic icon;
        TextView name, sub;
        ProgressBar bar;
    }

    static void show(final Activity a) {
        final DownloadEngine eng = DownloadEngine.get(a);
        final Handler h = new Handler(Looper.getMainLooper());
        final ArrayList<Row> rows = new ArrayList<>();
        final String[] key = {null};
        final int[] filter = {0};
        final String[] query = {""};

        final Dialog d = new Dialog(a, android.R.style.Theme_Material_Light_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG, BG2}));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        // --- cabeçalho: título (ou busca) + engrenagem, lupa, fechar
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(a, 20), dp(a, 12), dp(a, 8), dp(a, 8));
        head.setClipChildren(false);
        head.setClipToPadding(false);

        final TextView title = txt(a, "Transferências", 28, NAVY);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final EditText search = new EditText(a);
        search.setHint("Pesquisar downloads");
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        search.setTextColor(NAVY);
        search.setBackground(null);
        search.setVisibility(View.GONE);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        head.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Ic icSearch = headerButton(a, Ic.SEARCH, null);
        icSearch.setOnClickListener(v -> {
            boolean on = search.getVisibility() != View.VISIBLE;
            search.setVisibility(on ? View.VISIBLE : View.GONE);
            title.setVisibility(on ? View.GONE : View.VISIBLE);
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (on) {
                search.requestFocus();
                if (imm != null) imm.showSoftInput(search, 0);
            } else {
                search.setText("");
                if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                query[0] = s.toString().trim().toLowerCase(Locale.ROOT);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        Ic icGear = headerButton(a, Ic.GEAR, v -> settingsDialog(a, eng));
        Ic icClose = headerButton(a, Ic.CLOSE, v -> d.dismiss());
        Ic[] btns = {icGear, icSearch, icClose};
        for (Ic b : btns) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44));
            bp.leftMargin = dp(a, 4);
            bp.rightMargin = dp(a, 6);
            head.addView(b, bp);
        }
        root.addView(head);

        // --- armazenamento do aparelho: em uso, total e livre
        LinearLayout pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(a, 14), dp(a, 10), dp(a, 16), dp(a, 10));
        GradientDrawable pb = new GradientDrawable();
        pb.setCornerRadius(dp(a, 22));
        pb.setColor(PILL);
        pill.setBackground(pb);
        Ic dbIc = new Ic(a, Ic.DB, ACCENT, 22);
        pill.addView(dbIc, new LinearLayout.LayoutParams(dp(a, 36), dp(a, 36)));

        LinearLayout pcol = new LinearLayout(a);
        pcol.setOrientation(LinearLayout.VERTICAL);
        final TextView usage = txt(a, "", 14, NAVY);
        usage.setSingleLine(true);
        pcol.addView(usage);
        final Meter meter = new Meter(a);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 6));
        mp.topMargin = dp(a, 6);
        pcol.addView(meter, mp);
        final TextView freeTv = txt(a, "", 13, SUB);
        freeTv.setSingleLine(true);
        freeTv.setPadding(0, dp(a, 4), 0, 0);
        pcol.addView(freeTv);
        LinearLayout.LayoutParams pcl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pcl.leftMargin = dp(a, 10);
        pill.addView(pcol, pcl);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.setMargins(dp(a, 16), dp(a, 6), dp(a, 16), dp(a, 4));
        root.addView(pill, pl);

        // --- filtros
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        final LinearLayout chipRow = new LinearLayout(a);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 8));
        hs.addView(chipRow);
        root.addView(hs);

        // --- lista
        ScrollView sv = new ScrollView(a); sv.setClipChildren(false);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        sv.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final Runnable[] buildChips = new Runnable[1];
        buildChips[0] = () -> {
            chipRow.removeAllViews();
            for (int i = 0; i < CHIPS.length; i++) {
                final int idx = i;
                boolean sel = filter[0] == i;
                LinearLayout chip = new LinearLayout(a);
                chip.setOrientation(LinearLayout.HORIZONTAL);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setPadding(dp(a, 12), 0, dp(a, 16), 0);
                GradientDrawable cb = new GradientDrawable();
                cb.setCornerRadius(dp(a, 20));
                if (sel) {
                    cb.setColor(0xFFEAF1FF);
                    cb.setStroke(dp(a, 2), ACCENT);
                } else {
                    cb.setColor(0xFFF7F9FF);
                    cb.setStroke(dp(a, 1), Fx.TOP1);
                }
                chip.setBackground(cb);
                Ic ci = new Ic(a, sel ? Ic.CHECK : CHIP_ICON[i], sel ? ACCENT : NAVY, 20);
                chip.addView(ci, new LinearLayout.LayoutParams(dp(a, 26), dp(a, 26)));
                TextView t = txt(a, CHIPS[i], 15, sel ? ACCENT : NAVY);
                t.setPadding(dp(a, 6), 0, 0, 0);
                if (sel) t.setTypeface(Typeface.DEFAULT_BOLD);
                chip.addView(t);
                chip.setOnClickListener(v -> {
                    filter[0] = idx;
                    buildChips[0].run();
                });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(a, 40));
                cp.rightMargin = dp(a, 10);
                chipRow.addView(chip, cp);
            }
        };
        buildChips[0].run();

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            // estrutura da lista (muda com filtro, busca, itens e conclusão)
            StringBuilder k = new StringBuilder();
            k.append(filter[0]).append('|').append(query[0]).append('|');
            for (DlItem it : eng.items) k.append(it.id).append(it.state == DlItem.DONE ? 'd' : 'a').append(',');
            if (!k.toString().equals(key[0])) {
                key[0] = k.toString();
                list.removeAllViews();
                rows.clear();
                ArrayList<DlItem> vis = new ArrayList<>();
                for (DlItem it : eng.items) {
                    boolean ok = filter[0] == 0
                            || (filter[0] == 1 && it.state != DlItem.DONE)
                            || (filter[0] >= 2 && kind(it) == filter[0]);
                    if (ok && !query[0].isEmpty()
                            && !(it.fileName != null && it.fileName.toLowerCase(Locale.ROOT).contains(query[0]))) {
                        ok = false;
                    }
                    if (ok) vis.add(it);
                }
                Collections.sort(vis, (x, y) -> Long.compare(y.added, x.added));
                if (vis.isEmpty()) {
                    list.addView(emptyState(a, eng.items.isEmpty() ? "Nenhum download ainda" : "Nada encontrado"));
                }
                String last = null;
                for (DlItem it : vis) {
                    String g = dayLabel(it.added);
                    if (!g.equals(last)) {
                        last = g;
                        TextView gh = txt(a, g, 20, NAVY);
                        gh.setPadding(dp(a, 20), dp(a, 18), dp(a, 20), dp(a, 8));
                        list.addView(gh);
                    }
                    Row r = buildRow(a, eng, it);
                    rows.add(r);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.setMargins(dp(a, 14), dp(a, 5), dp(a, 14), dp(a, 7));
                    list.addView(r.box, rlp);
                }
                list.addView(new View(a), new LinearLayout.LayoutParams(1, dp(a, 24)));
            }
            // armazenamento do aparelho (atualiza a cada ciclo)
            long tot = 0, free = 0;
            try {
                StatFs s = new StatFs(Environment.getDataDirectory().getPath());
                tot = s.getTotalBytes();
                free = s.getAvailableBytes();
            } catch (Exception ignored) {
            }
            long usedPhone = Math.max(0, tot - free);
            usage.setText("Usando " + humanPt(usedPhone) + " de " + humanPt(tot));
            freeTv.setText("Livre " + humanPt(free));
            meter.setFrac(tot > 0 ? (float) usedPhone / (float) tot : 0f);
            for (Row r : rows) update(r);
            h.postDelayed(refresh[0], 500);
        };

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(BG));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(BG);
            w.setNavigationBarColor(BG2);
            int vis = 0;
            if (Build.VERSION.SDK_INT >= 23) vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) vis |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(vis);
        }
        d.setOnDismissListener(x -> h.removeCallbacks(refresh[0]));
        d.show();
        refresh[0].run();
    }

    // ------------------------------------------------------------------ linhas

    private static Row buildRow(final Activity a, final DownloadEngine eng, final DlItem it) {
        final Row r = new Row();
        r.it = it;
        r.box = new LinearLayout(a);
        r.box.setOrientation(LinearLayout.HORIZONTAL);
        r.box.setGravity(Gravity.CENTER_VERTICAL);
        r.box.setPadding(dp(a, 12), dp(a, 10), dp(a, 6), dp(a, 10));
        TypedValue tv = new TypedValue();
        a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        r.box.setBackground(Fx.button(a, false)); Fx.glow(r.box, 3);
        r.box.setClickable(true);

        r.icon = new Ic(a, Ic.FILE, 0xFF2B2D33, 20);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(CIRCLE);
        r.icon.setBackground(Fx.button(a, true)); Fx.glow(r.icon, 3);
        r.box.addView(r.icon, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        r.name = txt(a, "", 18, INK);
        r.name.setSingleLine(true);
        r.name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(r.name);
        r.sub = txt(a, "", 15, SUB);
        r.sub.setPadding(0, dp(a, 2), 0, 0);
        col.addView(r.sub);
        r.bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        r.bar.setMax(1000);
        r.bar.setProgressTintList(ColorStateList.valueOf(BLUE));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 4));
        pp.topMargin = dp(a, 8);
        col.addView(r.bar, pp);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.leftMargin = dp(a, 16);
        r.box.addView(col, cl);

        final Ic dots = iconButton(a, Ic.DOTS, 0xFF2B2D33, null);
        dots.setOnClickListener(v -> itemMenu(a, eng, it, dots));
        r.box.addView(dots, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));

        r.box.setOnClickListener(v -> {
            switch (it.state) {
                case DlItem.DONE:
                    openFile(a, it);
                    break;
                case DlItem.RUNNING:
                case DlItem.QUEUED:
                    eng.pause(it);
                    break;
                default:
                    eng.resume(it);
                    break;
            }
        });
        return r;
    }

    private static void itemMenu(final Activity a, final DownloadEngine eng, final DlItem it, View anchor) {
        ArbeonPopup m = new ArbeonPopup(a, anchor);
        int st = it.state;
        if (st == DlItem.DONE) m.getMenu().add(0, 1, 0, "Abrir");
        else if (st == DlItem.RUNNING || st == DlItem.QUEUED) m.getMenu().add(0, 2, 0, "Pausar");
        else if (st == DlItem.FAILED) m.getMenu().add(0, 3, 0, "Tentar de novo");
        else m.getMenu().add(0, 3, 0, "Continuar");
        if (st != DlItem.DONE) m.getMenu().add(0, 4, 1, "Atualizar link");
        m.getMenu().add(0, 5, 2, "Copiar link");
        m.getMenu().add(0, 6, 3, "Remover");
        m.setOnMenuItemClickListener(mi -> {
            switch (mi.getItemId()) {
                case 1:
                    openFile(a, it);
                    break;
                case 2:
                    eng.pause(it);
                    break;
                case 3:
                    eng.resume(it);
                    break;
                case 4:
                    editLink(a, eng, it);
                    break;
                case 5:
                    try {
                        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("link", it.url));
                        ArbeonUi.toast(a, "Link copiado");
                    } catch (Exception ignored) {
                    }
                    break;
                case 6:
                    if (it.state == DlItem.DONE) {
                        eng.remove(it);
                    } else {
                        new ArbeonDialog(a)
                                .setTitle("Remover download?")
                                .setMessage("O progresso parcial será apagado.")
                                .setPositiveButton("Remover", (dd, w) -> eng.remove(it))
                                .setNegativeButton("Cancelar", null)
                                .show();
                    }
                    break;
                default:
                    break;
            }
            return true;
        });
        m.show();
    }

    private static void update(Row r) {
        DlItem it = r.it;
        long done = it.done(), total = it.total;
        int st = it.state;
        r.name.setText(it.fileName);
        String hst = host(it.url);
        String where = hst == null ? "" : " • " + hst;
        String size = total > 0 ? humanPt(done) + " / " + humanPt(total) : humanPt(done);
        String text;
        int color = SUB;
        switch (st) {
            case DlItem.RUNNING:
                if (it.finalizing) {
                    text = "Salvando em Downloads…";
                } else if (it.stop) {
                    text = "Pausando…";
                } else {
                    long sp = (long) it.speed;
                    String pct = total > 0 ? (done * 100 / total) + "%" : humanPt(done);
                    String e = (total > 0 && sp > 0) ? " • faltam " + eta((total - done) / sp) : "";
                    text = pct + " • " + humanPt(sp) + "/s" + e + "\n" + size;
                }
                break;
            case DlItem.QUEUED:
                text = "Na fila • " + size;
                break;
            case DlItem.PAUSED:
                text = "Pausado • " + size
                        + (it.error != null && !it.error.isEmpty() && !it.error.equals("Pausado")
                        ? "\n" + it.error : "");
                break;
            case DlItem.FAILED:
                text = "Falhou • " + size + "\n" + it.error + "\nToque para tentar de novo.";
                color = RED;
                break;
            default:
                text = humanPt(total > 0 ? total : done) + where;
                break;
        }
        r.sub.setTextColor(color);
        r.sub.setText(text);
        r.icon.setColor(st == DlItem.RUNNING ? BLUE : (st == DlItem.FAILED ? RED : 0xFF2B2D33));

        if (st == DlItem.DONE) {
            r.bar.setVisibility(View.GONE);
        } else {
            r.bar.setVisibility(View.VISIBLE);
            boolean indet = st == DlItem.RUNNING && total <= 0 && !it.finalizing;
            r.bar.setIndeterminate(indet);
            if (!indet) r.bar.setProgress(total > 0 ? (int) (done * 1000 / total) : 0);
        }
    }

    // ------------------------------------------------------------------ configurações (engrenagem)

    private static void settingsDialog(final Activity a, final DownloadEngine eng) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 24), dp(a, 8), dp(a, 24), 0);

        final TextView lblMax = txt(a, "", 16, INK);
        SeekBar sbMax = new SeekBar(a);
        sbMax.setMax(10);
        sbMax.setProgress(eng.maxSimultaneous());
        sbMax.setProgressTintList(ColorStateList.valueOf(BLUE));
        sbMax.setThumbTintList(ColorStateList.valueOf(BLUE));
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
        box.addView(lblMax);
        box.addView(sbMax);

        final TextView lblConn = txt(a, "Conexões por download: " + eng.connections() + " (mais = mais veloz)", 16, INK);
        lblConn.setPadding(0, dp(a, 14), 0, 0);
        SeekBar sbConn = new SeekBar(a);
        sbConn.setMax(15);
        sbConn.setProgress(eng.connections() - 1);
        sbConn.setProgressTintList(ColorStateList.valueOf(BLUE));
        sbConn.setThumbTintList(ColorStateList.valueOf(BLUE));
        sbConn.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                lblConn.setText("Conexões por download: " + (p + 1) + " (mais = mais veloz)");
                if (user) eng.setConnections(p + 1);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        box.addView(lblConn);
        box.addView(sbConn);

        new ArbeonDialog(a)
                .setTitle("Configurações de download")
                .setView(box)
                .setPositiveButton("Fechar", null)
                .setNeutralButton("Baixar por link", (dd, w) -> newLinkDialog(a, eng))
                .setNegativeButton("Limpar concluídos", (dd, w) -> eng.clearDone())
                .show();
    }

    private static void maxLabel(TextView t, int n) {
        t.setText(n == 0
                ? "Downloads simultâneos: 0 (manual: nada começa sozinho)"
                : "Downloads simultâneos: " + n);
    }

    // ------------------------------------------------------------------ baixar por link / atualizar link / abrir

    private static void newLinkDialog(final Activity a, final DownloadEngine eng) {
        final EditText e = new EditText(a); ArbeonUi.styleField(e);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setHint("https://…");
        String c = clipLink(a, null);
        if (c != null) e.setText(c);
        new ArbeonDialog(a)
                .setTitle("Baixar por link")
                .setMessage("Cole o link direto do arquivo.")
                .setView(e)
                .setPositiveButton("Baixar", (dd, w) -> {
                    String u = e.getText().toString().trim();
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        askPermissions(a);
                        eng.add(u, null, null, null, URLUtil.guessFileName(u, null, null));
                    } else {
                        ArbeonUi.toast(a, "Link inválido");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static void editLink(final Activity a, final DownloadEngine eng, final DlItem it) {
        final EditText e = new EditText(a); ArbeonUi.styleField(e);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMaxLines(5);
        String c = clipLink(a, it.url);
        e.setText(c != null ? c : it.url);
        e.setSelectAllOnFocus(true);
        new ArbeonDialog(a)
                .setTitle("Atualizar link")
                .setMessage("Cole o novo link do mesmo arquivo. O progresso é mantido e o download continua de onde parou.")
                .setView(e)
                .setPositiveButton("Atualizar e continuar", (dd, w) -> {
                    String u = e.getText().toString().trim();
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        eng.updateLink(it, u);
                        ArbeonUi.toast(a, "Link atualizado");
                    } else {
                        ArbeonUi.toast(a, "Link inválido");
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
            ArbeonUi.toast(a, "Arquivo salvo na pasta Downloads");
        }
    }
}
