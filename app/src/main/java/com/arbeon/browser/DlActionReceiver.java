package com.arbeon.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recebe o toque no botão "Cancelar" da notificação de download. */
public class DlActionReceiver extends BroadcastReceiver {
    static final String CANCEL = "com.arbeon.browser.DL_CANCEL";

    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || !CANCEL.equals(i.getAction())) return;
        DownloadEngine e = DownloadEngine.get(c);
        DlItem it = e.find(i.getStringExtra("id"));
        if (it != null) e.remove(it);
    }
}
