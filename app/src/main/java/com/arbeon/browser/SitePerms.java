package com.arbeon.browser;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;

import java.util.ArrayList;

/** Permissões dos sites (Local, Câmera, Microfone): perguntar primeiro ou bloquear. */
final class SitePerms {
    private SitePerms() {
    }

    private interface Answer {
        void on(boolean ok);
    }

    private static SharedPreferences p(Activity a) {
        return a.getSharedPreferences("arbeon", Activity.MODE_PRIVATE);
    }

    private static String hostOf(String origin) {
        try {
            String h = android.net.Uri.parse(origin).getHost();
            return h != null ? h : origin;
        } catch (Exception e) {
            return origin;
        }
    }

    private static void ask(final Activity a, final String msg, final Answer ans) {
        a.runOnUiThread(() -> new ArbeonDialog(a)
                .setTitle("Permissão")
                .setMessage(msg)
                .setPositiveButton("Permitir", (d, w) -> ans.on(true))
                .setNegativeButton("Bloquear", (d, w) -> ans.on(false))
                .setOnCancel(() -> ans.on(false))
                .show());
    }

    private static void needSystem(Activity a, String... perms) {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> missing = new ArrayList<>();
        for (String s : perms) {
            if (a.checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED) missing.add(s);
        }
        if (!missing.isEmpty()) a.requestPermissions(missing.toArray(new String[0]), 4101);
    }

    static void geo(final Activity a, final String origin, final GeolocationPermissions.Callback cb) {
        if (p(a).getInt("perm_geo", 0) == 1) {
            cb.invoke(origin, false, false);
            return;
        }
        ask(a, hostOf(origin) + " quer saber a sua localização.", ok -> {
            if (ok) needSystem(a, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
            cb.invoke(origin, ok, false);
        });
    }

    static void media(final Activity a, final PermissionRequest req) {
        final ArrayList<String> grant = new ArrayList<>();
        boolean cam = false, mic = false;
        for (String r : req.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                cam = true;
                grant.add(r);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                mic = true;
                grant.add(r);
            }
        }
        final boolean wantCam = cam, wantMic = mic;
        SharedPreferences sp = p(a);
        if ((!cam && !mic) || (cam && sp.getInt("perm_cam", 0) == 1) || (mic && sp.getInt("perm_mic", 0) == 1)) {
            a.runOnUiThread(req::deny);
            return;
        }
        String what = cam && mic ? "a câmera e o microfone" : (cam ? "a câmera" : "o microfone");
        ask(a, hostOf(req.getOrigin().toString()) + " quer usar " + what + ".", ok -> {
            if (!ok) {
                req.deny();
                return;
            }
            if (wantCam) needSystem(a, Manifest.permission.CAMERA);
            if (wantMic) needSystem(a, Manifest.permission.RECORD_AUDIO);
            req.grant(grant.toArray(new String[0]));
        });
    }
}
