package com.amitek.attendance;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Phone restarted, or the app was updated: carry on tracking, and say so after an update. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()) && Store.updating(c)) {
            Store.updating(c, false);
            try {
                NotificationManager nm = c.getSystemService(NotificationManager.class);
                nm.cancel("update", 100);
                nm.cancel("update-confirm", 100);
                nm.notify("updated", 100, new Notification.Builder(c, Push.CH_UPDATES)
                        .setSmallIcon(R.drawable.ic_stat).setColor(0xFF3D6B50)
                        .setContentTitle("App updated ✓")
                        .setContentText("You now have version " + BuildConfigLite.version(c) + ". Tap to open.")
                        .setContentIntent(Push.open(c, 12, "today"))
                        .setAutoCancel(true).setTimeoutAfter(10 * 60 * 1000L).build());
            } catch (Exception ignored) {}
        }
        if (Store.session(c) == null) return;
        try {
            c.startForegroundService(new Intent(c, TrackerService.class).setAction(TrackerService.ACT_FORCE));
        } catch (Exception ignored) {
            // not allowed without "Allow all the time"; tracking resumes when the app is opened
        }
    }
}
