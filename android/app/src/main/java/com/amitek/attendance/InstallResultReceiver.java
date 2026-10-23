package com.amitek.attendance;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Android reports how the update went: asks for the person's confirmation, or records a failure. */
public class InstallResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) return;
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (MainActivity.isVisible()) {
                try { c.startActivity(confirm); return; } catch (Exception ignored) { /* fall through to the notification */ }
            }
            // the person left the app while it downloaded: a tap on this notification brings the confirmation back
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm == null) return;
            Notification n = new Notification.Builder(c, Push.CH_UPDATES)
                    .setSmallIcon(R.drawable.ic_stat).setColor(0xFF3D6B50)
                    .setContentTitle("Update downloaded")
                    .setContentText("Tap to finish installing the new version.")
                    .setContentIntent(PendingIntent.getActivity(c, 11, confirm, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                    .setAutoCancel(true).build();
            nm.notify("update-confirm", 100, n);
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) return;   // the app is replaced and restarts; BootReceiver says so
        String why = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Updater.fail(c, status == PackageInstaller.STATUS_FAILURE_ABORTED
                ? "The update was cancelled. Tap Update to try again."
                : "Android could not install the update" + (why == null || why.isEmpty() ? "." : ": " + why));
    }
}
