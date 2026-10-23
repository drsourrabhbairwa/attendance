package com.amitek.attendance;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/**
 * The buttons under "You seem to have left at …": "Yes, punch out" and "Still working".
 * They are answered here, from the notification, without opening the app.
 */
public class NoticeActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        final boolean yes = Push.ACT_YES.equals(i.getAction());
        if (!yes && !Push.ACT_NO.equals(i.getAction())) return;
        final String noticeId = nz(i.getStringExtra("noticeId")), tag = nz(i.getStringExtra("tag")), ref = nz(i.getStringExtra("ref"));
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { answer(c.getApplicationContext(), yes, noticeId, tag, ref); }
            catch (Exception ignored) { openApp(c, tag); }
            finally { pr.finish(); }
        }).start();
    }

    private void answer(Context c, boolean yes, String noticeId, String tag, String ref) throws Exception {
        JSONObject a = Store.auth(c);
        if (a == null) { openApp(c, tag); return; }   // signed out: the app asks for the sign-in
        String[] p = noticeId.split("\\|");
        String date = p.length > 2 && p[2].matches("\\d{4}-\\d\\d-\\d\\d") ? p[2] : Store.today();
        try {
            Net.post(a.optString("scriptUrl"), new JSONObject().put("action", yes ? "confirmLeft" : "stillWorking")
                    .put("secret", a.optString("secret")).put("token", a.optString("token"))
                    .put("empId", a.optString("empId")).put("date", date));
        } catch (Net.Rejected r) {
            openApp(c, tag);   // sign-in ended or the question was already answered: the app shows the true state
            return;
        }
        if (yes && Store.session(c) != null) TrackerService.stop(c);   // the day is over: stop the site checks too
        done(c, tag, yes ? "✓ Punched out" + (ref.isEmpty() ? "" : " at " + ref) : "Noted: you are still working",
                yes ? "Thank you. Your day is closed." : "We will not ask again about this.");
    }

    /** Replaces the question with a short answer that goes away by itself. */
    private void done(Context c, String tag, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.cancel(tag, 100);
        Notification n = new Notification.Builder(c, Push.CH_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat).setColor(0xFF3D6B50)
                .setContentTitle(title).setContentText(text)
                .setContentIntent(Push.open(c, 7, "today"))
                .setAutoCancel(true).setTimeoutAfter(15000).build();
        nm.notify(tag + ":done", 100, n);
    }

    /** The answer could not be sent from here: the question stays, and a line beside it opens the app on it (Android does not let a button open a screen directly). */
    private void openApp(Context c, String tag) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = new Notification.Builder(c, Push.CH_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat).setColor(0xFF3D6B50)
                .setContentTitle("Could not send your answer")
                .setContentText("Tap to open the app and answer there.")
                .setContentIntent(Push.open(c, 8, "out"))
                .setAutoCancel(true).setTimeoutAfter(60000).build();
        nm.notify(tag + ":retry", 100, n);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
