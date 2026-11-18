package com.arbeon.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;

/** Aplica nas páginas as opções de Configurações (sites, privacidade, segurança e posição da barra). */
final class WebPrefs {
    private WebPrefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("arbeon", Context.MODE_PRIVATE);
    }

    /** Chamado pelo MainActivity.applySettings para cada WebView. */
    static void apply(Context c, WebView w) {
        SharedPreferences sp = p(c);
        WebSettings s = w.getSettings();
        s.setLoadsImagesAutomatically(sp.getBoolean("images", true));
        s.setJavaScriptCanOpenWindowsAutomatically(sp.getBoolean("popups", false));
        s.setMediaPlaybackRequiresUserGesture(!sp.getBoolean("autoplay", false));
        if (Build.VERSION.SDK_INT >= 26) {
            s.setSafeBrowsingEnabled(sp.getBoolean("safe_browsing", true));
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, sp.getBoolean("tp_cookies", true));
    }

    static boolean thirdParty(Context c) {
        return p(c).getBoolean("tp_cookies", true);
    }

    static boolean httpsOnly(Context c) {
        return p(c).getBoolean("https_only", false);
    }

    /** Barra de endereço embaixo (Aparência > Barra de endereço > Parte de baixo). */
    static void applyBars(Activity a) {
        try {
            if (p(a).getInt("bar_pos", 0) != 1) return;
            ViewGroup content = a.findViewById(android.R.id.content);
            View root = content.getChildAt(0);
            if (!(root instanceof LinearLayout)) return;
            LinearLayout ll = (LinearLayout) root;
            if (ll.getChildCount() < 4) return;
            View bar = ll.getChildAt(1);
            ll.removeViewAt(1);
            ll.addView(bar);
        } catch (Exception ignored) {
        }
    }
}
