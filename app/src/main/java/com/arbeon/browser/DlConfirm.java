package com.arbeon.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

/** Janela "Baixar o arquivo?" (azul com nebulosa), mostrada antes de iniciar qualquer download. */
final class DlConfirm {
    private static final Locale PT = new Locale("pt", "BR");

    private DlConfirm() {
    }

    private static int dp(Context c, int v) {
        return Fx.dp(c, v);
    }

    /** Matiz do tema (Configurações > Aparência). Padrão: azul. */
    private static float hue() {
        return Fx.hue < 0 ? 222f : (Fx.hue % 360f);
    }

    private static int tone(float s, float v) {
        return Color.HSVToColor(new float[]{hue(), s, v});
    }

    private static int tone(float s, float v, int alpha) {
        return (tone(s, v) & 0x00FFFFFF) | (alpha << 24);
    }

    /** Evita que um desenho "match_parent" estique o cabeçalho: só ocupa o espaço que o pai já definiu. */
    private static int[] fillSize(int wSpec, int hSpec) {
        int w = View.MeasureSpec.getSize(wSpec);
        int h = View.MeasureSpec.getMode(hSpec) == View.MeasureSpec.EXACTLY
                ? View.MeasureSpec.getSize(hSpec) : 0;
        return new int[]{w, h};
    }

    private static float dens(View v) {
        return v.getResources().getDisplayMetrics().density;
    }

    /** Tamanho em português: 1,72 MB. */
    static String sizePt(long b) {
        if (b >= 1024L * 1024 * 1024) return String.format(PT, "%.2f GB", b / 1073741824.0);
        if (b >= 1024L * 1024) return String.format(PT, "%.2f MB", b / 1048576.0);
        if (b >= 1024) return String.format(PT, "%.0f kB", b / 1024.0);
        return b + " B";
    }

    private static float[][] makeStars(int n, long seed, float xmin) {
        Random r = new Random(seed);
        float[][] s = new float[n][4];
        for (int i = 0; i < n; i++) {
            s[i][0] = xmin + r.nextFloat() * (1f - xmin);
            s[i][1] = r.nextFloat();
            s[i][2] = 0.4f + r.nextFloat() * 1.1f;
            s[i][3] = 0.25f + r.nextFloat() * 0.75f;
        }
        return s;
    }

    private static void drawStars(Canvas c, Paint p, float w, float h, float[][] st, float d) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        for (float[] s : st) {
            p.setColor(Color.WHITE);
            p.setAlpha((int) (s[3] * 255));
            c.drawCircle(s[0] * w, s[1] * h, s[2] * d, p);
        }
        p.setAlpha(255);
    }

    // ------------------------------------------------------------------ desenhos

    /** Canto superior direito: nebulosa escura com borda azul brilhante e estrela de brilho. */
    private static class Nebula extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path fill = new Path(), edge = new Path(), spark = new Path();
        private final float[][] stars = makeStars(80, 11, 0.55f);

        Nebula(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int[] sz = fillSize(wSpec, hSpec);
            setMeasuredDimension(sz[0], sz[1]);
        }

        @Override
        protected void onDraw(Canvas c) {
            float W = getWidth(), H = getHeight(), d = dens(this);
            edge.reset();
            edge.moveTo(0.62f * W, 0);
            edge.cubicTo(0.67f * W, 0.50f * H, 0.74f * W, 0.72f * H, 0.81f * W, 0.62f * H);
            edge.cubicTo(0.89f * W, 0.52f * H, 0.94f * W, 0.34f * H, W, 0.30f * H);
            fill.set(edge);
            fill.lineTo(W, 0);
            fill.close();

            c.save();
            c.clipPath(fill);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0.6f * W, 0, W, H,
                    new int[]{tone(.93f, .22f), tone(.93f, .61f), tone(.88f, 1f)}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p);
            p.setShader(new RadialGradient(0.90f * W, 0.20f * H, 0.32f * W, tone(.76f, 1f, 0x77), tone(.76f, 1f, 0x00),
                    Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p);
            drawStars(c, p, W, H, stars, d);
            c.restore();

            // borda brilhante
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(tone(.76f, 1f, 0x55));
            p.setStrokeWidth(7 * d);
            c.drawPath(edge, p);
            p.setColor(Fx.GLOW);
            p.setStrokeWidth(2.5f * d);
            c.drawPath(edge, p);

            // estrela de 4 pontas
            float cx = 0.84f * W, cy = 0.27f * H, r = 11 * d;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy, r * 1.8f, 0x99FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r * 1.8f, p);
            p.setShader(null);
            p.setColor(Color.WHITE);
            spark.reset();
            spark.moveTo(cx, cy - r);
            spark.quadTo(cx, cy, cx + r, cy);
            spark.quadTo(cx, cy, cx, cy + r);
            spark.quadTo(cx, cy, cx - r, cy);
            spark.quadTo(cx, cy, cx, cy - r);
            c.drawPath(spark, p);
        }
    }

    /** Círculo escuro com seta de download (lado esquerdo do título). */
    private static class Badge extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float[][] stars = makeStars(30, 5, 0f);

        Badge(Context c) {
            super(c);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = dens(this);
            float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - 3 * d;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy * 0.85f, r * 1.1f,
                    new int[]{tone(.85f, .85f), tone(.90f, .45f), tone(.94f, .20f)}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, p);
            p.setShader(null);
            c.save();
            path.reset();
            path.addCircle(cx, cy, r, Path.Direction.CW);
            c.clipPath(path);
            drawStars(c, p, w, h, stars, d);
            c.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Fx.GLOW);
            p.setStrokeWidth(3 * d);
            c.drawCircle(cx, cy, r, p);
            p.setColor(0xAAFFFFFF);
            p.setStrokeWidth(1.2f * d);
            c.drawCircle(cx, cy, r - 4 * d, p);

            // seta + base
            float u = r / 24f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawRoundRect(cx - 4 * u, cy - 15 * u, cx + 4 * u, cy + 2 * u, 2 * u, 2 * u, p);
            path.reset();
            path.moveTo(cx - 11 * u, cy - 3 * u);
            path.lineTo(cx + 11 * u, cy - 3 * u);
            path.lineTo(cx, cy + 9 * u);
            path.close();
            c.drawPath(path, p);
            c.drawRoundRect(cx - 14 * u, cy + 13 * u, cx + 14 * u, cy + 17 * u, 2 * u, 2 * u, p);
        }
    }

    /** Ícone de arquivo com zíper e etiqueta azul com a extensão. */
    private static class FileIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final String ext;

        FileIcon(Context c, String ext) {
            super(c);
            this.ext = ext;
        }

        @Override
        protected void onDraw(Canvas c) {
            float u = getWidth() / 100f;
            float ox = 0;
            int ink = 0xFF1B2A55;
            // folha
            path.reset();
            path.moveTo(10 * u, 4 * u);
            path.lineTo(62 * u, 4 * u);
            path.lineTo(90 * u, 32 * u);
            path.lineTo(90 * u, 104 * u);
            path.lineTo(10 * u, 104 * u);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 4 * u, 0, 104 * u, 0xFFFFFFFF, 0xFFD5DBEC, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(2.5f * u);
            p.setColor(ink);
            c.drawPath(path, p);
            // dobra
            path.reset();
            path.moveTo(62 * u, 4 * u);
            path.lineTo(62 * u, 32 * u);
            path.lineTo(90 * u, 32 * u);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFC3CBE0);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(ink);
            c.drawPath(path, p);
            // zíper
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF161A29);
            for (int i = 0; i < 9; i++) {
                float y = 8 * u + i * 5.6f * u;
                c.drawRect(37 * u, y, 46 * u, y + 3.2f * u, p);
                c.drawRect(46 * u, y + 2.8f * u, 55 * u, y + 6f * u, p);
            }
            c.drawRoundRect(39 * u, 59 * u, 53 * u, 80 * u, 5 * u, 5 * u, p);
            p.setColor(0xFFDDE3F3);
            c.drawCircle(46 * u, 68 * u, 3 * u, p);
            // etiqueta
            RectF lab = new RectF(4 * u, 84 * u, 96 * u, 114 * u);
            p.setShader(new LinearGradient(0, 84 * u, 0, 114 * u, tone(.76f, 1f), tone(.94f, .78f), Shader.TileMode.CLAMP));
            c.drawRoundRect(lab, 8 * u, 8 * u, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * u);
            p.setColor(tone(.93f, .48f));
            c.drawRoundRect(lab, 8 * u, 8 * u, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize((ext.length() > 3 ? 17 : 21) * u);
            c.drawText(ext, 50 * u, 106 * u, p);
        }
    }

    /** Estrelinhas no botão escuro (recortadas na pílula). */
    private static class StarBg extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF r = new RectF();
        private final float[][] stars = makeStars(60, 23, 0f);

        StarBg(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int[] sz = fillSize(wSpec, hSpec);
            setMeasuredDimension(sz[0], sz[1]);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = dens(this);
            r.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(r, h / 2f, h / 2f, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(0.25f * w, 0.9f * h, 0.45f * w, tone(.76f, 1f, 0x55), tone(.76f, 1f, 0x00),
                    Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            p.setShader(null);
            drawStars(c, p, w, h, stars, d);
            c.restore();
        }
    }

    private static class XView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final float frac;

        XView(Context c, int color, float frac) {
            super(c);
            this.color = color;
            this.frac = frac;
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = dens(this);
            float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) * frac;
            p.setColor(color);
            p.setStrokeWidth(3.2f * d);
            c.drawLine(cx - r, cy - r, cx + r, cy + r, p);
            c.drawLine(cx + r, cy - r, cx - r, cy + r, p);
        }
    }

    private static class XDot extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        XDot(Context c) {
            super(c);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = dens(this);
            float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Fx.NAVY);
            c.drawCircle(cx, cy, r, p);
            p.setColor(Fx.WHITE);
            p.setStrokeWidth(2.6f * d);
            float k = r * 0.38f;
            c.drawLine(cx - k, cy - k, cx + k, cy + k, p);
            c.drawLine(cx + k, cy - k, cx - k, cy + k, p);
        }
    }

    private static class DlArrow extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        DlArrow(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            float u = Math.min(getWidth(), getHeight()) / 28f;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawRoundRect(cx - 3 * u, cy - 11 * u, cx + 3 * u, cy + 1 * u, 1.5f * u, 1.5f * u, p);
            path.reset();
            path.moveTo(cx - 9 * u, cy - 3 * u);
            path.lineTo(cx + 9 * u, cy - 3 * u);
            path.lineTo(cx, cy + 7 * u);
            path.close();
            c.drawPath(path, p);
            c.drawRoundRect(cx - 11 * u, cy + 10 * u, cx + 11 * u, cy + 13 * u, 1.5f * u, 1.5f * u, p);
        }
    }

    /** Ícone de elo de corrente, do botão "Atualizar link". */
    private static class LinkIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        LinkIcon(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            float u = Math.min(getWidth(), getHeight()) / 30f;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            p.setColor(Fx.NAVY);
            p.setStrokeWidth(3f * u);
            c.save();
            c.rotate(-45, cx, cy);
            r.set(cx - 13 * u, cy - 5.5f * u, cx + 3 * u, cy + 5.5f * u);
            c.drawRoundRect(r, 5.5f * u, 5.5f * u, p);
            r.set(cx - 3 * u, cy - 5.5f * u, cx + 13 * u, cy + 5.5f * u);
            c.drawRoundRect(r, 5.5f * u, 5.5f * u, p);
            c.restore();
        }
    }

    // ------------------------------------------------------------------ janela

    private static TextView msgLine(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTextColor(Fx.NAVY);
        return t;
    }

    private static String extOf(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "FILE";
        String e = name.substring(i + 1).toUpperCase(Locale.ROOT);
        return e.length() > 4 ? e.substring(0, 4) : e;
    }

    static void show(Activity a, String name, long len, boolean again, Runnable onYes) {
        show(a, name, len, again, onYes, null);
    }

    /**
     * again + onUpdate != null: download já existe, a janela oferece "Atualizar link" e "Baixar novamente".
     */
    static void show(final Activity a, String name, long len, boolean again, final Runnable onYes,
                     final Runnable onUpdate) {
        final boolean choose = again && onUpdate != null;
        final Dialog d = new Dialog(a, android.R.style.Theme_Material_Dialog_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout wrap = new FrameLayout(a);
        wrap.setPadding(dp(a, 14), dp(a, 14), dp(a, 14), dp(a, 18));
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.WHITE, Fx.FIELD2});
        cbg.setCornerRadius(dp(a, 26));
        cbg.setStroke(dp(a, 2), Fx.GLOW);
        card.setBackground(cbg);
        card.setClipToOutline(true);
        Fx.glow(card, 14);
        wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- cabeçalho
        FrameLayout hdr = new FrameLayout(a);
        hdr.addView(new Nebula(a), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 14), dp(a, 10), dp(a, 8), dp(a, 10));
        Badge badge = new Badge(a);
        Fx.glow(badge, 6);
        row.addView(badge, new LinearLayout.LayoutParams(dp(a, 52), dp(a, 52)));
        TextView title = new TextView(a);
        title.setText(choose ? "Esse download já existe" : (again ? "Baixar o arquivo novamente?" : "Baixar o arquivo?"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Fx.NAVY);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(a, 12);
        tp.rightMargin = dp(a, 44);
        row.addView(title, tp);
        hdr.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        XView close = new XView(a, Fx.NAVY, 0.30f);
        close.setClickable(true);
        close.setOnClickListener(v -> d.dismiss());
        FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(dp(a, 38), dp(a, 38),
                Gravity.END | Gravity.BOTTOM);
        xp.rightMargin = dp(a, 8);
        xp.bottomMargin = dp(a, 4);
        hdr.addView(close, xp);
        card.addView(hdr, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- corpo
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.WHITE, Fx.FIELD2});
        bbg.setCornerRadius(dp(a, 20));
        bbg.setStroke(dp(a, 2), Fx.BORDER2);
        body.setBackground(bbg);
        body.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 10));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(dp(a, 8), 0, dp(a, 8), dp(a, 10));
        card.addView(body, bp);

        LinearLayout info = new LinearLayout(a);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.addView(new FileIcon(a, extOf(name)), new LinearLayout.LayoutParams(dp(a, 40), dp(a, 52)));
        LinearLayout msgCol = new LinearLayout(a);
        msgCol.setOrientation(LinearLayout.VERTICAL);
        if (!choose) msgCol.addView(msgLine(a, "Quer baixar"));
        TextView nm = new TextView(a);
        SpannableStringBuilder nb = new SpannableStringBuilder(name);
        nb.setSpan(new ForegroundColorSpan(Fx.ACCENT), 0, nb.length(), 0);
        nb.setSpan(new UnderlineSpan(), 0, nb.length(), 0);
        nm.setText(nb);
        nm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nm.setMaxLines(2);
        nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        msgCol.addView(nm);
        String size = len > 0 ? "(" + sizePt(len) + ")" : "";
        String tail = choose
                ? (size.isEmpty() ? "" : size + " ") + "já está nos downloads. Atualizar o link ou baixar novamente?"
                : size + (again ? " novamente?" : "?");
        msgCol.addView(msgLine(a, tail.trim()));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mp.leftMargin = dp(a, 12);
        info.addView(msgCol, mp);
        body.addView(info);

        View line = new View(a);
        line.setBackgroundColor(Fx.TOP1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 1));
        lp.setMargins(dp(a, 2), dp(a, 10), dp(a, 2), dp(a, 10));
        body.addView(line, lp);

        // ---- botões
        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setClipChildren(false);

        LinearLayout cancel = new LinearLayout(a);
        cancel.setOrientation(LinearLayout.HORIZONTAL);
        cancel.setGravity(Gravity.CENTER);
        GradientDrawable cg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.WHITE, Fx.FACE});
        cg.setCornerRadius(dp(a, 100));
        cg.setStroke(dp(a, 2), Fx.BORDER2);
        cancel.setBackground(cg);
        Fx.glow(cancel, 4);
        cancel.setClickable(true);
        cancel.setOnClickListener(v -> {
            d.dismiss();
            if (choose && onUpdate != null) onUpdate.run();
        });
        cancel.addView(choose ? new LinkIcon(a) : new XDot(a), new LinearLayout.LayoutParams(dp(a, 24), dp(a, 24)));
        TextView ct = new TextView(a);
        ct.setText(choose ? "Atualizar link" : "Cancelar");
        ct.setTextSize(TypedValue.COMPLEX_UNIT_SP, choose ? 14 : 16);
        ct.setTypeface(Typeface.DEFAULT_BOLD);
        ct.setTextColor(Fx.NAVY);
        ct.setPadding(dp(a, 6), 0, 0, 0);
        cancel.addView(ct);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(a, 46), 1f);
        cp.rightMargin = dp(a, 10);
        btns.addView(cancel, cp);

        FrameLayout go = new FrameLayout(a);
        GradientDrawable gg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{tone(.94f, .18f), tone(.93f, .52f), tone(.94f, .75f)});
        gg.setCornerRadius(dp(a, 100));
        gg.setStroke(dp(a, 3), Fx.GLOW);
        go.setBackground(gg);
        Fx.glow(go, 8);
        go.setClickable(true);
        go.addView(new StarBg(a), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout gc = new LinearLayout(a);
        gc.setOrientation(LinearLayout.HORIZONTAL);
        gc.setGravity(Gravity.CENTER);
        gc.setPadding(dp(a, 8), 0, dp(a, 8), 0);
        gc.addView(new DlArrow(a), new LinearLayout.LayoutParams(dp(a, 24), dp(a, 24)));
        TextView gt = new TextView(a);
        gt.setText(choose ? "Baixar novamente" : (again ? "Fazer o download novamente" : "Fazer o download"));
        gt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        gt.setMaxLines(2);
        gt.setTypeface(Typeface.DEFAULT_BOLD);
        gt.setTextColor(Color.WHITE);
        gt.setGravity(Gravity.CENTER);
        gt.setPadding(dp(a, 6), 0, 0, 0);
        gc.addView(gt);
        go.addView(gc, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        go.setOnClickListener(v -> {
            d.dismiss();
            if (onYes != null) onYes.run();
        });
        btns.addView(go, new LinearLayout.LayoutParams(0, dp(a, 46), choose ? 1f : 1.3f));
        body.addView(btns);

        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw = a.getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (sw * 0.95f), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams wl = w.getAttributes();
            wl.dimAmount = 0.5f;
            w.setAttributes(wl);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.setCanceledOnTouchOutside(true);
        d.show();
    }
}
