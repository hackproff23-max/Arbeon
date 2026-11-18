package com.arbeon.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Normalizer;

/** Tela cheia de Configurações do Arbeon, no estilo azul com pílulas e brilho. */
final class SettingsUi {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int ICON = 0xFF12248A;
    private static final int BG_TOP = 0xFFF3F6FF;
    private static final int BG_BOT = 0xFFE2ECFF;

    private static final class Item {
        final int icon;
        final String label;
        final Runnable run;

        Item(int icon, String label, Runnable run) {
            this.icon = icon;
            this.label = label;
            this.run = run;
        }
    }

    private final Activity a;
    private final SharedPreferences prefs;
    private final SettingsDialog.OnSave save;
    private final Runnable clear;
    private Dialog d;
    private boolean changed;
    private boolean onSub;

    private SettingsUi(Activity a, SettingsDialog.OnSave save, Runnable clear) {
        this.a = a;
        this.save = save;
        this.clear = clear;
        this.prefs = a.getSharedPreferences("arbeon", Activity.MODE_PRIVATE);
    }

    static void show(Activity a, SettingsDialog.OnSave save, Runnable clear) {
        new SettingsUi(a, save, clear).open();
    }

    private int dp(int v) {
        return Fx.dp(a, v);
    }

    // ------------------------------------------------------------------ janela

    private void open() {
        d = new Dialog(a, android.R.style.Theme_Material_Light_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setOnKeyListener((dlg, key, ev) -> {
            if (key == KeyEvent.KEYCODE_BACK && ev.getAction() == KeyEvent.ACTION_UP && onSub) {
                showMain();
                return true;
            }
            return false;
        });
        d.setOnDismissListener(x -> {
            if (changed) save.run(prefs.getBoolean("js", true), prefs.getBoolean("desktop", false));
        });
        showMain();
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(MATCH, MATCH);
            w.setStatusBarColor(BG_TOP);
            w.setNavigationBarColor(BG_BOT);
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        d.show();
    }

    // ------------------------------------------------------------------ tela principal

    private void showMain() {
        onSub = false;
        LinearLayout root = newRoot();
        root.addView(header("Configurações", true), lp(MATCH, 48, 0));

        final EditText field = new EditText(a);
        field.setHint("Pesquise nas configurações");
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        field.setTextColor(Fx.NAVY);
        field.setHintTextColor(0xFF7A8099);
        field.setBackground(null);
        field.setPadding(dp(12), 0, 0, 0);

        LinearLayout pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(7), 0, dp(16), 0);
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFF1F3FC});
        pbg.setCornerRadius(dp(100));
        pbg.setStroke(dp(2), 0xFF9DB7F5);
        pill.setBackground(pbg);
        Fx.glow(pill, 4);
        Glyph lupa = new Glyph(a, Glyph.SEARCH, Fx.NAVY, 20);
        lupa.setClickable(false);
        lupa.setBackground(circle(0xFFFFFFFF));
        Fx.glow(lupa, 2);
        pill.addView(lupa, new LinearLayout.LayoutParams(dp(44), dp(44)));
        pill.addView(field, new LinearLayout.LayoutParams(0, MATCH, 1f));
        LinearLayout.LayoutParams pl = lp(MATCH, 58, 20);
        pl.leftMargin = dp(2);
        pl.rightMargin = dp(2);
        root.addView(pill, pl);

        ScrollView sc = new ScrollView(a);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipChildren(false);
        sc.setClipToPadding(false);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(2), dp(4), dp(2), dp(24));
        sc.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(sc, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        final Item[] main = {
                new Item(SIcon.SHIELD_LOCK, "Privacidade e segurança", this::showPrivacy),
                new Item(SIcon.GEAR, "Configurações de sites", this::showSites),
                new Item(SIcon.COMPASS, "Navegador padrão", this::openDefaultBrowser)
        };
        final Item[] adv = {
                new Item(SIcon.BELL, "Notificações", this::openNotifications),
                new Item(SIcon.ACCESS, "Acessibilidade", this::openAccessibility),
                new Item(SIcon.INFO, "Sobre o Arbeon", this::showAbout)
        };
        buildList(list, main, adv, "");
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                buildList(list, main, adv, s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        d.setContentView(root);
    }

    private void buildList(LinearLayout list, Item[] main, Item[] adv, String q) {
        list.removeAllViews();
        String nq = norm(q);
        int shown = 0;
        for (Item it : main) {
            if (norm(it.label).contains(nq)) {
                list.addView(itemRow(it), rowLp(12));
                shown++;
            }
        }
        boolean header = false;
        for (Item it : adv) {
            if (norm(it.label).contains(nq)) {
                if (!header) {
                    list.addView(sectionLabel("Avançadas"), sectionLp());
                    header = true;
                }
                list.addView(itemRow(it), rowLp(12));
                shown++;
            }
        }
        if (shown == 0) {
            TextView t = new TextView(a);
            t.setText("Nenhuma configuração encontrada");
            t.setGravity(Gravity.CENTER);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTextColor(0xFF5B6280);
            list.addView(t, lp(MATCH, 0, 40));
        }
    }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    // ------------------------------------------------------------------ subtelas

    private LinearLayout beginSub(String title) {
        onSub = true;
        LinearLayout root = newRoot();
        root.addView(header(title, false), lp(MATCH, 48, 0));
        ScrollView sc = new ScrollView(a);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipChildren(false);
        sc.setClipToPadding(false);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(2), dp(8), dp(2), dp(24));
        sc.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(sc, lp(MATCH, 0, 16));
        ((LinearLayout.LayoutParams) sc.getLayoutParams()).height = 0;
        ((LinearLayout.LayoutParams) sc.getLayoutParams()).weight = 1f;
        d.setContentView(root);
        return list;
    }

    private void showPrivacy() {
        LinearLayout list = beginSub("Privacidade e segurança");
        list.addView(note("Apague cookies, cache, histórico e dados de sites de todas as guias."), rowLp(4));
        LinearLayout r = pillRow(SIcon.TRASH, "Excluir dados de navegação", null);
        r.addView(chevron(), new LinearLayout.LayoutParams(dp(24), dp(24)));
        r.setOnClickListener(v -> new ArbeonDialog(a)
                .setTitle("Excluir dados de navegação?")
                .setMessage("Cookies, cache, histórico e dados de sites serão apagados.")
                .setPositiveButton("Excluir", (dd, ww) -> clear.run())
                .setNegativeButton("Cancelar", null)
                .show());
        list.addView(r, rowLp(12));
    }

    private void showSites() {
        LinearLayout list = beginSub("Configurações de sites");
        addToggle(list, SIcon.SLIDERS, "Ativar JavaScript", "Necessário para a maioria dos sites", "js", true);
        addToggle(list, SIcon.MONITOR, "Modo desktop", "Pede a versão para computador dos sites", "desktop", false);
        list.addView(note("As alterações são aplicadas às guias abertas quando você sair daqui."), rowLp(16));
    }

    private void showAbout() {
        new ArbeonDialog(a)
                .setTitle("Arbeon")
                .setMessage("Navegador simples para Android.\nVersão 1.0")
                .setPositiveButton("OK", null)
                .show();
    }

    // ------------------------------------------------------------------ ações do sistema

    private void startSafe(Intent i) {
        try {
            a.startActivity(i);
        } catch (Exception e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                ArbeonUi.toast(a, "Não foi possível abrir as configurações do sistema");
            }
        }
    }

    private void openDefaultBrowser() {
        startSafe(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
    }

    private void openNotifications() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 26) {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName());
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:" + a.getPackageName()));
        }
        startSafe(i);
    }

    private void openAccessibility() {
        startSafe(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    // ------------------------------------------------------------------ peças visuais

    private LinearLayout newRoot() {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOT}));
        root.setPadding(dp(16), dp(14), dp(16), 0);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        return root;
    }

    private LinearLayout header(String title, boolean help) {
        LinearLayout h = new LinearLayout(a);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setClipChildren(false);
        Glyph back = new Glyph(a, Glyph.BACK, Fx.ACCENT, 20);
        back.setBackground(Fx.button(a, true));
        Fx.glow(back, 5);
        back.setOnClickListener(v -> {
            if (onSub) showMain();
            else d.dismiss();
        });
        h.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView t = new TextView(a);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Fx.NAVY);
        t.setPadding(dp(18), 0, dp(8), 0);
        h.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));

        if (help) {
            SIcon q = new SIcon(a, SIcon.HELP, Fx.ACCENT, 24);
            q.setBackground(Fx.button(a, true));
            Fx.glow(q, 5);
            q.setOnClickListener(v -> showAbout());
            h.addView(q, new LinearLayout.LayoutParams(dp(46), dp(46)));
        }
        return h;
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Fx.ACCENT);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(32), dp(10), dp(22), dp(10));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFEAF0FF});
        g.setCornerRadius(dp(100));
        t.setBackground(g);
        Fx.glow(t, 3);
        return t;
    }

    private LinearLayout.LayoutParams sectionLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, WRAP);
        p.topMargin = dp(26);
        return p;
    }

    private TextView note(String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(0xFF5B6280);
        t.setPadding(dp(8), 0, dp(8), 0);
        return t;
    }

    private SIcon chevron() {
        return new SIcon(a, SIcon.CHEVRON, Fx.NAVY, 22);
    }

    private LinearLayout.LayoutParams lp(int w, int hDp, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hDp == 0 ? WRAP : dp(hDp));
        p.topMargin = dp(topDp);
        return p;
    }

    private LinearLayout.LayoutParams rowLp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.topMargin = dp(topDp);
        return p;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Linha em pílula: ícone em círculo branco + texto (e subtítulo opcional). */
    private LinearLayout pillRow(int icon, String label, String sub) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(9), dp(8), dp(18), dp(8));
        r.setMinimumHeight(dp(66));
        r.setBackground(Fx.button(a, false));
        Fx.glow(r, 4);
        r.setClickable(true);

        SIcon ic = new SIcon(a, icon, ICON, 24);
        ic.setBackground(circle(0xFFFFFFFF));
        Fx.glow(ic, 2);
        r.addView(ic, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(16), 0, dp(8), 0);
        TextView l = new TextView(a);
        l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        l.setTextColor(Fx.NAVY);
        texts.addView(l, new LinearLayout.LayoutParams(MATCH, WRAP));
        if (sub != null) {
            TextView s = new TextView(a);
            s.setText(sub);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            s.setTextColor(0xFF5B6280);
            texts.addView(s, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return r;
    }

    private LinearLayout itemRow(final Item it) {
        LinearLayout r = pillRow(it.icon, it.label, null);
        r.addView(chevron(), new LinearLayout.LayoutParams(dp(24), dp(24)));
        r.setOnClickListener(v -> it.run.run());
        return r;
    }

    private void addToggle(LinearLayout list, int icon, String label, String sub, final String key, boolean def) {
        final boolean[] on = {prefs.getBoolean(key, def)};
        LinearLayout r = pillRow(icon, label, sub);
        final FrameLayout track = new FrameLayout(a);
        final View thumb = new View(a);
        track.addView(thumb);
        styleToggle(track, thumb, on[0]);
        r.addView(track, new LinearLayout.LayoutParams(dp(54), dp(30)));
        r.setOnClickListener(v -> {
            on[0] = !on[0];
            prefs.edit().putBoolean(key, on[0]).apply();
            changed = true;
            styleToggle(track, thumb, on[0]);
        });
        list.addView(r, rowLp(12));
    }

    private void styleToggle(FrameLayout track, View thumb, boolean on) {
        GradientDrawable g;
        if (on) {
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Fx.GLOW, Fx.ACCENT});
        } else {
            g = new GradientDrawable();
            g.setColor(0xFFC9D3EE);
        }
        g.setCornerRadius(dp(100));
        track.setBackground(g);
        thumb.setBackground(circle(0xFFFFFFFF));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(24), dp(24),
                Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START));
        tp.setMargins(dp(3), 0, dp(3), 0);
        thumb.setLayoutParams(tp);
    }
}
