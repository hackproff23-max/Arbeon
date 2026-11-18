package com.arbeon.browser;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Menu ⋮ em painel no canto superior direito: fileira de botões redondos + lista com ícones de brilho azul. */
final class AppMenu {
    // índices do array de ações
    static final int BACK = 0, FWD = 1, STAR = 2, DOWNLOAD = 3, RELOAD = 4,
            NEW_TAB = 5, CLEAR = 6, SETTINGS = 7, TRANSFERS = 8, BOOKMARKS = 9, SHARE = 10,
            HISTORY = 11, DESKTOP = 12;

    private AppMenu() {
    }

    private static int dp(Activity a, int v) {
        return Fx.dp(a, v);
    }

    private static Glyph round(Activity a, int type, int size, boolean enabled, boolean on) {
        Glyph g = new Glyph(a, type, enabled ? Fx.NAVY : Fx.HINT, 22);
        g.setOn(on);
        if (enabled) {
            g.setBackground(Fx.button(a, true));
            Fx.glow(g, 4);
        } else {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(Fx.FACE);
            g.setBackground(gd);
            g.setClickable(false);
        }
        return g;
    }

    static void show(final Activity a, boolean canBack, boolean canFwd, boolean starred,
                     boolean desktopOn, final Runnable[] act) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Material_Dialog_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout wrap = new FrameLayout(a);
        wrap.setPadding(dp(a, 14), dp(a, 4), dp(a, 14), dp(a, 18));
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setPadding(dp(a, 16), dp(a, 16), dp(a, 16), dp(a, 10));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.WHITE, Fx.FIELD2});
        bg.setCornerRadius(dp(a, 28));
        bg.setStroke(dp(a, 2), Fx.BORDER2);
        panel.setBackground(bg);
        Fx.glow(panel, 10);
        wrap.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- fileira de cima: voltar, avançar, favoritar, download, recarregar
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setClipChildren(false);
        int[] types = {Glyph.BACK, Glyph.FWD, Glyph.STAR, Glyph.DOWNLOAD, Glyph.RELOAD};
        boolean[] enabled = {canBack, canFwd, true, true, true};
        for (int i = 0; i < types.length; i++) {
            final int idx = i;
            FrameLayout cell = new FrameLayout(a);
            cell.setClipChildren(false);
            Glyph g = round(a, types[i], 50, enabled[i], i == STAR && starred);
            if (enabled[i]) {
                g.setOnClickListener(v -> {
                    d.dismiss();
                    if (act[idx] != null) act[idx].run();
                });
            }
            FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(dp(a, 48), dp(a, 48), Gravity.CENTER);
            cell.addView(g, gp);
            top.addView(cell, new LinearLayout.LayoutParams(0, dp(a, 56), 1f));
        }
        panel.addView(top);

        // ---- lista em grupos
        panel.addView(divider(a));
        item(a, d, panel, Glyph.PLUSBOX, false, "Nova guia", act, NEW_TAB);
        panel.addView(divider(a));
        item(a, d, panel, Glyph.TRASH, false, "Excluir dados de navegação", act, CLEAR);
        item(a, d, panel, Glyph.SLIDERS, false, "Configurações", act, SETTINGS);
        panel.addView(divider(a));
        item(a, d, panel, Glyph.HISTORY, false, "Histórico", act, HISTORY);
        item(a, d, panel, Glyph.DLCHECK, false, "Transferências", act, TRANSFERS);
        item(a, d, panel, Glyph.STAR, true, "Favoritos", act, BOOKMARKS);
        panel.addView(divider(a));
        LinearLayout desk = item(a, d, panel, Glyph.DESKTOP, desktopOn, "Modo computador", act, DESKTOP);
        Switch sw = new Switch(a);
        sw.setChecked(desktopOn);
        sw.setClickable(false);
        sw.setFocusable(false);
        desk.addView(sw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        item(a, d, panel, Glyph.SHARE, false, "Compartilhar…", act, SHARE);

        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw2 = a.getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (sw2 * 0.90f), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.TOP | Gravity.END);
            WindowManager.LayoutParams lp = w.getAttributes();
            int sb = 0;
            int rid = a.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (rid > 0) sb = a.getResources().getDimensionPixelSize(rid);
            lp.y = sb;
            lp.dimAmount = 0.28f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.setCanceledOnTouchOutside(true);
        d.show();
    }

    private static View divider(Activity a) {
        View v = new View(a);
        v.setBackgroundColor(Fx.TOP1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 1));
        lp.setMargins(dp(a, 4), dp(a, 6), dp(a, 4), dp(a, 6));
        v.setLayoutParams(lp);
        return v;
    }

    private static LinearLayout item(final Activity a, final Dialog d, LinearLayout panel, int type, boolean on,
                                     String label, final Runnable[] act, final int idx) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setPadding(dp(a, 2), dp(a, 5), dp(a, 8), dp(a, 5));
        row.setClickable(true);

        Glyph g = round(a, type, 44, true, on);
        g.setClickable(false);
        row.addView(g, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));

        TextView t = new TextView(a);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTextColor(Fx.NAVY);
        t.setPadding(dp(a, 16), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> {
            d.dismiss();
            if (act[idx] != null) act[idx].run();
        });
        panel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }
}
