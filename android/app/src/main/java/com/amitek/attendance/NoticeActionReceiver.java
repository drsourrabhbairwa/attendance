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
    /** The system gives a button's work only a few seconds; the whole call, redirect included, stays inside this. */
    private static final long BUDGET_MS = 8000;

    @Override public void onReceive(Context c, Intent i) {
        final boolean yes = Push.ACT_YES.equals(i.getAction());
        if (!yes && !Push.ACT_NO.equals(i.getAction())) return;
        final String noticeId = nz(i.getStringExtra("noticeId")), tag = nz(i.getStringExtra("tag")), ref = nz(i.getStringExtra("ref")), to = nz(i.getStringExtra("empId"));
        final Context app = c.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { answer(app, yes, noticeId, tag, ref, to); }
            catch (Throwable t) { try { retry(app, tag, ""); } catch (Throwable ignored) { /* nothing more can be done from here */ } }
            finally { pr.finish(); }
        }).start();
    }

    private void answer(Context c, boolean yes, String noticeId, String tag, String ref, String to) throws Exception {
        JSONObject a = Store.auth(c);
        // The question belongs to the person it was sent to. Signed out, or someone else signed in since: it is not theirs to answer, so it just goes.
        if (a == null || !PushLogic.forPerson(to, a.optString("empId"))) { dismiss(c, tag); return; }
        String[] p = noticeId.split("\\|");
        String date = p.length > 2 && p[2].matches("\\d{4}-\\d\\d-\\d\\d") ? p[2] : Store.today();
        JSONObject reply;
        try {
            reply = Net.post(a.optString("scriptUrl"), new JSONObject().put("action", yes ? "confirmLeft" : "stillWorking")
                    .put("secret", a.optString("secret")).put("token", a.optString("token"))
                    .put("empId", a.optString("empId")).put("date", date), BUDGET_MS);
        } catch (Net.Rejected r) {
            // sign-in ended, or the office refused (for example a meeting is still open): the question stays and a line beside it says why
            retry(c, tag, Net.signInNeeded(r) ? "Please sign in again." : nz(r.getMessage()));
            return;
        }
        if (yes && Store.session(c) != null) TrackerService.stop(c);   // the day is over: stop the site checks too
        JSONObject data = reply.optJSONObject("data");
        String[] text = PushLogic.answer(yes, data == null ? "" : data.optString("time"), ref);
        done(c, tag, text[0], text[1]);
    }

    /** The question goes; nothing replaces it. */
    private void dismiss(Context c, String tag) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(tag, Push.NOTICE_ID);
    }

    /** Replaces the question with a short answer that goes away by itself. */
    private void done(Context c, String tag, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.cancel(tag, Push.NOTICE_ID);
        Notification n = new Notification.Builder(c, Push.CH_QUIET)
                .setSmallIcon(R.drawable.ic_stat).setColor(0xFF3D6B50)
                .setContentTitle(title).setContentText(text)
                .setContentIntent(Push.open(c, 7, "today"))
                .setAutoCancel(true).setTimeoutAfter(15000).build();
        nm.notify(tag + ":done", Push.NOTICE_ID, n);
    }

    /** The answer could not be sent from here: the question stays, and a line beside it opens the app on it (Android does not let a button open a screen directly). */
    private void retry(Context c, String tag, String why) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = new Notification.Builder(c, Push.CH_QUIET)
                .setSmallIcon(R.drawable.ic_stat).setColor(0xFF3D6B50)
                .setContentTitle("Could not send your answer")
                .setContentText((why.isEmpty() ? "" : why + " ") + "Tap to open the app and answer there.")
                .setContentIntent(Push.open(c, 8, "out"))
                .setAutoCancel(true).setTimeoutAfter(60000).build();
        nm.notify(tag + ":retry", Push.NOTICE_ID, n);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
