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
        final int build; final String url; final long size; final String notes;
        Release(int build, String url, long size, String notes) { this.build = build; this.url = url; this.size = size; this.notes = notes; }
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
            return new Release(build, url, apkSize(assets), cleanNotes(r.optString("body")));
        } catch (Exception e) { return null; }
    }

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
