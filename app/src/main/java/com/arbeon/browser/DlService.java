package com.arbeon.browser;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Mantém os downloads vivos com o app em segundo plano. */
public class DlService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = DownloadEngine.get(this).notification("Baixando…");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(DownloadEngine.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(DownloadEngine.NOTIF_ID, n);
        }
        return START_NOT_STICKY;
    }
}
