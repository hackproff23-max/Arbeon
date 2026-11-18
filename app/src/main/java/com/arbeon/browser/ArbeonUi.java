package com.arbeon.browser;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** Pecinhas de interface no estilo azul do Arbeon: aviso (toast) e campo de texto em pílula. */
final class ArbeonUi {
    private ArbeonUi() {
    }

    /** Aviso em pílula branca com borda azul, no lugar do toast cinza padrão. */
    static void toast(Context c, CharSequence msg) {
        if (msg == null) return;
        try {
            TextView t = new TextView(c);
            t.setText(msg);
            t.setTextColor(Fx.NAVY);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setGravity(Gravity.CENTER);
            t.setMaxWidth(Fx.dp(c, 320));
            t.setPadding(Fx.dp(c, 22), Fx.dp(c, 12), Fx.dp(c, 22), Fx.dp(c, 12));
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFFFFFFFF, 0xFFE8EBF7});
            boolean multi = msg.length() > 36 || msg.toString().contains("\n");
            g.setCornerRadius(Fx.dp(c, multi ? 26 : 100));
            g.setStroke(Fx.dp(c, 2), 0xFF9DB7F5);
            t.setBackground(g);
            Toast toast = new Toast(c);
            toast.setView(t);
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, Fx.dp(c, 96));
            toast.setDuration(multi ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
            toast.show();
        } catch (Exception e) {
            Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
        }
    }

    /** Campo de texto em pílula com borda azul. */
    static void styleField(EditText e) {
        Context c = e.getContext();
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFEEF2FF});
        g.setCornerRadius(Fx.dp(c, 24));
        g.setStroke(Fx.dp(c, 2), 0xFF9DB7F5);
        e.setBackground(g);
        e.setPadding(Fx.dp(c, 18), Fx.dp(c, 12), Fx.dp(c, 18), Fx.dp(c, 12));
        e.setTextColor(Fx.NAVY);
        e.setHintTextColor(0xFF7A8099);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    }
}
