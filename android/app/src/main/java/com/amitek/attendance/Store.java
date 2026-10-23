package com.amitek.attendance;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The running site session and the tracker's state, kept on the phone so it survives restarts. */
final class Store {
    private Store() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("tracker", Context.MODE_PRIVATE);
    }

    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date()); }
    static int minutesNow() { java.util.Calendar k = java.util.Calendar.getInstance(); return k.get(java.util.Calendar.HOUR_OF_DAY) * 60 + k.get(java.util.Calendar.MINUTE); }
    static String clock() { return new SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH).format(new Date()); }

    /** Today's session, or null (a session from an earlier day is dropped). */
    static JSONObject session(Context c) {
        try {
            String s = sp(c).getString("session", null);
            if (s == null) return null;
            JSONObject o = new JSONObject(s);
            if (!today().equals(o.optString("date"))) { clear(c); return null; }
            // All-staff tracking is for office hours only: after the closing time the session ends by itself
            int endMin = o.optInt("endMin", 0);
            if (endMin > 0 && minutesNow() >= endMin) { clear(c); return null; }
            return o;
        } catch (Exception e) { return null; }
    }

    static void begin(Context c, JSONObject session) {
        sp(c).edit().putString("session", session.toString()).putLong("nextAt", 0).putLong("failAt", 0).putBoolean("needSignIn", false).apply();
    }

    static void clear(Context c) {
        sp(c).edit().remove("session").remove("nextAt").remove("failAt").remove("needSignIn").apply();
    }

    /** The staff member's private sign-in token: the page hands over a fresh one whenever the server renews it. */
    static void setToken(Context c, String token) {
        refreshAuthToken(c, token);
        try {
            String s = sp(c).getString("session", null);
            if (s == null) return;
            JSONObject o = new JSONObject(s);
            String t = token == null ? "" : token;
            o.put("token", t);
            SharedPreferences.Editor e = sp(c).edit().putString("session", o.toString());
            if (!t.isEmpty()) e.putBoolean("needSignIn", false);
            e.apply();
        } catch (Exception ignored) {}
    }

    // ── who is signed in on this phone (for the notification buttons and push registration, with or without site work) ──
    /** The page hands over empId, token, scriptUrl and secret whenever the person signs in or the token is renewed; "" or an empty object signs the phone out. */
    static void auth(Context c, String json) {
        try {
            JSONObject o = (json == null || json.trim().isEmpty()) ? null : new JSONObject(json);
            if (o == null || o.optString("empId").isEmpty() || o.optString("token").isEmpty() || o.optString("scriptUrl").isEmpty()) { sp(c).edit().remove("auth").apply(); return; }
            sp(c).edit().putString("auth", o.toString()).apply();
        } catch (Exception e) { sp(c).edit().remove("auth").apply(); }
    }

    static JSONObject auth(Context c) {
        try { String s = sp(c).getString("auth", null); return s == null ? null : new JSONObject(s); } catch (Exception e) { return null; }
    }

    /** The newest sign-in token also goes to the stored sign-in, so notification buttons keep working after a renewal. */
    private static void refreshAuthToken(Context c, String token) {
        try {
            JSONObject a = auth(c);
            if (a != null && token != null && !token.isEmpty()) sp(c).edit().putString("auth", a.put("token", token).toString()).apply();
        } catch (Exception ignored) {}
    }

    // ── phone push token and what was last registered with the server ──
    static String fcmToken(Context c) { return sp(c).getString("fcmToken", ""); }
    static void fcmToken(Context c, String t) { sp(c).edit().putString("fcmToken", t == null ? "" : t).apply(); }
    static String fcmSent(Context c) { return sp(c).getString("fcmSent", ""); }
    static void fcmSent(Context c, String v) { sp(c).edit().putString("fcmSent", v == null ? "" : v).apply(); }

    // ── screen a notification tap should open, kept until the page picks it up ──
    static synchronized void route(Context c, String r) { sp(c).edit().putString("route", r == null ? "" : r).apply(); }
    static synchronized String takeRoute(Context c) {
        String r = sp(c).getString("route", "");
        if (!r.isEmpty()) sp(c).edit().remove("route").apply();
        return r;
    }

    // ── in-app updater state ──
    static String update(Context c) { return sp(c).getString("update", "{}"); }
    static void update(Context c, String json) { sp(c).edit().putString("update", json).apply(); }
    static int updateNotified(Context c) { return sp(c).getInt("updNotified", 0); }
    static void updateNotified(Context c, int build) { sp(c).edit().putInt("updNotified", build).apply(); }
    static boolean updating(Context c) { return sp(c).getBoolean("updating", false); }
    static void updating(Context c, boolean v) { sp(c).edit().putBoolean("updating", v).apply(); }

    static String token(Context c) { JSONObject s = session(c); return s == null ? "" : s.optString("token"); }

    /** The server stopped accepting this sign-in: the checks wait in the queue until the person opens the app. */
    static boolean needsSignIn(Context c) { return sp(c).getBoolean("needSignIn", false); }
    static void needsSignIn(Context c, boolean v) { sp(c).edit().putBoolean("needSignIn", v).apply(); }

    static long nextAt(Context c) { return sp(c).getLong("nextAt", 0); }
    static void nextAt(Context c, long t) { sp(c).edit().putLong("nextAt", t).apply(); }
    static long failAt(Context c) { return sp(c).getLong("failAt", 0); }
    static void failAt(Context c, long t) { sp(c).edit().putLong("failAt", t).apply(); }

    /** Check requests already handled, so each one is answered once. */
    static synchronized boolean seen(Context c, String id) {
        try { JSONArray a = new JSONArray(sp(c).getString("seen", "[]")); for (int i = 0; i < a.length(); i++) if (id.equals(a.optString(i))) return true; } catch (Exception ignored) {}
        return false;
    }

    static synchronized void markSeen(Context c, String id) {
        try {
            JSONArray a = new JSONArray(sp(c).getString("seen", "[]"));
            a.put(id);
            while (a.length() > 40) a.remove(0);
            sp(c).edit().putString("seen", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    static String sosSeen(Context c) { return sp(c).getString("sosSeen", ""); }
    static void sosSeen(Context c, String k) { sp(c).edit().putString("sosSeen", k).apply(); }

    /** Checks that could not be sent (no internet); sent later, oldest first. */
    static synchronized JSONArray queue(Context c) {
        try { return new JSONArray(sp(c).getString("queue", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    static synchronized void queue(Context c, JSONArray q) {
        while (q.length() > 300) q.remove(0);
        sp(c).edit().putString("queue", q.toString()).apply();
    }

    static synchronized void enqueue(Context c, String url, JSONObject body) {
        try {
            JSONArray q = queue(c);
            q.put(new JSONObject().put("url", url).put("body", body));
            queue(c, q);
        } catch (Exception ignored) {}
    }
}
