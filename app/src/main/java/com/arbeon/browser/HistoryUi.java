package com.arbeon.browser;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Tela de histórico: lista das páginas visitadas, toque para abrir, X para apagar uma, "Limpar tudo". */
final class HistoryUi {
    interface Host {
        void open(String url);
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private HistoryUi() {
    }

    static void show(final Activity a, final Host host) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Material_Light_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Fx.SURFACE);

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Fx.dp(a, 8), Fx.dp(a, 6), Fx.dp(a, 8), Fx.dp(a, 6));
        Glyph back = new Glyph(a, Glyph.BACK, Fx.NAVY, 22);
        back.setOnClickListener(v -> d.dismiss());
        head.addView(back, new LinearLayout.LayoutParams(Fx.dp(a, 44), Fx.dp(a, 44)));
        TextView title = new TextView(a);
        title.setText("Histórico");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Fx.NAVY);
        title.setPadding(Fx.dp(a, 8), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView clear = new TextView(a);
        clear.setText("Limpar tudo");
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        clear.setTypeface(Typeface.DEFAULT_BOLD);
        clear.setTextColor(Fx.ACCENT);
        clear.setPadding(Fx.dp(a, 12), Fx.dp(a, 10), Fx.dp(a, 12), Fx.dp(a, 10));
        head.addView(clear, new LinearLayout.LayoutParams(WRAP, WRAP));
        root.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView sv = new ScrollView(a);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Fx.dp(a, 10), 0, Fx.dp(a, 10), Fx.dp(a, 16));
        sv.addView(list, new ViewGroup.LayoutParams(MATCH, WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        clear.setOnClickListener(v -> new ArbeonDialog(a)
                .setTitle("Limpar histórico?")
                .setMessage("Todas as páginas visitadas serão apagadas da lista.")
                .setPositiveButton("Limpar", (dd, ww) -> {
                    HistoryStore.clear(a);
                    render(a, d, list, host);
                })
                .setNegativeButton("Cancelar", null)
                .show());

        render(a, d, list, host);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Fx.SURFACE));
            w.setLayout(MATCH, MATCH);
        }
        d.show();
    }

    private static void render(final Activity a, final Dialog d, final LinearLayout list, final Host host) {
        list.removeAllViews();
        JSONArray arr = HistoryStore.list(a);
        if (arr.length() == 0) {
            TextView t = new TextView(a);
            t.setText("Nenhum histórico ainda.");
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTextColor(Fx.MUTED);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Fx.dp(a, 48), 0, 0);
            list.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            final int index = i;
            final String url = o.optString("u");
            String t = o.optString("t");
            String host2 = Uri.parse(url).getHost();
            String sub = (host2 == null ? url : host2) + " • " + fmt.format(new Date(o.optLong("ts")));

            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Fx.dp(a, 12), Fx.dp(a, 8), Fx.dp(a, 4), Fx.dp(a, 8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Fx.WHITE);
            bg.setCornerRadius(Fx.dp(a, 18));
            row.setBackground(bg);

            Glyph g = new Glyph(a, Glyph.GLOBE, Fx.MUTED, 18);
            g.setClickable(false);
            row.addView(g, new LinearLayout.LayoutParams(Fx.dp(a, 28), Fx.dp(a, 28)));

            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Fx.dp(a, 10), 0, Fx.dp(a, 6), 0);
            TextView tt = new TextView(a);
            tt.setText(TextUtils.isEmpty(t) ? url : t);
            tt.setSingleLine(true);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tt.setTextColor(Fx.NAVY);
            col.addView(tt, new LinearLayout.LayoutParams(MATCH, WRAP));
            TextView ss = new TextView(a);
            ss.setText(sub);
            ss.setSingleLine(true);
            ss.setEllipsize(TextUtils.TruncateAt.END);
            ss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            ss.setTextColor(Fx.MUTED);
            col.addView(ss, new LinearLayout.LayoutParams(MATCH, WRAP));
            row.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));

            Glyph x = new Glyph(a, Glyph.CLOSE, Fx.MUTED, 14);
            x.setOnClickListener(v -> {
                HistoryStore.remove(a, index);
                render(a, d, list, host);
            });
            row.addView(x, new LinearLayout.LayoutParams(Fx.dp(a, 40), Fx.dp(a, 40)));

            row.setOnClickListener((View v) -> {
                d.dismiss();
                host.open(url);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.topMargin = Fx.dp(a, 6);
            list.addView(row, lp);
        }
    }
}
