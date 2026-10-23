package com.amitek.attendance;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The decisions of the in-app updater, kept free of Android classes so they can be unit-tested. */
final class UpdateLogic {
    private UpdateLogic() {}

    static final String APK_NAME = "amitek-attendance.apk";
    private static final String OWN_RELEASES = "https://github.com/drsourrabhbairwa/attendance/releases/download/";
    private static final Pattern TAG = Pattern.compile("^app-v(\\d{1,9})(?:\\.\\d+)?$");

    /** "app-v12.1" -> 12 (the GitHub run number, which is also the app's versionCode). -1 when the tag is not one of ours. */
    static int buildOfTag(String tag) {
        if (tag == null) return -1;
        Matcher m = TAG.matcher(tag.trim());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** A newer build exists. Unknown never counts as newer, so a broken reply cannot start an update. */
    static boolean newer(int current, int latest) { return latest > 0 && latest > current; }

    /** The office has set a floor: any build below it must update before the app is used. */
    static boolean forced(int current, int minBuild) { return minBuild > 0 && current < minBuild; }

    /** Only this project's own release downloads are ever fetched and installed. */
    static boolean trusted(String url) {
        return url != null && url.startsWith(OWN_RELEASES) && !url.contains("..") && !url.contains("@");
    }

    /** The download address of the APK among a release's assets; "" when it is missing or not from our own releases. */
    static String apkUrl(JSONArray assets) {
        if (assets == null) return "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null || !APK_NAME.equals(a.optString("name"))) continue;
            String u = a.optString("browser_download_url");
            if (trusted(u)) return u;
        }
        return "";
    }

    private static final Pattern SHA256 = Pattern.compile("^sha256:([0-9a-fA-F]{64})$");

    /** The SHA-256 GitHub reports for the APK asset (lower-case hex); "" when it is not reported, so nothing is checked. */
    static String apkDigest(JSONArray assets) {
        if (assets == null) return "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null || !APK_NAME.equals(a.optString("name"))) continue;
            Matcher m = SHA256.matcher(a.optString("digest"));
            return m.find() ? m.group(1).toLowerCase(java.util.Locale.ROOT) : "";
        }
        return "";
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return sb.toString();
    }

    static long apkSize(JSONArray assets) {
        if (assets == null) return 0;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && APK_NAME.equals(a.optString("name"))) return a.optLong("size", 0);
        }
        return 0;
    }

    /** What the App Store screen needs to know about the newest installable app. */
    static final class Release {
        final int build; final String url; final long size; final String notes; final String digest;
        Release(int build, String url, long size, String notes) { this(build, url, size, notes, ""); }
        Release(int build, String url, long size, String notes, String digest) { this.build = build; this.url = url; this.size = size; this.notes = notes; this.digest = digest == null ? "" : digest; }
    }

    /** The newest release as GitHub reports it; null when it is a draft or pre-release, not one of ours, has no APK, or the reply is not JSON. */
    static Release parseRelease(String json) {
        try {
            JSONObject r = new JSONObject(json);
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) return null;
            int build = buildOfTag(r.optString("tag_name"));
            JSONArray assets = r.optJSONArray("assets");
            String url = apkUrl(assets);
            if (build <= 0 || url.isEmpty()) return null;
            return new Release(build, url, apkSize(assets), cleanNotes(r.optString("body")), apkDigest(assets));
        } catch (Exception e) { return null; }
    }

    /** The release facts kept for the screen: set from a good release, removed when the newest release no longer qualifies. The floor (minBuild) is a separate fact and is left alone. */
    static void applyRelease(JSONObject s, Release r) {
        try {
            if (r == null) { s.remove("latest"); s.remove("url"); s.remove("size"); s.remove("notes"); s.remove("digest"); return; }
            s.put("latest", r.build).put("url", r.url).put("size", r.size).put("notes", r.notes).put("digest", r.digest);
        } catch (Exception ignored) {}
    }

    static final long INSTALL_STALE_MS = 2 * 60 * 1000L;   // Android's "Update?" box not answered for this long: let the person start over
    static final long INSTALL_STUCK_MS = 30 * 1000L;       // after this long the screen offers "Try again" beside the wait message

    /**
     * What the screen should show, given the saved state: a state that cannot be true any more (the app was closed mid-download,
     * the confirmation was never answered, a failure with nothing left to install) becomes "idle" so no screen can stay stuck.
     */
    static String effectiveState(String stored, boolean downloadingNow, long stateAt, long now, int current, int latest) {
        String st = stored == null || stored.isEmpty() ? "idle" : stored;
        if (!newer(current, latest)) return "idle";
        if ("downloading".equals(st) && !downloadingNow) return "idle";
        if ("installing".equals(st) && (stateAt > now || now - stateAt > INSTALL_STALE_MS)) return "idle";
        return st;
    }

    /** The install has been waiting long enough that "Try again" should be offered. */
    static boolean installStuck(String state, long stateAt, long now) {
        return "installing".equals(state) && (stateAt > now || now - stateAt > INSTALL_STUCK_MS);
    }

    /** Failures in a row for the build being installed; a different build starts the count over. */
    static int failCount(int prevFails, int prevBuild, int latest) { return prevBuild == latest ? prevFails + 1 : 1; }

    /** The failure count to show: only failures of the build that is on offer now. */
    static int failsShown(int fails, int failsBuild, int latest) { return failsBuild == latest ? fails : 0; }

    /** "minBuild" from the page's version.json; 0 when absent or unreadable (no floor). */
    static int minBuildFrom(String versionJson) {
        try { return Math.max(0, new JSONObject(versionJson).optInt("minBuild", 0)); } catch (Exception e) { return 0; }
    }

    /** Release notes as short plain text for the App Store screen. */
    static String cleanNotes(String body) {
        if (body == null) return "";
        String t = body.replace("\r", "").replaceAll("(?m)^#+\\s*", "").replaceAll("[*`]+", "").trim();
        return t.length() > 600 ? t.substring(0, 600).trim() + "…" : t;
    }

    /** Percent downloaded, 0..100; an unknown total counts as 0. */
    static int percent(long done, long total) {
        if (total <= 0 || done <= 0) return 0;
        return (int) Math.min(100, done * 100 / total);
    }
}
