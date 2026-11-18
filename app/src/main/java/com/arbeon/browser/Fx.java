package com.arbeon.browser;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

/**
 * Estilo visual do Arbeon: botões claros com brilho.
 * As cores seguem o tema escolhido em Configurações > Aparência (cor + claro/escuro/sistema).
 */
final class Fx {
    // ---- cores padrão do tema claro azul (original)
    private static final int D_NAVY = 0xFF0B1230, D_ACCENT = 0xFF1F5FE0, D_GLOW = 0xFF2D6BFF,
            D_SURFACE = 0xFFF6F7FD, D_TOP1 = 0xFFD6DBF0, D_TOP2 = 0xFFE6E9F6,
            D_BORDER = 0xFF9DB7F5, D_BORDER2 = 0xFFBFD0FA,
            D_PRESS1 = 0xFFDDE5FF, D_PRESS2 = 0xFFC9D6FA, D_FACE = 0xFFE8EBF7,
            D_CARD2 = 0xFFE9EEFF, D_FIELD2 = 0xFFF1F3FC,
            D_TEXT2 = 0xFF2B3350, D_MUTED = 0xFF5B6280, D_HINT = 0xFF7A8099;

    static int NAVY = D_NAVY;       // texto/ícones fortes
    static int ACCENT = D_ACCENT;   // cor principal
    static int GLOW = D_GLOW;       // brilho
    static int SURFACE = D_SURFACE; // fundo
    static int TOP1 = D_TOP1;       // barra de cima / divisórias
    static int TOP2 = D_TOP2;
    static int BORDER = D_BORDER;
    static int BORDER2 = D_BORDER2;
    static int PRESS1 = D_PRESS1;
    static int PRESS2 = D_PRESS2;
    static int FACE = D_FACE;       // fundo dos botões (parte de baixo)
    static int WHITE = 0xFFFFFFFF;  // "branco" do tema (parte de cima dos botões, cartões)
    static int CARD2 = D_CARD2;
    static int FIELD2 = D_FIELD2;
    static int TEXT2 = D_TEXT2;
    static int MUTED = D_MUTED;
    static int HINT = D_HINT;

    static boolean dark = false;
    static int hue = -1;

    private Fx() {
    }

    /** Lê o tema salvo (theme_hue: -1 = azul; theme_mode: 0 claro, 1 escuro, 2 sistema). */
    static void init(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("arbeon", Context.MODE_PRIVATE);
        hue = sp.getInt("theme_hue", -1);
        int mode = sp.getInt("theme_mode", 0);
        boolean sys = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        dark = mode == 1 || (mode == 2 && sys);
        rebuild();
    }

    /** Muda só o tom (sem reler o modo). Valor negativo = azul padrão. */
    static void setHue(int h) {
        hue = h;
        rebuild();
    }

    private static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[]{h, s, v});
    }

    private static void rebuild() {
        if (!dark) {
            WHITE = 0xFFFFFFFF;
            if (hue < 0) {
                NAVY = D_NAVY; ACCENT = D_ACCENT; GLOW = D_GLOW; SURFACE = D_SURFACE;
                TOP1 = D_TOP1; TOP2 = D_TOP2; BORDER = D_BORDER; BORDER2 = D_BORDER2;
                PRESS1 = D_PRESS1; PRESS2 = D_PRESS2; FACE = D_FACE; CARD2 = D_CARD2;
                FIELD2 = D_FIELD2; TEXT2 = D_TEXT2; MUTED = D_MUTED; HINT = D_HINT;
                return;
            }
            float h = hue % 360;
            NAVY = hsv(h, 0.85f, 0.20f);
            ACCENT = hsv(h, 0.84f, 0.88f);
            GLOW = hsv(h, 0.80f, 1.00f);
            SURFACE = hsv(h, 0.03f, 0.99f);
            TOP1 = hsv(h, 0.10f, 0.93f);
            TOP2 = hsv(h, 0.06f, 0.96f);
            BORDER = hsv(h, 0.38f, 0.96f);
            BORDER2 = hsv(h, 0.26f, 0.98f);
            PRESS1 = hsv(h, 0.13f, 1.00f);
            PRESS2 = hsv(h, 0.20f, 0.98f);
            FACE = hsv(h, 0.05f, 0.97f);
            CARD2 = hsv(h, 0.08f, 1.00f);
            FIELD2 = hsv(h, 0.05f, 0.99f);
            TEXT2 = hsv(h, 0.45f, 0.32f);
            MUTED = hsv(h, 0.30f, 0.50f);
            HINT = hsv(h, 0.22f, 0.60f);
        } else {
            float h = hue < 0 ? 222f : hue % 360;
            NAVY = hsv(h, 0.10f, 0.96f);
            ACCENT = hsv(h, 0.70f, 0.98f);
            GLOW = hsv(h, 0.75f, 1.00f);
            SURFACE = hsv(h, 0.35f, 0.10f);
            TOP1 = hsv(h, 0.40f, 0.17f);
            TOP2 = hsv(h, 0.38f, 0.14f);
            BORDER = hsv(h, 0.55f, 0.55f);
            BORDER2 = hsv(h, 0.50f, 0.40f);
            PRESS1 = hsv(h, 0.40f, 0.32f);
            PRESS2 = hsv(h, 0.42f, 0.26f);
            FACE = hsv(h, 0.38f, 0.18f);
            WHITE = hsv(h, 0.30f, 0.25f);
            CARD2 = hsv(h, 0.38f, 0.16f);
            FIELD2 = hsv(h, 0.35f, 0.15f);
            TEXT2 = hsv(h, 0.08f, 0.88f);
            MUTED = hsv(h, 0.15f, 0.72f);
            HINT = hsv(h, 0.15f, 0.58f);
        }
    }

    /** Pinta barra de status/navegação da tela conforme o tema. Chamar depois de montar a tela. */
    static void styleWindow(Activity a) {
        try {
            Window w = a.getWindow();
            w.setStatusBarColor(TOP1);
            w.setNavigationBarColor(SURFACE);
            View dec = w.getDecorView();
            int f = dec.getSystemUiVisibility();
            if (dark) {
                f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) f &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            dec.setSystemUiVisibility(f);
        } catch (Exception ignored) {
        }
    }

    /** Gira o tom do logo "Arbeon" (que é azul) para acompanhar a cor do tema. */
    static void tintLogo(ImageView v) {
        if (hue < 0) {
            v.clearColorFilter();
            return;
        }
        double r = Math.toRadians(hue - 212f);
        float cs = (float) Math.cos(r), sn = (float) Math.sin(r);
        float[] m = {
                0.213f + cs * 0.787f - sn * 0.213f, 0.715f - cs * 0.715f - sn * 0.715f, 0.072f - cs * 0.072f + sn * 0.928f, 0, 0,
                0.213f - cs * 0.213f + sn * 0.143f, 0.715f + cs * 0.285f + sn * 0.140f, 0.072f - cs * 0.072f - sn * 0.283f, 0, 0,
                0.213f - cs * 0.213f - sn * 0.787f, 0.715f - cs * 0.715f + sn * 0.715f, 0.072f + cs * 0.928f + sn * 0.072f, 0, 0,
                0, 0, 0, 1, 0};
        v.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(m)));
    }

    static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    private static GradientDrawable face(Context c, boolean oval, int top, int bottom) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        if (oval) g.setShape(GradientDrawable.OVAL);
        else g.setCornerRadius(dp(c, 100));
        g.setStroke(dp(c, 1), dark ? BORDER2 : 0xFFFFFFFF);
        return g;
    }

    /** Fundo de botão: círculo (oval) ou pílula, com estado de toque. */
    static StateListDrawable button(Context c, boolean oval) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, face(c, oval, PRESS1, PRESS2));
        sl.addState(new int[]{}, face(c, oval, WHITE, FACE));
        return sl;
    }

    /** Sombra com brilho do tema (Android 9+; antes disso fica uma sombra comum). */
    static void glow(View v, int elevDp) {
        v.setElevation(dp(v.getContext(), elevDp));
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineSpotShadowColor(GLOW);
            v.setOutlineAmbientShadowColor((GLOW & 0x00FFFFFF) | 0x77000000);
        }
    }
}
