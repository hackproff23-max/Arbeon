package com.arbeon.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
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
import android.widget.PopupMenu;
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
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

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
    private TextView btnBack, btnFwd, btnStar;
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
        buildUi();
        newTab(getIntent() != null ? getIntent().getDataString() : null);
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

    // ------------------------------------------------------------------ interface

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private TextView iconButton(String glyph, int sizeSp, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(glyph);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        b.setTextColor(GRAY);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setOnClickListener(l);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        if (tv.resourceId != 0) b.setBackgroundResource(tv.resourceId);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setFitsSystemWindows(true);

        // ---- barra de guias (em cima)
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(0xFFDEE1E6);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);

        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(6), dp(6), 0, 0);
        tabScroll.addView(tabStrip, new FrameLayout.LayoutParams(WRAP, MATCH));
        tabBar.addView(tabScroll, new LinearLayout.LayoutParams(0, MATCH, 1f));

        TextView plus = iconButton("+", 24, v -> newTab(null));
        tabBar.addView(plus, new LinearLayout.LayoutParams(dp(44), MATCH));
        root.addView(tabBar, new LinearLayout.LayoutParams(MATCH, dp(42)));

        // ---- barra de endereço
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);

        btnBack = iconButton("‹", 30, v -> {
            Tab t = curTab();
            if (!t.home && t.web.canGoBack()) t.web.goBack();
        });
        btnFwd = iconButton("›", 30, v -> {
            Tab t = curTab();
            if (!t.home && t.web.canGoForward()) t.web.goForward();
        });
        TextView btnReload = iconButton("⟳", 22, v -> {
            Tab t = curTab();
            if (!t.home) t.web.reload();
        });

        address = new EditText(this);
        address.setBackground(rounded(0xFFF1F3F4, 20, 0));
        address.setPadding(dp(14), 0, dp(14), 0);
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

        btnStar = iconButton("☆", 22, v -> toggleBookmark());
        TextView btnMenu = iconButton("⋮", 22, this::showMenu);

        bar.addView(btnBack, new LinearLayout.LayoutParams(dp(36), dp(44)));
        bar.addView(btnFwd, new LinearLayout.LayoutParams(dp(36), dp(44)));
        bar.addView(btnReload, new LinearLayout.LayoutParams(dp(40), dp(44)));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(38), 1f);
        ap.setMargins(dp(2), 0, dp(2), 0);
        bar.addView(address, ap);
        bar.addView(btnStar, new LinearLayout.LayoutParams(dp(40), dp(44)));
        bar.addView(btnMenu, new LinearLayout.LayoutParams(dp(36), dp(44)));
        root.addView(bar, new LinearLayout.LayoutParams(MATCH, dp(48)));

        // ---- progresso
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(BLUE));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(MATCH, dp(3)));

        // ---- conteúdo: páginas + tela inicial
        FrameLayout content = new FrameLayout(this);
        webContainer = new FrameLayout(this);
        content.addView(webContainer, new FrameLayout.LayoutParams(MATCH, MATCH));
        buildHome();
        content.addView(homeView, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        setContentView(root);
    }

    private void buildHome() {
        homeView = new ScrollView(this);
        homeView.setFillViewport(true);
        homeView.setBackgroundColor(Color.WHITE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(20), dp(72), dp(20), dp(24));

        TextView logo = new TextView(this);
        logo.setText("Arbeon");
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setTextColor(BLUE);
        col.addView(logo, new LinearLayout.LayoutParams(WRAP, WRAP));

        TextView sub = new TextView(this);
        sub.setText("Pesquise com o Google");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setTextColor(GRAY);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(WRAP, WRAP);
        sp.bottomMargin = dp(28);
        col.addView(sub, sp);

        homeSearch = new EditText(this);
        homeSearch.setBackground(rounded(Color.WHITE, 26, 0xFFDADCE0));
        homeSearch.setPadding(dp(20), 0, dp(20), 0);
        homeSearch.setHint("Pesquisar no Google ou digitar endereço");
        homeSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        homeSearch.setSingleLine(true);
        homeSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        homeSearch.setOnEditorActionListener((v, actionId, event) -> {
            go(homeSearch.getText().toString());
            return true;
        });
        col.addView(homeSearch, new LinearLayout.LayoutParams(MATCH, dp(52)));

        homeBmLabel = new TextView(this);
        homeBmLabel.setText("Favoritos");
        homeBmLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        homeBmLabel.setTextColor(GRAY);
        homeBmLabel.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(36);
        col.addView(homeBmLabel, lp);

        homeGrid = new GridLayout(this);
        homeGrid.setColumnCount(4);
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

            TextView circle = new TextView(this);
            circle.setText(title.substring(0, 1).toUpperCase());
            circle.setGravity(Gravity.CENTER);
            circle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            circle.setTextColor(BLUE);
            GradientDrawable oval = new GradientDrawable();
            oval.setShape(GradientDrawable.OVAL);
            oval.setColor(0xFFE8F0FE);
            circle.setBackground(oval);
            item.addView(circle, new LinearLayout.LayoutParams(dp(52), dp(52)));

            TextView label = new TextView(this);
            label.setText(title);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            label.setTextColor(0xFF3C4043);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(MATCH, WRAP);
            llp.topMargin = dp(6);
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
            chip.setPadding(dp(12), 0, dp(2), 0);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(sel ? Color.WHITE : 0xFFC8CCD2);
            float r = dp(10);
            bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            chip.setBackground(bg);

            TextView title = new TextView(this);
            title.setText(titleOf(t));
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setMaxWidth(dp(120));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            title.setTextColor(sel ? 0xFF202124 : GRAY);
            t.chipTitle = title;
            chip.addView(title, new LinearLayout.LayoutParams(WRAP, WRAP));

            TextView close = iconButton("✕", 12, v -> closeTab(t));
            chip.addView(close, new LinearLayout.LayoutParams(dp(30), dp(30)));

            chip.setOnClickListener(v -> {
                int idx = tabs.indexOf(t);
                if (idx >= 0) select(idx);
            });

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(WRAP, MATCH);
            cp.rightMargin = dp(3);
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
        return "https://www.google.com/search?q=" + Uri.encode(in);
    }

    private void go(String input) {
        String u = toUrl(input);
        if (u == null) return;
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

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(final Tab t) {
        WebView w = new WebView(this);
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
        cm.setAcceptThirdPartyCookies(w, true);

        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
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
                if (isCur(t)) {
                    if (!address.hasFocus()) address.setText(url);
                    updateStar();
                    updateNav();
                }
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
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

        w.setDownloadListener((url, ua, cd, mime, len) -> DownloadsUi.onDownloadRequested(MainActivity.this, url, ua, cd, mime, len));
        return w;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void applySettings(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(prefs.getBoolean("js", true));
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
        btnStar.setText(on ? "★" : "☆");
        btnStar.setTextColor(on ? 0xFFF9AB00 : GRAY);
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
        JSONArray a = loadBookmarks();
        if (a.length() == 0) {
            toast("Nenhum favorito ainda. Toque na ☆ para adicionar.");
            return;
        }
        final String[] names = new String[a.length()];
        final String[] urls = new String[a.length()];
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            String title = o == null ? "" : o.optString("title");
            urls[i] = o == null ? "" : o.optString("url");
            names[i] = (TextUtils.isEmpty(title) ? urls[i] : title) + "\n" + urls[i];
        }
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Favoritos (segure para remover)")
                .setItems(names, (dd, which) -> go(urls[which]))
                .setNegativeButton("Fechar", null)
                .create();
        d.show();
        d.getListView().setOnItemLongClickListener((parent, view, pos, id) -> {
            JSONArray b = loadBookmarks();
            b.remove(pos);
            saveBookmarks(b);
            d.dismiss();
            updateStar();
            if (curTab().home) refreshHome();
            toast("Favorito removido");
            return true;
        });
    }

    // ------------------------------------------------------------------ menu e configurações

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Nova guia");
        m.getMenu().add(0, 2, 1, "Favoritos");
        m.getMenu().add(0, 3, 2, "Configurações");
        m.getMenu().add(0, 4, 3, "Compartilhar página");
        m.getMenu().add(0, 5, 4, "Downloads");
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    newTab(null);
                    break;
                case 2:
                    showBookmarks();
                    break;
                case 3:
                    showSettings();
                    break;
                case 5:
                    DownloadsUi.show(MainActivity.this);
                    break;
                case 4:
                    sharePage();
                    break;
                default:
                    break;
            }
            return true;
        });
        m.show();
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
        final boolean[] checked = {
                prefs.getBoolean("js", true),
                prefs.getBoolean("desktop", false)
        };
        new AlertDialog.Builder(this)
                .setTitle("Configurações")
                .setMultiChoiceItems(new String[]{"Ativar JavaScript", "Modo desktop"},
                        checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Salvar", (d, w) -> {
                    prefs.edit()
                            .putBoolean("js", checked[0])
                            .putBoolean("desktop", checked[1])
                            .apply();
                    for (Tab t : tabs) applySettings(t.web);
                    Tab c = curTab();
                    if (!c.home) c.web.reload();
                    toast("Configurações salvas");
                })
                .setNeutralButton("Limpar dados", (d, w) -> clearData())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void clearData() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        for (Tab t : tabs) {
            t.web.clearCache(true);
            t.web.clearHistory();
            t.web.clearFormData();
        }
        updateNav();
        toast("Dados de navegação apagados");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
