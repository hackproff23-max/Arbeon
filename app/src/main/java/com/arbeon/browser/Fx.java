package com.arbeon.browser;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;

/** Estilo visual do Arbeon: botões claros com brilho azul embaixo. */
final class Fx {
    static final int NAVY = 0xFF0B1230;
    static final int ACCENT = 0xFF1F5FE0;
    static final int GLOW = 0xFF2D6BFF;
    static final int SURFACE = 0xFFF6F7FD;
    static final int TOP1 = 0xFFD6DBF0;
    static final int TOP2 = 0xFFE6E9F6;

    private Fx() {
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

    /** Fundo de botão: círculo (oval) ou pílula, branco-lavanda, com estado de toque. */
    static StateListDrawable button(Context c, boolean oval) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, face(c, oval, 0xFFDDE5FF, 0xFFC9D6FA));
        sl.addState(new int[]{}, face(c, oval, 0xFFFFFFFF, 0xFFE8EBF7));
        return sl;
    }

    /** Sombra com brilho azul (Android 9+; antes disso fica uma sombra comum). */
    static void glow(View v, int elevDp) {
        v.setElevation(dp(v.getContext(), elevDp));
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineSpotShadowColor(GLOW);
            v.setOutlineAmbientShadowColor(0x772D6BFF);
        }
    }
}
