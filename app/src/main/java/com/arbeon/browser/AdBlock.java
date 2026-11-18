package com.arbeon.browser;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Bloqueio de anúncios e rastreadores por domínio. Ligado por padrão; dá para desligar no menu. */
final class AdBlock {
    static final AtomicInteger blocked = new AtomicInteger();

    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            // Google / anúncios
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
            "googletagmanager.com", "google-analytics.com", "adservice.google.com", "2mdn.net",
            "admob.com", "app-measurement.com",
            // redes de anúncios
            "adnxs.com", "adsrvr.org", "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
            "rubiconproject.com", "pubmatic.com", "openx.net", "amazon-adsystem.com", "moatads.com",
            "adform.net", "smartadserver.com", "media.net", "yieldmo.com", "sharethrough.com",
            "indexww.com", "casalemedia.com", "lijit.com", "sovrn.com", "33across.com", "bidswitch.net",
            "contextweb.com", "teads.tv", "zedo.com", "adroll.com", "serving-sys.com", "flashtalking.com",
            "smaato.net", "adcolony.com", "applovin.com", "mopub.com", "inmobi.com", "chartboost.com",
            "vungle.com", "ironsrc.com", "supersonicads.com", "unityads.unity3d.com", "adsmoloco.com",
            // anúncios pop-up / redirecionamento
            "popads.net", "popcash.net", "propellerads.com", "exoclick.com", "juicyads.com",
            "trafficjunky.com", "adsterra.com", "clickadu.com", "hilltopads.net", "mgid.com",
            "revcontent.com", "adcash.com", "richpush.co", "pushance.com", "onclickads.net",
            "ad-maven.com", "admaven.com", "monetag.com", "a-ads.com", "bidvertiser.com",
            // rastreadores e métricas
            "scorecardresearch.com", "quantserve.com", "hotjar.com", "mixpanel.com", "segment.io",
            "appsflyer.com", "adjust.com", "fullstory.com", "crazyegg.com", "mouseflow.com", "clarity.ms",
            "bluekai.com", "krxd.net", "demdex.net", "everesttech.net", "agkn.com", "rlcdn.com",
            "crwdcntrl.net", "tapad.com", "adsymptotic.com", "ay.delivery", "bat.bing.com",
            "mc.yandex.ru", "an.yandex.ru", "ads.yahoo.com", "ads.twitter.com", "static.ads-twitter.com",
            "ads.linkedin.com", "analytics.tiktok.com", "ads.tiktok.com", "ads.pinterest.com"
    ));

    private AdBlock() {
    }

    static boolean enabled(Context c) {
        SharedPreferences p = c.getSharedPreferences("arbeon", Context.MODE_PRIVATE);
        return p.getBoolean("adblock", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("arbeon", Context.MODE_PRIVATE).edit().putBoolean("adblock", on).apply();
    }

    /** true se o domínio (ou um domínio-pai dele) está na lista. */
    static boolean isBlocked(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        while (true) {
            if (HOSTS.contains(h)) return true;
            int dot = h.indexOf('.');
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false;
            h = h.substring(dot + 1);
        }
    }
}
