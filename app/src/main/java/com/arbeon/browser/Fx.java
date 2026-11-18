package com.arbeon.browser;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;

/** Estilo visual do Arbeon: botões claros com brilho. As cores seguem o tema escolhido em Aparência. */
final class Fx {
    // valores padrão (azul original)
    private static final int D_NAVY = 0xFF0B1230, D_ACCENT = 0xFF1F5FE0, D_GLOW = 0xFF2D6BFF,
            D_SURFACE = 0xFFF6F7FD, D_TOP1 = 0xFFD6DBF0, D_TOP2 = 0xFFE6E9F6,
            D_BORDER = 0xFF9DB7F5, D_BORDER2 = 0xFFBFD0FA,
            D_PRESS1 = 0xFFDDE5FF, D_PRESS2 = 0xFFC9D6FA, D_FACE = 0xFFE8EBF7;

    static int NAVY = D_NAVY;
    static int ACCENT = D_ACCENT;
    static int GLOW = D_GLOW;
    static int SURFACE = D_SURFACE;
    static int TOP1 = D_TOP1;
    static int TOP2 = D_TOP2;
    static int BORDER = D_BORDER;
    static int BORDER2 = D_BORDER2;
    static int PRESS1 = D_PRESS1;
    static int PRESS2 = D_PRESS2;
    static int FACE = D_FACE;

    private Fx() {
    }

    /** Lê o tema salvo (preferência "theme_hue"; -1 = azul padrão). Chamar no início do app. */
    static void init(Context c) {
        setHue(c.getSharedPreferences("arbeon", Context.MODE_PRIVATE).getInt("theme_hue", -1));
    }

    private static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[]{h, s, v});
    }

    /** Define o tema a partir de um tom (0-359). Valor negativo volta ao azul padrão. */
    static void setHue(int hue) {
        if (hue < 0) {
            NAVY = D_NAVY; ACCENT = D_ACCENT; GLOW = D_GLOW; SURFACE = D_SURFACE;
            TOP1 = D_TOP1; TOP2 = D_TOP2; BORDER = D_BORDER; BORDER2 = D_BORDER2;
            PRESS1 = D_PRESS1; PRESS2 = D_PRESS2; FACE = D_FACE;
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
        g.setStroke(dp(c, 1), 0xFFFFFFFF);
        return g;
    }

    /** Fundo de botão: círculo (oval) ou pílula, branco com toque do tema, com estado de toque. */
    static StateListDrawable button(Context c, boolean oval) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, face(c, oval, PRESS1, PRESS2));
        sl.addState(new int[]{}, face(c, oval, 0xFFFFFFFF, FACE));
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
