package com.arbeon.browser;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Janela de Configurações no mesmo estilo da tela de configurações do modelo (azul, pílulas, brilho). */
final class SettingsDialog {

    interface OnSave {
        void run(boolean js, boolean desktop);
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private SettingsDialog() {
    }

    static void show(final Activity a, boolean js, boolean desktop, final OnSave save, final Runnable clear) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final boolean[] st = {js, desktop};

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 18), dp(a, 18), dp(a, 18), dp(a, 18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFE9EEFF});
        bg.setCornerRadius(dp(a, 30));
        bg.setStroke(dp(a, 2), 0xFFBFD0FA);
        card.setBackground(bg);

        // ---- cabeçalho: seta voltar + título
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Glyph back = new Glyph(a, Glyph.BACK, Fx.ACCENT, 19);
        back.setBackground(Fx.button(a, true));
        Fx.glow(back, 4);
        back.setOnClickListener(v -> d.dismiss());
        head.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = new TextView(a);
        title.setText("Configurações");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Fx.NAVY);
        title.setPadding(dp(a, 14), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(WRAP, WRAP));
        card.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        // ---- opções em pílulas
        card.addView(row(a, Glyph.SLIDERS, "Ativar JavaScript", st, 0), rowLp(a, 18));
        card.addView(row(a, Glyph.GLOBE, "Modo desktop", st, 1), rowLp(a, 10));

        // ---- botões
        TextView btnSave = pill(a, "Salvar", true);
        btnSave.setOnClickListener(v -> {
            d.dismiss();
            save.run(st[0], st[1]);
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, dp(a, 50));
        sp.topMargin = dp(a, 22);
        card.addView(btnSave, sp);

        LinearLayout two = new LinearLayout(a);
        two.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnCancel = pill(a, "Cancelar", false);
        btnCancel.setOnClickListener(v -> d.dismiss());
        TextView btnClear = pill(a, "Limpar dados", false);
        btnClear.setOnClickListener(v -> {
            d.dismiss();
            clear.run();
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(a, 46), 1f);
        l1.rightMargin = dp(a, 6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(a, 46), 1f);
        l2.leftMargin = dp(a, 6);
        two.addView(btnCancel, l1);
        two.addView(btnClear, l2);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tp.topMargin = dp(a, 12);
        card.addView(two, tp);

        d.setContentView(card);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            int width = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.9f);
            w.setLayout(width, WRAP);
        }
        d.show();
    }

    private static int dp(android.content.Context c, int v) {
        return Fx.dp(c, v);
    }

    private static int dp(int v) {
        return v;
    }

    private static LinearLayout.LayoutParams rowLp(Activity a, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, dp(a, 60));
        p.topMargin = dp(a, topDp);
        return p;
    }

    private static LinearLayout row(final Activity a, int glyph, String label, final boolean[] st, final int idx) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(a, 8), 0, dp(a, 14), 0);
        r.setBackground(Fx.button(a, false));
        Fx.glow(r, 3);

        Glyph g = new Glyph(a, glyph, Fx.ACCENT, 19);
        g.setClickable(false);
        g.setBackground(circle(0xFFFFFFFF, 0));
        r.addView(g, new LinearLayout.LayoutParams(dp(a, 42), dp(a, 42)));

        TextView t = new TextView(a);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTextColor(Fx.NAVY);
        t.setPadding(dp(a, 14), 0, 0, 0);
        r.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));

        final TextView check = new TextView(a);
        check.setGravity(Gravity.CENTER);
        check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        check.setTypeface(Typeface.DEFAULT_BOLD);
        styleCheck(a, check, st[idx]);
        r.addView(check, new LinearLayout.LayoutParams(dp(a, 30), dp(a, 30)));

        r.setOnClickListener(v -> {
            st[idx] = !st[idx];
            styleCheck(a, check, st[idx]);
        });
        return r;
    }

    private static GradientDrawable circle(int color, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private static void styleCheck(Activity a, TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (on) {
            g.setColor(Fx.ACCENT);
            t.setText("\u2713");
            t.setTextColor(0xFFFFFFFF);
        } else {
            g.setColor(0xFFFFFFFF);
            g.setStroke(dp(a, 2), 0xFF9DB7F5);
            t.setText("");
        }
        t.setBackground(g);
    }

    private static TextView pill(Activity a, String text, boolean primary) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 17 : 15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        if (primary) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Fx.GLOW, Fx.ACCENT});
            g.setCornerRadius(dp(a, 100));
            t.setBackground(g);
            t.setTextColor(0xFFFFFFFF);
        } else {
            t.setBackground(Fx.button(a, false));
            t.setTextColor(Fx.NAVY);
        }
        Fx.glow(t, primary ? 5 : 3);
        t.setClickable(true);
        return t;
    }
}
