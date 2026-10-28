package com.amitek.attendance;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Random;

/**
 * Site work tracking that keeps going with the phone locked or the app closed.
 * Location is checked at random 45–75 minute gaps (retry every 5 minutes when there is
 * no location), each check is sent to the Google Sheet, and fake-GPS apps, location
 * switched off and permission removed are all reported. Only Stop Site Work / Punch Out
 * (or midnight) ends it.
 */
public class TrackerService extends Service {
    static final String ACT_START = "start", ACT_CHECK = "check", ACT_FORCE = "force", ACT_POLL = "poll";
    private static final String SAFETY_CHANNEL = "safety_alerts";
    private static final long POLL_MS = 2 * 60 * 1000L;
    private static volatile boolean polling;
    private static final String CHANNEL = "site_tracking";
    private static final int NOTE_ID = 7;
    private static volatile boolean busy;
    private static volatile boolean noteSignIn;   // the ongoing notification currently asks the person to open the app
    private BroadcastReceiver locationSwitch;

    // ── called by the app ──
    static void start(Context c) {
        c.startForegroundService(new Intent(c, TrackerService.class).setAction(ACT_START));
    }

    static void stop(Context c) {
        Store.clear(c);
        cancelAlarm(c);
        c.stopService(new Intent(c, TrackerService.class));
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Site work tracking", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Shown while site work is running");
        nm.createNotificationChannel(ch);
        NotificationChannel sc = new NotificationChannel(SAFETY_CHANNEL, "Location checks and emergencies", NotificationManager.IMPORTANCE_HIGH);
        sc.setDescription("The office asks for your location, or answers your emergency");
        sc.enableVibration(true);
        sc.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 400});
        nm.createNotificationChannel(sc);
        // Location switched off in quick settings: record it straight away
        locationSwitch = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Store.session(c) != null && !Fix.locationOn(c)) check(true);
            }
        };
        registerReceiver(locationSwitch, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(locationSwitch); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        JSONObject s = Store.session(this);
        if (s == null) { cancelAlarm(this); stopSelf(); return START_NOT_STICKY; }
        try {
            foreground(Fix.locationOn(this) ? "Site work tracking is on" : "⚠ Location is off — turn it on");
        } catch (Exception e) {
            // Android refused to start location tracking from the background (e.g. after a restart
            // without "Allow all the time"): tell the office, and ask the employee to open the app.
            new Thread(() -> report(null, "Tracking paused — app must be opened")).start();
            stopSelf();
            return START_NOT_STICKY;
        }
        String a = intent == null ? null : intent.getAction();
        boolean force = ACT_FORCE.equals(a);
        if (force || System.currentTimeMillis() >= Store.nextAt(this) - 30000) check(force);
        else schedule(this, Store.nextAt(this));
        poll();
        schedulePoll(this, POLL_MS);
        return START_STICKY;
    }

    private Notification note(String text) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Site work running")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void foreground(String text) {
        Notification n = note(text);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTE_ID, n);
    }

    /** The server no longer accepts this phone's sign-in (signed out, password changed, expired, or this app is too old for the switch): the checks wait in the queue and the notification asks the person to open the app. */
    private void signInNeeded() {
        Store.needsSignIn(this, true);
        noteSignIn = true;
        try { getSystemService(NotificationManager.class).notify(NOTE_ID, note("Open the app to sign in")); } catch (Exception ignored) {}
    }

    private void check(boolean force) {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            PowerManager.WakeLock wl = ((PowerManager) getSystemService(POWER_SERVICE))
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "amitek:check");
            wl.acquire(3 * 60 * 1000L);
            try { runCheck(force); } catch (Exception ignored) {
            } finally { busy = false; if (wl.isHeld()) wl.release(); }
        }).start();
    }

    private void runCheck(boolean force) throws Exception {
        JSONObject s = Store.session(this);
        if (s == null) { stopSelf(); return; }
        if (noteSignIn && !Store.needsSignIn(this)) {   // the person opened the app and signed in again
            noteSignIn = false;
            try { getSystemService(NotificationManager.class).notify(NOTE_ID, note(Fix.locationOn(this) ? "Site work tracking is on" : "⚠ Location is off — turn it on")); } catch (Exception ignored) {}
        }
        flushQueue();
        long startedAt = System.currentTimeMillis();
        String why = null;
        Location loc = null;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            why = "Location permission is blocked";
        } else if (!Fix.locationOn(this)) {
            why = "Location is turned off";
        } else {
            loc = Fix.get(this, 60000);
            if (loc == null) why = "GPS is off or has no signal";
        }
        if (Store.session(this) == null) return;   // stopped while we were waiting for GPS
        if (loc != null) {
            report(loc, null);
            if (Store.session(this) == null) return;   // the server ended this job with that check
            Store.failAt(this, 0);
            // By day the usual 45-75 minutes; from the evening time every eveGap minutes, so a forgotten punch-out is noticed soon
            int gap = Cadence.nextGapMinutes(Store.minutesNow(), s.optInt("minGap", 45), s.optInt("maxGap", 75), s.optInt("eveMin", 0), s.optInt("eveGap", 0), new Random());
            Store.nextAt(this, startedAt + gap * 60000L);
            foreground("Site work tracking is on");
        } else {
            long failAt = Store.failAt(this);
            if (failAt == 0 || System.currentTimeMillis() - failAt >= 3600000L || force) {
                report(null, why);
                if (Store.session(this) == null) return;
                Store.failAt(this, System.currentTimeMillis());
            }
            Store.nextAt(this, startedAt + s.optInt("retry", 5) * 60000L);
            foreground("⚠ " + why + " — please fix it");
        }
        schedule(this, Store.nextAt(this));
        try { Updater.maybeCheck(this); } catch (Exception ignored) {}   // after the check is sent: a slow connection never delays it
    }

    /** Sends one check (loc == null: no location, with the reason). Queued if there is no internet. */
    private void report(Location loc, String why) {
        JSONObject s = Store.session(this);
        if (s == null) return;
        try {
            JSONObject p = new JSONObject()
                    .put("action", "logSitePing").put("secret", s.optString("secret")).put("token", s.optString("token"))
                    .put("id", "ping-" + System.currentTimeMillis() + "-a")
                    .put("siteId", s.optString("siteId")).put("empId", s.optString("empId"))
                    .put("name", s.optString("name")).put("date", s.optString("date"))
                    .put("time", Store.clock()).put("source", "app")
                    .put("siteLat", s.optDouble("siteLat")).put("siteLng", s.optDouble("siteLng"));
            // Battery level goes with every check, so the office is warned before a phone dies
            try {
                Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (b != null) {
                    int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    if (lvl >= 0 && scale > 0) p.put("battery", Math.round(lvl * 100f / scale));
                    p.put("charging", st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL);
                }
            } catch (Exception ignored) {}
            if (loc == null) {
                p.put("gps", false).put("area", why);
            } else {
                double siteLat = s.optDouble("siteLat"), siteLng = s.optDouble("siteLng");
                int radius = s.optInt("radius", 50);
                int dist = (int) Math.round(Fix.meters(siteLat, siteLng, loc.getLatitude(), loc.getLongitude()));
                int acc = Math.round(loc.getAccuracy());
                String status = Fix.isFake(loc) ? "FAKE" : dist <= radius ? "YES" : dist - acc > radius ? "NO" : "ROUGH";
                p.put("lat", String.format(Locale.ENGLISH, "%.6f", loc.getLatitude()))
                        .put("lng", String.format(Locale.ENGLISH, "%.6f", loc.getLongitude()))
                        .put("area", Fix.area(this, loc.getLatitude(), loc.getLongitude()))
                        .put("distMeters", dist).put("status", status).put("withinBound", "YES".equals(status))
                        .put("accuracy", acc);
            }
            String url = s.optString("scriptUrl");
            try {
                if (Net.jobIsOver(Net.post(url, p))) {   // the office side ended this job for the person: stop checking and tracking
                    JSONObject cur = Store.session(this);
                    if (cur != null && cur.optString("siteId").equals(p.optString("siteId"))) stop(this);
                }
            }
            catch (Net.Rejected r) {
                if (Net.signInNeeded(r)) { Store.enqueue(this, url, p); signInNeeded(); }   // keep the check until the person signs in again
                /* any other refusal: don't retry */
            }
            catch (Exception e) { Store.enqueue(this, url, p); }
        } catch (Exception ignored) {}
    }

    // ── "Check now" requests and emergency updates from the office ──
    private void poll() {
        if (polling) return;
        polling = true;
        new Thread(() -> {
            try { doPoll(); } catch (Exception ignored) {
            } finally { polling = false; }
        }).start();
    }

    private void doPoll() throws Exception {
        JSONObject s = Store.session(this);
        if (s == null) return;
        String url = s.optString("scriptUrl");
        JSONObject r;
        try {
            r = Net.post(url, new JSONObject().put("action", "pollEmp")
                    .put("secret", s.optString("secret")).put("token", s.optString("token")).put("empId", s.optString("empId")));
        } catch (Net.Rejected x) {
            if (Net.signInNeeded(x)) signInNeeded();
            return;
        }
        JSONObject d = r.optJSONObject("data");
        if (d == null) return;
        JSONArray checks = d.optJSONArray("checks");
        for (int i = 0; checks != null && i < checks.length(); i++) {
            JSONObject c = checks.optJSONObject(i);
            if (c == null) continue;
            String id = c.optString("id");
            if (id.length() == 0 || Store.seen(this, id)) continue;
            Store.markSeen(this, id);
            String who = c.optString("requestedBy", "The office");
            notifySafety(21, "📍 Location check", who + " asked for your location. Sending it now…");
            answer(s, id, who);
        }
        JSONObject sos = d.optJSONObject("sos");
        if (sos != null) {
            String status = sos.optString("status");
            String key = sos.optString("id") + ":" + status;
            if (!key.equals(Store.sosSeen(this))) {
                Store.sosSeen(this, key);
                String by = "aid".equals(status) ? sos.optString("aidBy") : sos.optString("ackBy");
                if ("ack".equals(status)) notifySafety(22, "🚨 Emergency", by + " has seen your emergency and is on it.");
                else if ("aid".equals(status)) notifySafety(22, "🚑 Help is on the way", by + (sos.optString("aidNote").length() > 0 ? ": " + sos.optString("aidNote") : ""));
                else if ("resolved".equals(status)) notifySafety(22, "✅ Emergency resolved", sos.optString("resolveNote", "Marked resolved by the office"));
            }
        }
    }

    /** Answers a location check straight away, with the phone locked or the app closed. */
    private void answer(JSONObject s, String checkId, String who) {
        PowerManager.WakeLock wl = ((PowerManager) getSystemService(POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "amitek:answer");
        wl.acquire(2 * 60 * 1000L);
        try {
            String why = null;
            Location loc = null;
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                why = "Location permission is blocked";
            } else if (!Fix.locationOn(this)) {
                why = "Location is turned off";
            } else {
                loc = Fix.get(this, 45000);
                if (loc == null) why = "GPS is off or has no signal";
            }
            JSONObject p = new JSONObject()
                    .put("action", "answerCheck").put("secret", s.optString("secret")).put("token", s.optString("token"))
                    .put("id", checkId).put("empId", s.optString("empId"))
                    .put("date", Store.today()).put("time", Store.clock()).put("source", "app")
                    .put("siteId", s.optString("siteId"))
                    .put("siteLat", s.optDouble("siteLat")).put("siteLng", s.optDouble("siteLng"));
            try {
                Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (b != null) {
                    int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    if (lvl >= 0 && scale > 0) p.put("battery", Math.round(lvl * 100f / scale));
                    p.put("charging", st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL);
                }
            } catch (Exception ignored) {}
            if (loc == null) {
                p.put("gps", false).put("area", why);
            } else {
                int radius = s.optInt("radius", 50);
                int dist = (int) Math.round(Fix.meters(s.optDouble("siteLat"), s.optDouble("siteLng"), loc.getLatitude(), loc.getLongitude()));
                int acc = Math.round(loc.getAccuracy());
                String status = Fix.isFake(loc) ? "FAKE" : dist <= radius ? "YES" : dist - acc > radius ? "NO" : "ROUGH";
                p.put("lat", String.format(Locale.ENGLISH, "%.6f", loc.getLatitude()))
                        .put("lng", String.format(Locale.ENGLISH, "%.6f", loc.getLongitude()))
                        .put("area", Fix.area(this, loc.getLatitude(), loc.getLongitude()))
                        .put("distMeters", dist).put("status", status).put("withinBound", "YES".equals(status))
                        .put("accuracy", acc);
            }
            String url = s.optString("scriptUrl");
            try { Net.post(url, p); }
            catch (Net.Rejected r) {
                if (Net.signInNeeded(r)) { Store.enqueue(this, url, p); signInNeeded(); }   // the answer is sent as soon as the person signs in again
                /* any other refusal, e.g. already answered */
            }
            catch (Exception e) { Store.enqueue(this, url, p); }
            notifySafety(21, loc != null ? "📍 Location sent" : "📍 Location not available",
                    loc != null ? "Your location was sent to " + who + "." : why + ". The office has been told — please fix it.");
        } catch (Exception ignored) {
        } finally { if (wl.isHeld()) wl.release(); }
    }

    private void notifySafety(int id, String title, String text) {
        try {
            Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(this, SAFETY_CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            getSystemService(NotificationManager.class).notify(id, n);
        } catch (Exception ignored) {}
    }

    private void flushQueue() {
        JSONArray q = Store.queue(this);
        if (q.length() == 0) return;
        JSONArray left = new JSONArray();
        boolean offline = false;
        for (int i = 0; i < q.length(); i++) {
            JSONObject item = q.optJSONObject(i);
            if (item == null) continue;
            if (offline) { left.put(item); continue; }
            try {
                JSONObject body = item.optJSONObject("body");
                String t = Store.token(this);   // a check waits in the queue with the token it was made with; send it with today's
                if (body != null && !t.isEmpty()) body.put("token", t);
                Net.post(item.optString("url"), body);
            }
            catch (Net.Rejected r) {
                if (Net.signInNeeded(r)) { offline = true; left.put(item); signInNeeded(); }   // keep it and everything behind it
                /* any other refusal: drop */
            }
            catch (Exception e) { offline = true; left.put(item); }
        }
        Store.queue(this, left);
    }

    // ── wake-up alarm for the next check (works in Doze, may be a few minutes late) ──
    private static PendingIntent alarmIntent(Context c) {
        return PendingIntent.getService(c, 1, new Intent(c, TrackerService.class).setAction(ACT_CHECK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedule(Context c, long at) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(at, System.currentTimeMillis() + 60000), alarmIntent(c));
    }

    private static PendingIntent pollIntent(Context c) {
        return PendingIntent.getService(c, 2, new Intent(c, TrackerService.class).setAction(ACT_POLL),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedulePoll(Context c, long inMs) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + inMs, pollIntent(c));
    }

    static void cancelAlarm(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) { am.cancel(alarmIntent(c)); am.cancel(pollIntent(c)); }
    }
}
