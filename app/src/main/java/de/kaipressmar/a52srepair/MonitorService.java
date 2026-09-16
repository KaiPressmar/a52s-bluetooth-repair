package de.kaipressmar.a52srepair;

import android.app.*;
import android.content.Intent;
import android.os.*;

public class MonitorService extends Service {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { public void run() { Diag.log(MonitorService.this, "MONITOR\n" + Diag.snapshot(MonitorService.this)); h.postDelayed(this, 15000); } };
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("monitor", "Bluetooth monitor", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "monitor").setContentTitle("A52s Bluetooth Monitor").setContentText("Bluetooth/Audio-Zustand wird protokolliert").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build();
        startForeground(1, n);
        Diag.log(this, "MONITOR START"); h.post(tick);
    }
    @Override public void onDestroy() { h.removeCallbacks(tick); Diag.log(this, "MONITOR STOP"); super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent i) { return null; }
}
