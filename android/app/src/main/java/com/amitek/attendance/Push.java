package com.amitek.attendance;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.service.notification.StatusBarNotification;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.util.Map;

/** Push messages from the office: how they sound, what they look like, and what a tap or a button does. */
final class Push {
    private Push() {}

    static final String CH_ALARM = "alarm", CH_REMINDERS = "reminders", CH_REPORTS = "reports", CH_UPDATES = "updates", CH_QUIET = "quiet";
    static final int NOTICE_ID = 100;
    private static final long QUESTION_LIFE_MS = 3 * 3600 * 1000L;   // a question with buttons does not linger all evening
    static final String EXTRA_ROUTE = "route";
    static final String ACT_YES = "com.amitek.attendance.LEFT_YES", ACT_NO = "com.amitek.attendance.LEFT_NO";
    private static final int BRAND = Color.parseColor("#3D6B50");

    /** Five channels, so the person can silence reports without silencing a location check. */
    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();

        NotificationChannel alarm = new NotificationChannel(CH_ALARM, "Urgent: location checks and emergencies", NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription("The office needs an answer now");
        alarm.enableVibration(true);
        alarm.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
        alarm.setSound(sound, aa);
        alarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(alarm);

        NotificationChannel rem = new NotificationChannel(CH_REMINDERS, "Reminders: punch in, punch out, tasks", NotificationManager.IMPORTANCE_HIGH);
        rem.setDescription("Reminders about your day");
        rem.enableVibration(true);
        rem.setSound(sound, aa);
        nm.createNotificationChannel(rem);

        NotificationChannel rep = new NotificationChannel(CH_REPORTS, "Reports and summaries", NotificationManager.IMPORTANCE_DEFAULT);
        rep.setDescription("Your weekly summary and the office reports sent to you");
        nm.createNotificationChannel(rep);

        NotificationChannel upd = new NotificationChannel(CH_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT);
        upd.setDescription("A new version of this app is ready");
        nm.createNotificationChannel(upd);

        NotificationChannel quiet = new NotificationChannel(CH_QUIET, "Answers and confirmations", NotificationManager.IMPORTANCE_LOW);
        quiet.setDescription("A short line after you answer from a notification");
        nm.createNotificationChannel(quiet);
    }

    static PendingIntent open(Context c, int code, String route) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_ROUTE, route);
        i.setAction("open:" + route);   // a different action per screen, so the system keeps their intents apart
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** A message arrived (the app may be closed). Drawn here, so it can have its own look and buttons. */
    static void handle(Context c, Map<String, String> d) {
        if (d == null || d.isEmpty()) return;
        // Only the person signed in on this phone gets it: a phone signed out, or handed to someone else, shows nothing
        JSONObject auth = Store.auth(c);
        String to = nz(d.get("empId"));
        if (auth == null || !PushLogic.forPerson(to, auth.optString("empId"))) return;
        String title = nz(d.get("title")), body = nz(d.get("body"));
        if (title.isEmpty() && body.isEmpty()) return;
        if (PushLogic.pageShows(nz(d.get("kind")), PushLogic.channelOf(d.get("channel")), MainActivity.isVisible())) {
            // the person is looking at the app: the page shows it itself, no need for a second one in the notification list
            MainActivity.evalInPage("window.AmkNotify&&AmkNotify.refresh&&AmkNotify.refresh()");
            return;
        }
        try { show(c, d, title, body, to.isEmpty() ? auth.optString("empId") : to); } catch (Exception ignored) {}
        new Thread(() -> { try { Updater.maybeCheck(c); } catch (Exception ignored) {} }).start();   // a push is a good moment to look for a new app version
    }

    private static void show(Context c, Map<String, String> d, String title, String body, String empId) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        String kind = nz(d.get("kind")), noticeId = nz(d.get("noticeId")), ref = nz(d.get("ref"));
        String channel = PushLogic.channelOf(d.get("channel")), route = PushLogic.routeOf(nz(d.get("route")));
        String tag = noticeId.isEmpty() ? kind + ":" + System.currentTimeMillis() : noticeId;   // the same notice again replaces itself instead of stacking
        int code = (tag.hashCode() & 0x3fffffff);

        boolean question = "leftOffice".equals(d.get("actions")) && noticeId.startsWith("left|");
        Notification.Builder b = new Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(BRAND)
                .setContentTitle(title)
                .setContentText(firstLine(body))
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(open(c, code, route))
                .setAutoCancel(!question)   // a question stays until it is answered, so a failed button press does not make it vanish
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setCategory(CH_ALARM.equals(channel) ? Notification.CATEGORY_ALARM : CH_REPORTS.equals(channel) ? Notification.CATEGORY_RECOMMENDATION : Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(new Notification.Builder(c, channel).setSmallIcon(R.drawable.ic_stat).setColor(BRAND).setContentTitle("Amitek Attendance").setContentText("You have a new message").build());

        if (question) {
            b.setTimeoutAfter(QUESTION_LIFE_MS);
            b.addAction(new Notification.Action.Builder(null, ref.isEmpty() ? "Yes, punch out" : "Yes, punch out at " + ref, action(c, ACT_YES, noticeId, tag, ref, empId, code + 1)).build());
            b.addAction(new Notification.Action.Builder(null, "Still working", action(c, ACT_NO, noticeId, tag, ref, empId, code + 2)).build());
        }
        nm.notify(tag, NOTICE_ID, b.build());
    }

    private static PendingIntent action(Context c, String act, String noticeId, String tag, String ref, String empId, int code) {
        Intent i = new Intent(c, NoticeActionReceiver.class).setAction(act)
                .putExtra("noticeId", noticeId).putExtra("tag", tag).putExtra("ref", ref).putExtra("empId", empId);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** One message is no longer needed (answered or read in the app): its notification goes. */
    static void clear(Context c, String noticeId) {
        if (noticeId == null || noticeId.isEmpty()) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.cancel(noticeId, NOTICE_ID);
        nm.cancel(noticeId + ":retry", NOTICE_ID);
    }

    /** Someone else (or nobody) is signed in now: the previous person's messages and questions must not stay in the notification list. */
    static void clearAll(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        try {
            for (StatusBarNotification n : nm.getActiveNotifications()) {
                String tag = n.getTag();
                if (n.getId() == NOTICE_ID && (tag == null || !tag.startsWith("update"))) nm.cancel(tag, NOTICE_ID);   // the app-update notices belong to the phone, not a person
            }
        } catch (Exception ignored) {}
    }

    /** Tells the office this phone's push address, once per person and token. Runs on a background thread. */
    static void register(Context c) {
        JSONObject a = Store.auth(c);
        String tok = Store.fcmToken(c);
        if (a == null || tok.isEmpty()) return;
        String key = a.optString("empId").toUpperCase() + "|" + tok;
        if (key.equals(Store.fcmSent(c))) return;
        try {
            Net.post(a.optString("scriptUrl"), new JSONObject().put("action", "registerPushToken")
                    .put("secret", a.optString("secret")).put("token", a.optString("token"))
                    .put("empId", a.optString("empId")).put("pushToken", tok).put("platform", "android"));
            Store.fcmSent(c, key);
        } catch (Exception ignored) {
            // no internet or signed out: tried again at the next app start or token change
        }
    }

    /** Asks Firebase for this phone's token (it may take a moment on first start) and registers it. */
    static void refreshToken(Context c) {
        final Context app = c.getApplicationContext();   // the screen may be gone by the time Firebase answers
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> {
                if (t == null || t.isEmpty()) return;
                Store.fcmToken(app, t);
                new Thread(() -> register(app)).start();
            });
        } catch (Exception ignored) {}
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }
}
