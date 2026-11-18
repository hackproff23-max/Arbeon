package com.arbeon.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
    private boolean themeChanged;
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
            // cores novas: reconstrói a tela do app para aplicar o tema (as guias recarregam)
            if (themeChanged) a.recreate();
        });
        showMain();
        Window w = d.getWindow();
        if (w != null) w.setLayout(MATCH, MATCH);
        styleDialogWindow();
        d.show();
    }

    private void styleDialogWindow() {
        Window w = d.getWindow();
        if (w == null) return;
        w.setStatusBarColor(Fx.SURFACE);
        w.setNavigationBarColor(Fx.TOP2);
        View dec = w.getDecorView();
        int f = dec.getSystemUiVisibility();
        dec.setSystemUiVisibility(Fx.dark ? (f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                : (f | (Fx.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)));
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
        field.setHintTextColor(Fx.HINT);
        field.setBackground(null);
        field.setPadding(dp(12), 0, 0, 0);

        LinearLayout pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(7), 0, dp(16), 0);
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.WHITE, Fx.FIELD2});
        pbg.setCornerRadius(dp(100));
        pbg.setStroke(dp(2), Fx.BORDER);
        pill.setBackground(pbg);
        Fx.glow(pill, 4);
        Glyph lupa = new Glyph(a, Glyph.SEARCH, Fx.NAVY, 20);
        lupa.setClickable(false);
        lupa.setBackground(circle(Fx.WHITE));
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
                new Item(SIcon.PALETTE, "Aparência", this::showAppearance),
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
            t.setTextColor(Fx.MUTED);
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

    // ---- Aparência: cores do tema

    private static final int[] PRESET_HUES = {-1, 140, 175, 270, 330, 0, 25};
    private static final String[] PRESET_NAMES = {"Azul", "Verde", "Turquesa", "Roxo", "Rosa", "Vermelho", "Laranja"};

    private void applyHue(int hue) {
        prefs.edit().putInt("theme_hue", hue).apply();
        Fx.setHue(hue);
        themeChanged = true;
        styleDialogWindow();
    }

    private void applyMode(int mode) {
        prefs.edit().putInt("theme_mode", mode).apply();
        Fx.init(a);
        themeChanged = true;
        styleDialogWindow();
    }

    private void showAppearance() {
        LinearLayout list = beginSub("Aparência");
        final int cur = prefs.getInt("theme_hue", -1);

        list.addView(note("Escolha o tema e a cor do Arbeon. Vale para botões, abas, barras, brilhos, ícones e o logo."), rowLp(4));

        // ---- tema: claro / escuro / sistema
        list.addView(sectionLabel("Tema"), sectionLp());
        final int mode = prefs.getInt("theme_mode", 0);
        String[] modeNames = {"Claro", "Escuro", "Sistema"};
        LinearLayout modes = new LinearLayout(a);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setClipChildren(false);
        modes.setPadding(0, dp(12), 0, dp(4));
        for (int i = 0; i < 3; i++) {
            final int m = i;
            boolean sel = i == mode;
            TextView t = new TextView(a);
            t.setText(modeNames[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            if (sel) {
                GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{Fx.GLOW, Fx.ACCENT});
                sg.setCornerRadius(dp(100));
                t.setBackground(sg);
                t.setTextColor(0xFFFFFFFF);
            } else {
                t.setBackground(Fx.button(a, false));
                t.setTextColor(Fx.NAVY);
            }
            Fx.glow(t, sel ? 5 : 3);
            t.setClickable(true);
            t.setOnClickListener(v -> {
                applyMode(m);
                showAppearance();
            });
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            mp.leftMargin = dp(4);
            mp.rightMargin = dp(4);
            modes.addView(t, mp);
        }
        list.addView(modes, lp(MATCH, 0, 0));

        // ---- prévia
        LinearLayout prev = new LinearLayout(a);
        prev.setOrientation(LinearLayout.HORIZONTAL);
        prev.setGravity(Gravity.CENTER_VERTICAL);
        prev.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.WHITE, Fx.FACE});
        pbg.setCornerRadius(dp(28));
        pbg.setStroke(dp(2), Fx.BORDER);
        prev.setBackground(pbg);
        Fx.glow(prev, 4);
        Glyph g1 = new Glyph(a, Glyph.RELOAD, Fx.ACCENT, 19);
        g1.setBackground(Fx.button(a, true));
        Fx.glow(g1, 4);
        prev.addView(g1, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView ex = new TextView(a);
        ex.setText("Exemplo");
        ex.setGravity(Gravity.CENTER);
        ex.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ex.setTypeface(Typeface.DEFAULT_BOLD);
        ex.setTextColor(0xFFFFFFFF);
        GradientDrawable eg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.GLOW, Fx.ACCENT});
        eg.setCornerRadius(dp(100));
        ex.setBackground(eg);
        Fx.glow(ex, 5);
        LinearLayout.LayoutParams exp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        exp.leftMargin = dp(14);
        prev.addView(ex, exp);
        list.addView(prev, rowLp(12));

        // ---- cores prontas
        TextView l1 = sectionLabel("Cores");
        list.addView(l1, sectionLp());
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        for (int i = 0; i < PRESET_HUES.length; i++) {
            final int hue = PRESET_HUES[i];
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClipChildren(false);
            TextView dot = new TextView(a);
            int base = hue < 0 ? 0xFF1F5FE0 : Color.HSVToColor(new float[]{hue, 0.84f, 0.88f});
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(base);
            if (hue == cur) dg.setStroke(dp(3), Fx.NAVY);
            dot.setBackground(dg);
            dot.setGravity(Gravity.CENTER);
            dot.setTextColor(0xFFFFFFFF);
            dot.setTypeface(Typeface.DEFAULT_BOLD);
            dot.setText(hue == cur ? "✓" : "");
            Fx.glow(dot, 4);
            cell.addView(dot, new LinearLayout.LayoutParams(dp(50), dp(50)));
            TextView nm = new TextView(a);
            nm.setText(PRESET_NAMES[i]);
            nm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            nm.setTextColor(Fx.NAVY);
            nm.setPadding(0, dp(6), 0, 0);
            cell.addView(nm, new LinearLayout.LayoutParams(WRAP, WRAP));
            cell.setOnClickListener(v -> {
                applyHue(hue);
                showAppearance();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(68), WRAP);
            row.addView(cell, cp);
        }
        hs.addView(row, new FrameLayout.LayoutParams(WRAP, WRAP));
        list.addView(hs, lp(MATCH, 0, 4));

        // ---- cor personalizada (barra de tons)
        list.addView(sectionLabel("Personalizada"), sectionLp());
        HueBar bar = new HueBar(a, cur < 0 ? 222 : cur, new HueBar.Listener() {
            @Override
            public void onHue(int hue, boolean done) {
                if (done) {
                    applyHue(hue);
                    showAppearance();
                }
            }
        });
        list.addView(bar, lp(MATCH, 54, 14));

        // ---- restaurar
        LinearLayout reset = pillRow(SIcon.PALETTE, "Restaurar azul padrão", null);
        reset.setOnClickListener(v -> {
            applyHue(-1);
            showAppearance();
        });
        list.addView(reset, rowLp(22));
        list.addView(note("Ao sair das configurações o Arbeon recarrega a tela para aplicar as cores (as guias abertas recarregam)."), rowLp(14));
    }

    /** Barra com todos os tons; arraste para escolher a cor. */
    private static final class HueBar extends View {
        interface Listener {
            void onHue(int hue, boolean done);
        }

        private final Listener l;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private int hue;

        HueBar(Activity a, int hue, Listener l) {
            super(a);
            this.hue = hue;
            this.l = l;
            Fx.glow(this, 3);
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float h = getHeight(), w = getWidth(), pad = h / 2f;
            int[] cols = new int[7];
            for (int i = 0; i < 7; i++) cols[i] = Color.HSVToColor(new float[]{i * 60f, 0.84f, 0.92f});
            p.setShader(new android.graphics.LinearGradient(pad, 0, w - pad, 0, cols, null,
                    android.graphics.Shader.TileMode.CLAMP));
            r.set(pad / 2f, h * 0.28f, w - pad / 2f, h * 0.72f);
            c.drawRoundRect(r, h * 0.22f, h * 0.22f, p);
            p.setShader(null);
            float x = pad + (w - 2 * pad) * (hue / 359f);
            p.setColor(Fx.WHITE);
            c.drawCircle(x, h / 2f, h * 0.42f, p);
            p.setColor(Color.HSVToColor(new float[]{hue, 0.84f, 0.92f}));
            c.drawCircle(x, h / 2f, h * 0.30f, p);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            float pad = getHeight() / 2f;
            float t = (e.getX() - pad) / Math.max(1f, getWidth() - 2 * pad);
            hue = Math.round(Math.max(0f, Math.min(1f, t)) * 359f);
            boolean up = e.getActionMasked() == android.view.MotionEvent.ACTION_UP;
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            invalidate();
            l.onHue(hue, up);
            return true;
        }
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
                new int[]{Fx.SURFACE, Fx.TOP1}));
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
                new int[]{Fx.WHITE, Fx.TOP2});
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
        t.setTextColor(Fx.MUTED);
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

        SIcon ic = new SIcon(a, icon, Fx.NAVY, 24);
        ic.setBackground(circle(Fx.WHITE));
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
            s.setTextColor(Fx.MUTED);
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
            g.setColor(Fx.PRESS2);
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
