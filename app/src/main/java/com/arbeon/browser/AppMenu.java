package com.arbeon.browser;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Menu ⋮ em painel claro arredondado, itens em pílulas, sem ícones (estilo GBA Emu). */
final class AppMenu {
    private static final String[] NAMES = {
            "NOVA GUIA", "FAVORITOS", "CONFIGURAÇÕES", "COMPARTILHAR PÁGINA", "DOWNLOADS"};

    private AppMenu() {
    }

    private static int dp(Activity a, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                a.getResources().getDisplayMetrics());
    }

    private static GradientDrawable pill(Activity a, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(a, 22));
        g.setStroke(dp(a, 1), 0x33FFFFFF);
        return g;
    }

    /** actions na mesma ordem de NAMES. */
    static void show(final Activity a, final Runnable[] actions) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Material_Dialog_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a, 20), dp(a, 22), dp(a, 20), dp(a, 20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF7FFFFFF, 0xF2DCE1E9});
        bg.setCornerRadius(dp(a, 30));
        bg.setStroke(dp(a, 2), 0xFFFFFFFF);
        panel.setBackground(bg);

        Typeface cond = Typeface.create("sans-serif-condensed", Typeface.BOLD);

        TextView title = new TextView(a);
        title.setText("MENU");
        title.setTypeface(cond);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setLetterSpacing(0.03f);
        title.setTextColor(0xFF15161A);
        title.setPadding(dp(a, 4), 0, 0, dp(a, 14));
        panel.addView(title);

        // subtítulo do item Downloads: quantos estão baixando agora
        int running = 0;
        try {
            for (DlItem it : DownloadEngine.get(a).items) if (it.state == DlItem.RUNNING) running++;
        } catch (Exception ignored) {
        }

        for (int i = 0; i < NAMES.length; i++) {
            final int idx = i;
            String sub = null;
            if (i == 2) sub = "JAVASCRIPT E MODO DESKTOP";
            if (i == 4) sub = running > 0 ? running + (running == 1 ? " BAIXANDO AGORA" : " BAIXANDO AGORA") : "VELOCIDADE, FILA E LINKS";

            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(a, 20), dp(a, 14), dp(a, 16), dp(a, 14));
            StateListDrawable sl = new StateListDrawable();
            sl.addState(new int[]{android.R.attr.state_pressed}, pill(a, 0xFFE3E7EF));
            sl.addState(new int[]{}, pill(a, 0xD9FFFFFF));
            row.setBackground(sl);
            row.setClickable(true);

            LinearLayout texts = new LinearLayout(a);
            texts.setOrientation(LinearLayout.VERTICAL);

            TextView name = new TextView(a);
            name.setText(NAMES[i]);
            name.setTypeface(cond);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            name.setLetterSpacing(0.02f);
            name.setTextColor(0xFF15161A);
            texts.addView(name);

            if (sub != null) {
                TextView s = new TextView(a);
                s.setText(sub);
                s.setTypeface(cond);
                s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                s.setLetterSpacing(0.02f);
                s.setTextColor(0xFF6B7280);
                texts.addView(s);
            }
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView chev = new TextView(a);
            chev.setText("›");
            chev.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            chev.setTextColor(0xFF8A8F98);
            row.addView(chev);

            row.setOnClickListener(v -> {
                d.dismiss();
                if (idx < actions.length && actions[idx] != null) actions[idx].run();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(a, 10);
            panel.addView(row, lp);
        }

        d.setContentView(panel);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw = a.getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (sw * 0.90f), WindowManager.LayoutParams.WRAP_CONTENT);
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
        d.show();
    }
}
