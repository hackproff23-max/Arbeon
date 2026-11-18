package com.arbeon.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * Barra de endereço expandida (como no Chrome): ao tocar na barra, abre em tela cheia com o
 * campo em cima, a página atual (copiar/compartilhar), o texto copiado, as buscas recentes
 * (até 25, com X para apagar uma e lixeira para apagar todas) e sugestões do histórico.
 */
final class AddressBar {
    interface Host {
        void go(String input);

        void share();
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private AddressBar() {
    }

    private static int dp(Activity a, int v) {
        return Fx.dp(a, v);
    }

    static void show(final Activity a, final String url, final String title, final Host host) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Material_Light_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Fx.SURFACE);

        // ---- campo em pílula
        LinearLayout pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(a, 4), 0, dp(a, 4), 0);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.WHITE, Fx.FIELD2});
        bg.setCornerRadius(dp(a, 26));
        bg.setStroke(dp(a, 2), Fx.GLOW);
        pill.setBackground(bg);
        Fx.glow(pill, 3);

        Glyph back = new Glyph(a, Glyph.BACK, Fx.NAVY, 20);
        back.setOnClickListener(v -> d.dismiss());
        pill.addView(back, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));

        final EditText field = new EditText(a);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setHint("Pesquisar no Google ou digitar endereço");
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setTextColor(Fx.NAVY);
        field.setHintTextColor(Fx.HINT);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setImeOptions(EditorInfo.IME_ACTION_GO);
        field.setText(url);
        field.setPadding(dp(a, 4), 0, 0, 0);
        pill.addView(field, new LinearLayout.LayoutParams(0, MATCH, 1f));

        final Glyph clear = new Glyph(a, Glyph.CLOSE, Fx.MUTED, 16);
        clear.setVisibility(url.isEmpty() ? View.GONE : View.VISIBLE);
        clear.setOnClickListener(v -> field.setText(""));
        pill.addView(clear, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));

        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(MATCH, dp(a, 52));
        pp.setMargins(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 6));
        root.addView(pill, pp);

        // ---- sugestões
        ScrollView sv = new ScrollView(a);
        sv.setKeepScreenOn(false);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(a, 10), 0, dp(a, 10), dp(a, 12));
        sv.addView(list, new ViewGroup.LayoutParams(MATCH, WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        render(a, d, list, url, url, title, host);

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                clear.setVisibility(e.length() == 0 ? View.GONE : View.VISIBLE);
                render(a, d, list, e.toString(), url, title, host);
            }
        });
        field.setOnEditorActionListener((v, id, ev) -> {
            String s = field.getText().toString();
            if (!s.trim().isEmpty()) {
                d.dismiss();
                host.go(s);
            }
            return true;
        });

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Fx.SURFACE));
            w.setLayout(MATCH, MATCH);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();

        // abre "expandindo" a partir de cima
        root.setAlpha(0f);
        root.setTranslationY(-dp(a, 18));
        root.animate().alpha(1f).translationY(0f).setDuration(170).start();
        field.requestFocus();
        field.post(field::selectAll);
    }

    private static LinearLayout row(Activity a, LinearLayout list, int glyph, String t, String sub,
                                    int subColor, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 12), dp(a, 8), dp(a, 8), dp(a, 8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Fx.WHITE);
        bg.setCornerRadius(dp(a, 20));
        row.setBackground(bg);

        Glyph g = new Glyph(a, glyph, Fx.NAVY, 20);
        g.setClickable(false);
        row.addView(g, new LinearLayout.LayoutParams(dp(a, 30), dp(a, 30)));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a, 12), 0, dp(a, 6), 0);
        TextView tt = new TextView(a);
        tt.setText(t);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tt.setTextColor(Fx.NAVY);
        col.addView(tt, new LinearLayout.LayoutParams(MATCH, WRAP));
        if (sub != null && !sub.isEmpty()) {
            TextView ss = new TextView(a);
            ss.setText(sub);
            ss.setSingleLine(true);
            ss.setEllipsize(TextUtils.TruncateAt.END);
            ss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            ss.setTextColor(subColor);
            col.addView(ss, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        row.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.setOnClickListener(l);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(a, 8);
        list.addView(row, lp);
        return row;
    }

    /** Linha de busca recente: relógio, texto e X para apagar só esta busca. */
    private static void searchRow(Activity a, LinearLayout list, String q,
                                  View.OnClickListener open, View.OnClickListener del) {
        LinearLayout r = row(a, list, Glyph.HISTORY, q, null, Fx.MUTED, open);
        Glyph x = new Glyph(a, Glyph.CLOSE, Fx.MUTED, 16);
        x.setContentDescription("Apagar esta busca");
        x.setOnClickListener(del);
        r.addView(x, new LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)));
    }

    /** Título da seção com a lixeira (apagar todas) à direita. */
    private static void sectionHeader(Activity a, LinearLayout list, String label,
                                      View.OnClickListener onTrash) {
        LinearLayout h = new LinearLayout(a);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(a, 6), 0, 0, 0);
        TextView tv = new TextView(a);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Fx.MUTED);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(tv, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Glyph trash = new Glyph(a, Glyph.TRASH, Fx.MUTED, 20);
        trash.setContentDescription("Apagar todas as buscas");
        trash.setOnClickListener(onTrash);
        h.addView(trash, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(a, 12);
        list.addView(h, lp);
    }

    private static void confirmClearSearches(final Activity a, final Dialog d,
                                             final LinearLayout list, final String text,
                                             final String url, final String title,
                                             final Host host) {
        new ArbeonDialog(a)
                .setTitle("Apagar histórico de buscas?")
                .setMessage("Todas as buscas recentes serão removidas.")
                .setPositiveButton("Apagar", (x, y) -> {
                    SearchStore.clear(a);
                    render(a, d, list, text, url, title, host);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static String clip(Activity a) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence c = cm.getPrimaryClip().getItemAt(0).coerceToText(a);
                if (c != null && c.toString().trim().length() > 0) return c.toString().trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void render(final Activity a, final Dialog d, final LinearLayout list,
                               final String text, final String url, final String title,
                               final Host host) {
        list.removeAllViews();
        String q = text.trim();
        boolean empty = q.isEmpty() || q.equals(url);
        JSONArray hist = HistoryStore.list(a);

        if (empty) {
            if (!url.isEmpty()) {
                LinearLayout r = row(a, list, Glyph.GLOBE, TextUtils.isEmpty(title) ? url : title, url,
                        Fx.ACCENT, v -> {
                            d.dismiss();
                            host.go(url);
                        });
                TextView copy = new TextView(a);
                copy.setText("Copiar");
                copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                copy.setTextColor(Fx.ACCENT);
                copy.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 10));
                copy.setOnClickListener(v -> {
                    try {
                        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("url", url));
                        ArbeonUi.toast(a, "Link copiado");
                    } catch (Exception ignored) {
                    }
                });
                r.addView(copy, new LinearLayout.LayoutParams(WRAP, WRAP));
                Glyph sh = new Glyph(a, Glyph.SHARE, Fx.NAVY, 18);
                sh.setOnClickListener(v -> {
                    d.dismiss();
                    host.share();
                });
                r.addView(sh, new LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)));
            }
            final String cb = clip(a);
            if (cb != null) {
                row(a, list, Glyph.SEARCH, "Texto que você copiou", cb, Fx.MUTED, v -> {
                    d.dismiss();
                    host.go(cb);
                });
            }

            // ---- buscas recentes (até 25): X apaga uma, lixeira apaga todas
            final List<String> recents = SearchStore.list(a);
            if (!recents.isEmpty()) {
                sectionHeader(a, list, "Buscas recentes",
                        v -> confirmClearSearches(a, d, list, text, url, title, host));
                for (int j = 0; j < recents.size(); j++) {
                    final String s = recents.get(j);
                    final int idx = j;
                    searchRow(a, list, s, v -> {
                        d.dismiss();
                        host.go(s);
                    }, v -> {
                        SearchStore.remove(a, idx);
                        render(a, d, list, text, url, title, host);
                    });
                }
            }

            int n = 0;
            for (int i = 0; i < hist.length() && n < 6; i++) {
                JSONObject o = hist.optJSONObject(i);
                if (o == null) continue;
                final String u = o.optString("u");
                if (u.equals(url)) continue;
                String t = o.optString("t");
                row(a, list, Glyph.GLOBE, TextUtils.isEmpty(t) ? u : t, u, Fx.MUTED, v -> {
                    d.dismiss();
                    host.go(u);
                });
                n++;
            }
        } else {
            final String qq = q;
            row(a, list, Glyph.SEARCH, qq, "Pesquisar no Google", Fx.MUTED, v -> {
                d.dismiss();
                host.go(qq);
            });
            String low = q.toLowerCase(Locale.ROOT);

            // buscas recentes que combinam com o que está sendo digitado
            final List<String> rc = SearchStore.list(a);
            int sn = 0;
            for (int j = 0; j < rc.size() && sn < 5; j++) {
                final String s = rc.get(j);
                final int idx = j;
                if (s.equalsIgnoreCase(qq) || !s.toLowerCase(Locale.ROOT).contains(low)) continue;
                searchRow(a, list, s, v -> {
                    d.dismiss();
                    host.go(s);
                }, v -> {
                    SearchStore.remove(a, idx);
                    render(a, d, list, text, url, title, host);
                });
                sn++;
            }

            int n = 0;
            for (int i = 0; i < hist.length() && n < 6; i++) {
                JSONObject o = hist.optJSONObject(i);
                if (o == null) continue;
                final String u = o.optString("u");
                String t = o.optString("t");
                if (!u.toLowerCase(Locale.ROOT).contains(low) && !t.toLowerCase(Locale.ROOT).contains(low)) {
                    continue;
                }
                row(a, list, Glyph.GLOBE, TextUtils.isEmpty(t) ? u : t, u, Fx.MUTED, v -> {
                    d.dismiss();
                    host.go(u);
                });
                n++;
            }
        }
    }
}
