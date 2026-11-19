package com.arbeon.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recebe os toques nos botões "Pausar", "Continuar" e "Cancelar" da notificação de download. */
public class DlActionReceiver extends BroadcastReceiver {
    static final String CANCEL = "com.arbeon.browser.DL_CANCEL";
    static final String PAUSE = "com.arbeon.browser.DL_PAUSE";
    static final String RESUME = "com.arbeon.browser.DL_RESUME";

    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || i.getAction() == null) return;
        DownloadEngine e = DownloadEngine.get(c);
        DlItem it = e.find(i.getStringExtra("id"));
        if (it == null) return;
        switch (i.getAction()) {
            case CANCEL:
                e.remove(it);
                break;
            case PAUSE:
                e.pause(it);
                break;
            case RESUME:
                e.resume(it);
                break;
            default:
                break;
        }
    }
}
