package com.arbeon.browser;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

/** Janela transparente que só pede a permissão de notificações (Android 13+) e fecha. */
public class NotifPermActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77);
        } else {
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        finish();
    }
}
