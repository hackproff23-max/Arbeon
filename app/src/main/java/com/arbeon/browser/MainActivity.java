package com.arbeon.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int BLUE = 0xFF1A73E8;
    private static final int GRAY = 0xFF5F6368;
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Light_Dialog_Alert;
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";
    private static final String GOOGLE_SEARCH = "https://www.google.com/search?q=";

    private static class Tab {
        WebView web;
        String title = "Nova guia";
        String url = "";
        boolean home = true;
        TextView chipTitle;
    }

    private final ArrayList<Tab> tabs = new ArrayList<>();
    private int cur = -1;
    private SharedPreferences prefs;
    private String defaultUA;

    private LinearLayout tabStrip;
    private HorizontalScrollView tabScroll;
    private Glyph btnBack, btnFwd, btnStar;
    private Glyph btnReload, pullIndicator;
    private float pullStartY, pullDist;
    private boolean pullArmed;
    private EditText address;
    private ProgressBar progress;
    private FrameLayout webContainer;
    private ScrollView homeView;
    private GridLayout homeGrid;
    private TextView homeBmLabel;
    private EditText homeSearch;

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("arbeon", MODE_PRIVATE);
        Fx.init(this);
        buildUi();
        Fx.styleWindow(this);
        WebPrefs.applyBars(this);
        String start = getIntent() != null ? getIntent().getDataString() : null;
        if (start == null || start.isEmpty()) {
            // sem link de fora: volta para o último site visitado
            start = prefs.getString("last_url", null);
        }
        newTab(start);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String d = intent.getDataString();
        if (d != null) newTab(d);
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (Tab t : tabs) t.web.onPause();
        saveLastUrl();
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (Tab t : tabs) t.web.onResume();
    }

    @Override
    protected void onDestroy() {
        for (Tab t : tabs) {
            webContainer.removeView(t.web);
            t.web.destroy();
        }
        tabs.clear();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Tab t = curTab();
        if (!t.home && t.web.canGoBack()) {
            t.web.goBack();
        } else if (!t.home) {
            t.home = true;
            t.url = "";
            t.title = "Nova guia";
            show(t);
            renderTabs();
        } else if (tabs.size() > 1) {
            closeTab(t);
        } else {
            super.onBackPressed();
        }
    }

    /** Guarda o último site aberto para reabrir nele quando o app for aberto de novo. */
    private void rememberUrl(String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:") || url.startsWith("data:")) return;
        prefs.edit().putString("last_url", url).apply();
    }

    private void saveLastUrl() {
        try {
            if (cur >= 0 && cur < tabs.size()) {
                Tab t = curTab();
                if (!t.home) rememberUrl(t.url);
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ interface

    private int dp(int v) {
        return Fx.dp(this, v);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    /** Botão redondo claro com brilho azul, igual ao modelo. */
    private Glyph roundButton(int type, int color, View.OnClickListener l) {
        Glyph g = new Glyph(this, type, color, 19);
        g.setBackground(Fx.button(this, true));
        Fx.glow(g, 4);
        g.setOnClickListener(l);
        return g;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(38), dp(38));
        p.setMargins(dp(2), 0, dp(2), 0);
        return p;
    }

    /** Campo em pílula branca com borda azul e lupa (usado na barra e na tela inicial). */
    private LinearLayout searchPill(EditText field, int heightDp, final GradientDrawable[] bgOut) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(14), 0, dp(12), 0);
        final GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.WHITE, Fx.FIELD2});
        bg.setCornerRadius(dp(heightDp / 2));
        bg.setStroke(dp(2), Fx.BORDER);
        pill.setBackground(bg);
        Fx.glow(pill, 3);

        Glyph s = new Glyph(this, Glyph.SEARCH, Fx.NAVY, 18);
        s.setClickable(false);
        pill.addView(s, new LinearLayout.LayoutParams(dp(20), dp(20)));

        field.setBackground(null);
        field.setPadding(dp(10), 0, 0, 0);
        field.setTextColor(Fx.NAVY);
        field.setHintTextColor(Fx.HINT);
        field.setOnFocusChangeListener((v, focused) -> bg.setStroke(dp(2), focused ? Fx.GLOW : Fx.BORDER));
        pill.addView(field, new LinearLayout.LayoutParams(0, MATCH, 1f));
        if (bgOut != null) bgOut[0] = bg;
        return pill;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Fx.SURFACE);
        root.setFitsSystemWindows(true);
        root.setClipChildren(false);

        // ---- barra de guias (em cima)
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.TOP1, Fx.TOP2}));
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        tabBar.setClipChildren(false);

        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(8), dp(6), 0, 0);
        tabScroll.addView(tabStrip, new FrameLayout.LayoutParams(WRAP, MATCH));
        tabBar.addView(tabScroll, new LinearLayout.LayoutParams(0, MATCH, 1f));

        Glyph plus = new Glyph(this, Glyph.PLUS, Fx.NAVY, 22);
        plus.setBackground(Fx.button(this, false));
        Fx.glow(plus, 4);
        plus.setOnClickListener(v -> newTab(null));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(54), dp(36));
        pl.setMargins(dp(4), 0, dp(10), 0);
        tabBar.addView(plus, pl);
        root.addView(tabBar, new LinearLayout.LayoutParams(MATCH, dp(50)));

        // ---- barra de endereço
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), 0, dp(6), 0);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        bar.setBackgroundColor(Fx.SURFACE);
        bar.setElevation(dp(1));

        btnBack = roundButton(Glyph.BACK, Fx.NAVY, v -> {
            Tab t = curTab();
            if (!t.home && t.web.canGoBack()) t.web.goBack();
        });
        btnFwd = roundButton(Glyph.FWD, Fx.NAVY, v -> {
            Tab t = curTab();
            if (!t.home && t.web.canGoForward()) t.web.goForward();
        });
        btnReload = roundButton(Glyph.RELOAD, Fx.ACCENT, v -> {
            Tab t = curTab();
            if (!t.home) {
                spinReload();
                t.web.reload();
            }
        });

        address = new EditText(this);
        address.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        address.setSingleLine(true);
        address.setHint("Pesquisar ou digitar endereço");
        address.setSelectAllOnFocus(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v, actionId, event) -> {
            go(address.getText().toString());
            return true;
        });
        LinearLayout addrPill = searchPill(address, 40, null);
        // toque na barra: abre a barra de endereço expandida (como no Chrome)
        address.setFocusable(false);
        address.setFocusableInTouchMode(false);
        address.setCursorVisible(false);
        address.setOnClickListener(v -> openAddressBar());
        addrPill.setOnClickListener(v -> openAddressBar());

        btnStar = roundButton(Glyph.STAR, Fx.NAVY, v -> toggleBookmark());
        Glyph btnDl = roundButton(Glyph.DOWNLOAD, Fx.NAVY, v -> DownloadsUi.show(this));
        Glyph btnMenu = roundButton(Glyph.DOTS, Fx.NAVY, this::showMenu);

        bar.addView(btnBack, btnLp());
        bar.addView(btnFwd, btnLp());
        bar.addView(btnReload, btnLp());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(40), 1f);
        ap.setMargins(dp(3), 0, dp(3), 0);
        bar.addView(addrPill, ap);
        bar.addView(btnStar, btnLp());
        bar.addView(btnDl, btnLp());
        bar.addView(btnMenu, btnLp());
        root.addView(bar, new LinearLayout.LayoutParams(MATCH, dp(56)));

        // ---- progresso
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(Fx.GLOW));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(MATCH, dp(3)));

        // ---- conteúdo: páginas + tela inicial
        FrameLayout content = new FrameLayout(this);
        webContainer = new FrameLayout(this);
        content.addView(webContainer, new FrameLayout.LayoutParams(MATCH, MATCH));
        buildHome();
        content.addView(homeView, new FrameLayout.LayoutParams(MATCH, MATCH));
        pullIndicator = roundButton(Glyph.RELOAD, Fx.ACCENT, null);
        pullIndicator.setClickable(false);
        pullIndicator.setAlpha(0f);
        pullIndicator.setTranslationY(-dp(48));
        content.addView(pullIndicator, new FrameLayout.LayoutParams(dp(40), dp(40),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        setContentView(root);
    }

    private void buildHome() {
        homeView = new ScrollView(this);
        homeView.setFillViewport(true);
        homeView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.SURFACE, Fx.WHITE}));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(20), dp(72), dp(20), dp(24));
        col.setClipChildren(false);
        col.setClipToPadding(false);

        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.arbeon_logo);
        Fx.tintLogo(logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(290), WRAP);
        logoLp.bottomMargin = dp(6);
        col.addView(logo, logoLp);

        TextView sub = new TextView(this);
        sub.setText("Pesquise com o Google");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setTextColor(Fx.MUTED);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(WRAP, WRAP);
        sp.bottomMargin = dp(28);
        col.addView(sub, sp);

        homeSearch = new EditText(this);
        homeSearch.setHint("Pesquisar no Google ou digitar endereço");
        homeSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        homeSearch.setSingleLine(true);
        homeSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        homeSearch.setOnEditorActionListener((v, actionId, event) -> {
            go(homeSearch.getText().toString());
            return true;
        });
        col.addView(searchPill(homeSearch, 54, null), new LinearLayout.LayoutParams(MATCH, dp(54)));

        homeBmLabel = new TextView(this);
        homeBmLabel.setText("Favoritos");
        homeBmLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        homeBmLabel.setTextColor(Fx.MUTED);
        homeBmLabel.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(36);
        col.addView(homeBmLabel, lp);

        homeGrid = new GridLayout(this);
        homeGrid.setColumnCount(4);
        homeGrid.setClipChildren(false);
        col.addView(homeGrid, new LinearLayout.LayoutParams(MATCH, WRAP));

        homeView.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
    }

    private void refreshHome() {
        homeGrid.removeAllViews();
        JSONArray a = loadBookmarks();
        homeBmLabel.setVisibility(a.length() == 0 ? View.GONE : View.VISIBLE);
        int max = Math.min(a.length(), 8);
        for (int i = 0; i < max; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final String url = o.optString("url");
            String title = o.optString("title");
            if (TextUtils.isEmpty(title)) title = Uri.parse(url).getHost();
            if (TextUtils.isEmpty(title)) title = url;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setClipChildren(false);

            TextView circle = new TextView(this);
            circle.setText(title.substring(0, 1).toUpperCase());
            circle.setGravity(Gravity.CENTER);
            circle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            circle.setTypeface(Typeface.DEFAULT_BOLD);
            circle.setTextColor(Fx.ACCENT);
            circle.setBackground(Fx.button(this, true));
            Fx.glow(circle, 4);
            item.addView(circle, new LinearLayout.LayoutParams(dp(52), dp(52)));

            TextView label = new TextView(this);
            label.setText(title);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            label.setTextColor(Fx.TEXT2);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(MATCH, WRAP);
            llp.topMargin = dp(8);
            item.addView(label, llp);

            item.setOnClickListener(v -> go(url));

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            glp.width = 0;
            glp.setMargins(dp(4), dp(12), dp(4), dp(4));
            homeGrid.addView(item, glp);
        }
    }

    // ------------------------------------------------------------------ guias

    private Tab curTab() {
        return tabs.get(cur);
    }

    private boolean isCur(Tab t) {
        return cur >= 0 && cur < tabs.size() && tabs.get(cur) == t;
    }

    private void newTab(String url) {
        Tab t = new Tab();
        t.web = createWebView(t);
        tabs.add(t);
        cur = tabs.size() - 1;
        if (url != null && !url.isEmpty()) {
            t.home = false;
            t.url = url;
            t.title = url;
            show(t);
            t.web.loadUrl(url);
        } else {
            show(t);
        }
        renderTabs();
        tabScroll.post(() -> tabScroll.fullScroll(View.FOCUS_RIGHT));
    }

    private void select(int i) {
        cur = i;
        show(tabs.get(i));
        renderTabs();
    }

    private void closeTab(Tab t) {
        int i = tabs.indexOf(t);
        if (i < 0) return;
        webContainer.removeView(t.web);
        t.web.destroy();
        tabs.remove(i);
        if (tabs.isEmpty()) {
            cur = -1;
            newTab(null);
            return;
        }
        if (cur > i) cur--;
        if (cur >= tabs.size()) cur = tabs.size() - 1;
        select(cur);
    }

    private String titleOf(Tab t) {
        if (t.home) return "Nova guia";
        return TextUtils.isEmpty(t.title) ? t.url : t.title;
    }

    private void renderTabs() {
        tabStrip.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final Tab t = tabs.get(i);
            boolean sel = (i == cur);

            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(12), 0, dp(4), 0);
            float r = dp(sel ? 22 : 18);
            float[] radii = {r, r, r, r, 0, 0, 0, 0};
            if (sel) {
                // aba ativa: fundo claro com a linha azul brilhante em cima
                GradientDrawable glow = new GradientDrawable();
                glow.setColor(Fx.GLOW);
                glow.setCornerRadii(radii);
                GradientDrawable face = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{Fx.WHITE, Fx.SURFACE});
                face.setCornerRadii(radii);
                LayerDrawable ld = new LayerDrawable(new Drawable[]{glow, face});
                ld.setLayerInset(1, 0, dp(3), 0, 0);
                chip.setBackground(ld);
            } else {
                GradientDrawable g = new GradientDrawable();
                g.setColor(0x66FFFFFF);
                g.setCornerRadii(radii);
                chip.setBackground(g);
            }

            Glyph globe = new Glyph(this, Glyph.GLOBE, sel ? Fx.ACCENT : Fx.MUTED, 18);
            globe.setClickable(false);
            chip.addView(globe, new LinearLayout.LayoutParams(dp(22), dp(22)));

            TextView title = new TextView(this);
            title.setText(titleOf(t));
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setMaxWidth(dp(110));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            title.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            title.setTextColor(sel ? Fx.NAVY : Fx.MUTED);
            title.setPadding(dp(8), 0, dp(4), 0);
            t.chipTitle = title;
            chip.addView(title, new LinearLayout.LayoutParams(WRAP, WRAP));

            Glyph close = new Glyph(this, Glyph.CLOSE, Fx.MUTED, 14);
            close.setOnClickListener(v -> closeTab(t));
            chip.addView(close, new LinearLayout.LayoutParams(dp(30), dp(30)));

            chip.setOnClickListener(v -> {
                int idx = tabs.indexOf(t);
                if (idx >= 0) select(idx);
            });

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(WRAP, MATCH);
            cp.rightMargin = dp(4);
            tabStrip.addView(chip, cp);
        }
    }

    private void show(Tab t) {
        webContainer.removeAllViews();
        if (t.home) {
            webContainer.setVisibility(View.GONE);
            homeView.setVisibility(View.VISIBLE);
            refreshHome();
            address.setText("");
            progress.setVisibility(View.GONE);
        } else {
            homeView.setVisibility(View.GONE);
            webContainer.setVisibility(View.VISIBLE);
            webContainer.addView(t.web, new FrameLayout.LayoutParams(MATCH, MATCH));
            address.setText(t.url);
        }
        address.clearFocus();
        updateStar();
        updateNav();
    }

    private void updateNav() {
        if (cur < 0) return;
        Tab t = curTab();
        btnBack.setAlpha(!t.home && t.web.canGoBack() ? 1f : 0.35f);
        btnFwd.setAlpha(!t.home && t.web.canGoForward() ? 1f : 0.35f);
    }

    // ------------------------------------------------------------------ navegação

    private String toUrl(String input) {
        if (input == null) return null;
        String in = input.trim();
        if (in.isEmpty()) return null;
        if (in.startsWith("http://") || in.startsWith("https://")
                || in.startsWith("file://") || in.startsWith("about:")) {
            return in;
        }
        if (!in.contains(" ") && (in.contains(".") || in.startsWith("localhost"))) {
            return "https://" + in;
        }
        return GOOGLE_SEARCH + Uri.encode(in);
    }

    private void go(String input) {
        String u = toUrl(input);
        if (u == null) return;
        // texto que virou pesquisa no Google entra no histórico de buscas (até 25)
        if (u.startsWith(GOOGLE_SEARCH) && !input.trim().startsWith("http")) {
            SearchStore.add(this, input.trim());
        }
        hideKeyboard();
        Tab t = curTab();
        t.home = false;
        t.url = u;
        t.title = u;
        homeSearch.setText("");
        show(t);
        renderTabs();
        t.web.loadUrl(u);
    }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View f = getCurrentFocus();
        if (im != null && f != null) im.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    /** Barra de endereço expandida em tela cheia (campo, página atual, copiado e histórico). */
    private void openAddressBar() {
        Tab t = curTab();
        final String u = t.home ? "" : t.url;
        String ti = t.home ? "" : t.title;
        AddressBar.show(this, u, ti, new AddressBar.Host() {
            @Override
            public void go(String input) {
                MainActivity.this.go(input);
            }

            @Override
            public void share() {
                sharePage();
            }
        });
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(final Tab t) {
        WebView w = new WebView(Fx.dark ? new ContextThemeWrapper(this, R.style.ArbeonWebDark) : this);
        WebSettings s = w.getSettings();
        if (defaultUA == null) defaultUA = s.getUserAgentString();
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        applySettings(w);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, WebPrefs.thirdParty(this));

        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (AdDns.blockNavigation(MainActivity.this, view, req)) return true;
                if (WebPrefs.httpsOnly(MainActivity.this) && u.startsWith("http://")) {
                    view.loadUrl("https://" + u.substring(7));
                    return true;
                }
                if (u.startsWith("http://") || u.startsWith("https://")
                        || u.startsWith("about:") || u.startsWith("file:")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                } catch (Exception e) {
                    toast("Não foi possível abrir este link");
                }
                return true;
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                try {
                    if (!req.isForMainFrame() && AdBlock.enabled(MainActivity.this)
                            && AdBlock.isBlocked(req.getUrl().getHost())) {
                        AdBlock.blocked.incrementAndGet();
                        return new android.webkit.WebResourceResponse("text/plain", "utf-8",
                                new java.io.ByteArrayInputStream(new byte[0]));
                    }
                    android.webkit.WebResourceResponse dr = AdDns.intercept(MainActivity.this, req);
                    if (dr != null) return dr;
                } catch (Exception ignored) {
                }
                return super.shouldInterceptRequest(view, req);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                t.url = url;
                t.home = false;
                if (isCur(t)) {
                    if (!address.hasFocus()) address.setText(url);
                    updateStar();
                    updateNav();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                t.url = url;
                HistoryStore.add(MainActivity.this, view.getTitle(), url);
                if (isCur(t)) {
                    rememberUrl(url);
                    if (!address.hasFocus()) address.setText(url);
                    updateStar();
                    updateNav();
                }
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback cb) {
                SitePerms.geo(MainActivity.this, origin, cb);
            }

            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest req) {
                SitePerms.media(MainActivity.this, req);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                new ArbeonDialog(MainActivity.this).setTitle("Esta p\u00e1gina diz").setMessage(message)
                        .setPositiveButton("OK", (dd, ww) -> result.confirm())
                        .setOnCancel(() -> result.confirm()).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                new ArbeonDialog(MainActivity.this).setTitle("Esta p\u00e1gina diz").setMessage(message)
                        .setPositiveButton("OK", (dd, ww) -> result.confirm())
                        .setNegativeButton("Cancelar", (dd, ww) -> result.cancel())
                        .setOnCancel(() -> result.cancel()).show();
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int p) {
                if (!isCur(t)) return;
                progress.setProgress(p);
                progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                t.title = title;
                if (t.chipTitle != null) t.chipTitle.setText(titleOf(t));
            }
        });

        w.setDownloadListener((url, ua, cd, mime, len) -> DownloadsUi.onDownloadRequested(MainActivity.this, url, ua, cd, mime, len, w.getUrl()));
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        attachPull(w, t);
        return w;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void applySettings(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(prefs.getBoolean("js", true));
        WebPrefs.apply(this, w);
        // sites seguem o tema escuro do app (sites com tema escuro próprio usam o deles)
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(Fx.dark);
        } else if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(Fx.dark ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }
        s.setUserAgentString(prefs.getBoolean("desktop", false) ? DESKTOP_UA : defaultUA);
    }

    // ------------------------------------------------------------------ favoritos

    private JSONArray loadBookmarks() {
        try {
            return new JSONArray(prefs.getString("bookmarks", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveBookmarks(JSONArray a) {
        prefs.edit().putString("bookmarks", a.toString()).apply();
    }

    private int bookmarkIndex(String url) {
        JSONArray a = loadBookmarks();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optString("url").equals(url)) return i;
        }
        return -1;
    }

    private void updateStar() {
        if (cur < 0) return;
        Tab t = curTab();
        boolean on = !t.home && bookmarkIndex(t.url) >= 0;
        btnStar.setOn(on);
    }

    private void toggleBookmark() {
        Tab t = curTab();
        if (t.home || t.url.isEmpty()) {
            toast("Abra uma página para favoritar");
            return;
        }
        JSONArray a = loadBookmarks();
        int i = bookmarkIndex(t.url);
        if (i >= 0) {
            a.remove(i);
            toast("Favorito removido");
        } else {
            try {
                JSONObject o = new JSONObject();
                o.put("title", t.title);
                o.put("url", t.url);
                a.put(o);
                toast("Favorito adicionado");
            } catch (Exception ignored) {
            }
        }
        saveBookmarks(a);
        updateStar();
    }

    private void showBookmarks() {
        BookmarksUi.show(this, prefs, new BookmarksUi.Host() {
            @Override
            public void open(String url) {
                go(url);
            }

            @Override
            public void openNewTab(String url) {
                newTab(url);
            }

            @Override
            public void changed() {
                updateStar();
                if (curTab().home) refreshHome();
            }
        });
    }

    // ------------------------------------------------------------------ menu e configurações

    private void showMenu(View anchor) {
        showMenuV2();
    }

    private void showMenuV2() {
        final Tab t = curTab();
        boolean back = !t.home && t.web.canGoBack();
        boolean fwd = !t.home && t.web.canGoForward();
        boolean starred = !t.home && bookmarkIndex(t.url) >= 0;
        AppMenu.show(this, back, fwd, starred, prefs.getBoolean("desktop", false),
                AdBlock.enabled(this), new Runnable[]{
                () -> { if (!t.home && t.web.canGoBack()) t.web.goBack(); },
                () -> { if (!t.home && t.web.canGoForward()) t.web.goForward(); },
                this::toggleBookmark,
                () -> DownloadsUi.show(this),
                () -> { if (!t.home) t.web.reload(); },
                () -> newTab(null),
                this::confirmClear,
                this::showSettings,
                () -> DownloadsUi.show(this),
                this::showBookmarks,
                this::sharePage,
                this::showHistory,
                this::toggleDesktop,
                this::toggleAdblock,
                this::showDns});
    }

    /** Abre o site de origem de um download para renovar o link (usado por Atualizar link > Renovar no site). */
    void openForRenew(String url) {
        newTab(url);
    }

    private void showHistory() {
        HistoryUi.show(this, url -> go(url));
    }

    /** Liga/desliga o bloqueador de anúncios e recarrega a página. */
    private void toggleAdblock() {
        boolean on = !AdBlock.enabled(this);
        AdBlock.setEnabled(this, on);
        Tab c = curTab();
        if (!c.home) c.web.reload();
        toast(on ? "Bloqueador de anúncios ativado" : "Bloqueador de anúncios desativado");
    }

    private void showDns() {
        DnsUi.show(this, () -> {
            Tab c = curTab();
            if (!c.home) c.web.reload();
        });
    }

    /** Liga/desliga o modo computador (site para PC) e recarrega a página. */
    private void toggleDesktop() {
        boolean on = !prefs.getBoolean("desktop", false);
        prefs.edit().putBoolean("desktop", on).apply();
        for (Tab t : tabs) applySettings(t.web);
        Tab c = curTab();
        if (!c.home) c.web.reload();
        toast(on ? "Modo computador ativado" : "Modo computador desativado");
    }

    private void confirmClear() {
        new ArbeonDialog(this)
                .setTitle("Excluir dados de navegação?")
                .setMessage("Cookies, cache, histórico e dados de sites serão apagados.")
                .setPositiveButton("Excluir", (d, w) -> clearData())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void sharePage() {
        Tab t = curTab();
        if (t.home || t.url.isEmpty()) {
            toast("Abra uma página para compartilhar");
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, t.url);
        startActivity(Intent.createChooser(i, "Compartilhar"));
    }

    private void showSettings() {
        SettingsDialog.show(this, prefs.getBoolean("js", true), prefs.getBoolean("desktop", false),
                (js, desktop) -> {
                    prefs.edit().putBoolean("js", js).putBoolean("desktop", desktop).apply();
                    for (Tab t : tabs) applySettings(t.web);
                    Tab c = curTab();
                    if (!c.home) c.web.reload();
                    toast("Configurações salvas");
                }, this::clearData);
    }

    private void clearData() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        HistoryStore.clear(this);
        SearchStore.clear(this);
        prefs.edit().remove("last_url").apply();
        for (Tab t : tabs) {
            t.web.clearCache(true);
            t.web.clearHistory();
            t.web.clearFormData();
        }
        updateNav();
        toast("Dados de navegação apagados");
    }

    private void toast(String s) {
        ArbeonUi.toast(this, s);
    }

    // ---- recarregar: giro e puxar para atualizar

    private void spinReload() {
        if (btnReload == null) return;
        btnReload.animate().cancel();
        btnReload.setRotation(0f);
        btnReload.animate().rotation(360f).setDuration(650)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> btnReload.setRotation(0f)).start();
    }

    private void movePull(float dist) {
        float ty = Math.min(dist * 0.5f, dp(80)) - dp(48);
        pullIndicator.animate().cancel();
        pullIndicator.setAlpha(Math.min(1f, dist / dp(40)));
        pullIndicator.setTranslationY(ty);
        pullIndicator.setRotation(dist * 1.5f);
        float sc = dist >= dp(130) ? 1.15f : 1f;
        pullIndicator.setScaleX(sc);
        pullIndicator.setScaleY(sc);
    }

    private void hidePull() {
        pullIndicator.animate().alpha(0f).translationY(-dp(48)).scaleX(1f).scaleY(1f)
                .setDuration(200)
                .withEndAction(() -> pullIndicator.setRotation(0f)).start();
    }

    private void endPull(boolean trigger) {
        if (pullIndicator == null) return;
        if (trigger) {
            Tab t = curTab();
            if (!t.home) {
                spinReload();
                t.web.reload();
            }
            pullIndicator.animate().translationY(dp(24)).alpha(1f).rotationBy(360f)
                    .setDuration(600).withEndAction(this::hidePull).start();
        } else {
            hidePull();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachPull(final WebView w, final Tab t) {
        w.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pullStartY = e.getY();
                    pullArmed = isCur(t) && w.getScrollY() == 0;
                    pullDist = 0f;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!pullArmed) break;
                    if (e.getPointerCount() > 1 || w.getScrollY() > 0) {
                        pullArmed = false;
                        pullDist = 0f;
                        endPull(false);
                        break;
                    }
                    pullDist = Math.max(0f, e.getY() - pullStartY - dp(12));
                    movePull(pullDist);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (pullArmed && pullDist > 0f) {
                        endPull(e.getActionMasked() == MotionEvent.ACTION_UP && pullDist >= dp(130));
                    }
                    pullArmed = false;
                    pullDist = 0f;
                    break;
                default:
                    break;
            }
            return false;
        });
    }
}
