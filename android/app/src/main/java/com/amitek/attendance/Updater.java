package com.amitek.attendance;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The app's own small store: looks for a newer app on GitHub, downloads it and hands it to Android's installer.
 * The employee sees one button; Android adds its own "Update?" confirmation (it is the phone's protection).
 */
final class Updater {
    private Updater() {}

    static final String ACT_RESULT = "com.amitek.attendance.INSTALL_RESULT";
    private static final String RELEASE_API = "https://api.github.com/repos/drsourrabhbairwa/attendance/releases/latest";
    private static final long QUIET_MS = 6 * 3600 * 1000L;   // how often the app looks by itself
    private static final long RETRY_MS = 15 * 60 * 1000L;    // how long it waits after a look that failed
    private static volatile boolean checking, downloading;

    /** The page (or nobody, with the app closed) wants to hear about progress. */
    interface Listener { void onChange(String infoJson); }
    private static volatile Listener listener;
    static void listen(Listener l) { listener = l; }

    private static void changed(Context c) {
        Listener l = listener;
        if (l != null) { try { l.onChange(info(c).toString()); } catch (Exception ignored) {} }
    }

    static int build(Context c) {
        try {
            android.content.pm.PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return (int) (Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode);
        } catch (Exception e) { return 0; }
    }

    static boolean canInstall(Context c) {
        return Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    // ── what the App Store screen shows ──
    static JSONObject info(Context c) {
        JSONObject s = stored(c);
        int cur = build(c), latest = s.optInt("latest", 0), min = s.optInt("minBuild", 0);
        long now = System.currentTimeMillis();
        String st = UpdateLogic.effectiveState(s.optString("state", "idle"), downloading, s.optLong("stateAt", 0), now, cur, latest);
        long checked = s.optLong("checkedAt", 0), failed = s.optLong("failedAt", 0);
        try {
            s.put("build", cur)
                    .put("newer", UpdateLogic.newer(cur, latest))
                    .put("forced", UpdateLogic.forced(cur, min))
                    .put("canInstall", canInstall(c))
                    .put("checking", checking)
                    .put("state", st)
                    .put("stuck", UpdateLogic.installStuck(st, s.optLong("stateAt", 0), now))
                    .put("fails", UpdateLogic.failsShown(s.optInt("fails", 0), s.optInt("failsBuild", 0), latest))
                    .put("checkFailed", failed > 0 && failed >= checked);   // the last look did not get an answer: "up to date" must not be claimed
        } catch (Exception ignored) {}
        return s;
    }

    /** One change to the saved update facts; always applied to what is saved right now, so two threads cannot overwrite each other. */
    private interface Edit { void apply(JSONObject s) throws Exception; }

    private static synchronized void edit(Context c, Edit e) {
        JSONObject s = stored(c);
        try { e.apply(s); } catch (Exception ignored) {}
        Store.update(c, s.toString());
    }

    private static void setState(Context c, String state, int pct) {
        edit(c, s -> s.put("state", state).put("pct", pct).put("stateAt", System.currentTimeMillis()));
    }

    private static JSONObject stored(Context c) {
        try { return new JSONObject(Store.update(c)); } catch (Exception e) { return new JSONObject(); }
    }

    // ── looking for a new app ──
    /** Looks now (the person pressed "Check again", or the store was opened). */
    static void check(Context c) {
        synchronized (Updater.class) { if (checking) return; checking = true; }
        changed(c);
        try {
            // The office's lowest allowed build first and on its own: it must be known (and may be lowered again) even when GitHub does not answer
            try {
                String home = c.getString(R.string.app_url);
                if (!home.endsWith("/")) home += "/";
                final int min = UpdateLogic.minBuildFrom(get(home + "version.json?t=" + System.currentTimeMillis(), "application/json"));
                edit(c, s -> s.put("minBuild", min));
            } catch (Exception ignored) { /* the old floor stays */ }

            final UpdateLogic.Release r = UpdateLogic.parseRelease(get(RELEASE_API, "application/vnd.github+json"));
            edit(c, s -> {
                UpdateLogic.applyRelease(s, r);   // no qualifying release any more: the old one is forgotten, so nothing stale can block the screen
                s.put("checkedAt", System.currentTimeMillis()).put("failedAt", 0);
                if ("error".equals(s.optString("state"))) s.put("state", "idle").put("err", "");
            });
            if (r != null && UpdateLogic.newer(build(c), r.build) && Store.updateNotified(c) != r.build) {
                Store.updateNotified(c, r.build);
                announce(c, r.build);
            }
        } catch (Exception e) {
            // no internet or GitHub is busy: the old answer stays; not asked again for a quarter of an hour
            edit(c, s -> s.put("failedAt", System.currentTimeMillis()));
        } finally {
            checking = false;
            changed(c);
        }
    }

    /** Looks only if it has been a while: called when the app opens, when a push arrives and from the tracker. */
    static void maybeCheck(Context c) {
        JSONObject s = stored(c);
        long now = System.currentTimeMillis(), at = s.optLong("checkedAt", 0), failed = s.optLong("failedAt", 0);
        if (failed > 0 && failed <= now && now - failed < RETRY_MS) return;   // GitHub or the internet said no a moment ago
        if (now - at >= QUIET_MS || at > now) check(c);
    }

    private static void announce(Context c, int build) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = new Notification.Builder(c, Push.CH_UPDATES)
                .setSmallIcon(R.drawable.ic_stat).setColor(0xFF3D6B50)
                .setContentTitle("A new version of the app is ready")
                .setContentText("Open the app → Me → App Store, and tap Update.")
                .setContentIntent(Push.open(c, 9, "store"))
                .setAutoCancel(true).build();
        nm.notify("update", 100, n);
    }

    // ── downloading and installing ──
    /** @return "ok" started, "permission" Android must first allow this app to install apps, "none" nothing to install, "busy" already running */
    static String start(Context c) {
        final JSONObject s = stored(c);
        final String url = s.optString("url");
        if (!UpdateLogic.trusted(url) || !UpdateLogic.newer(build(c), s.optInt("latest", 0))) return "none";
        synchronized (Updater.class) { if (downloading) return "busy"; downloading = true; }
        if (!canInstall(c)) {
            downloading = false;
            try {
                c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
            return "permission";
        }
        new Thread(() -> {
            try { download(c, url, s.optLong("size", 0), s.optString("digest")); }
            catch (Exception e) {
                fail(c, e.getMessage() != null && e.getMessage().startsWith("checksum")
                        ? "The downloaded update was damaged. Tap Try again."
                        : "Could not download the update. Check the internet and try again.");
            }
            finally { downloading = false; changed(c); }
        }).start();
        return "ok";
    }

    private static void download(Context c, String url, long size, String digest) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        // An earlier try that is still waiting for an answer would pile up beside this one and confuse Android's box: clear them first
        try { for (PackageInstaller.SessionInfo old : pi.getMySessions()) { try { pi.abandonSession(old.getSessionId()); } catch (Exception ignored) {} } } catch (Exception ignored) {}
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(c.getPackageName());
        if (size > 0) sp.setSize(size);
        if (Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(sp);
        PackageInstaller.Session session = pi.openSession(id);
        boolean committed = false;
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(20000);
            h.setReadTimeout(30000);
            h.setInstanceFollowRedirects(true);   // GitHub sends the file from its own storage address
            h.setRequestProperty("User-Agent", "AmitekApp");
            if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
            long total = h.getContentLengthLong() > 0 ? h.getContentLengthLong() : size;
            long done = 0; int lastPct = -1;
            java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
            edit(c, x -> x.put("err", ""));
            setState(c, "downloading", 0);
            changed(c);
            try (InputStream in = h.getInputStream(); OutputStream out = session.openWrite("base.apk", 0, total > 0 ? total : -1)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) {
                    out.write(b, 0, n);
                    sha.update(b, 0, n);
                    done += n;
                    int pct = UpdateLogic.percent(done, total);
                    if (pct != lastPct && (pct - lastPct >= 3 || pct == 100)) {
                        lastPct = pct;
                        setState(c, "downloading", pct);
                        changed(c);
                    }
                }
                if (total > 0 && done != total) throw new Exception("incomplete");
                if (size > 0 && done != size) throw new Exception("incomplete: not the size GitHub lists");
                if (!digest.isEmpty() && !digest.equals(UpdateLogic.hex(sha.digest()))) throw new Exception("checksum");
                session.fsync(out);
            }
            h.disconnect();
            setState(c, "installing", 100);
            Store.updating(c, true);
            Intent i = new Intent(c, InstallResultReceiver.class).setAction(ACT_RESULT).setPackage(c.getPackageName());
            PendingIntent pend = PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
            session.commit(pend.getIntentSender());
            committed = true;
        } finally {
            session.close();
            if (!committed) { try { pi.abandonSession(id); } catch (Exception ignored) {} }
        }
    }

    static void fail(Context c, String message) {
        edit(c, s -> {
            int latest = s.optInt("latest", 0);
            s.put("fails", UpdateLogic.failCount(s.optInt("fails", 0), s.optInt("failsBuild", 0), latest)).put("failsBuild", latest)
                    .put("err", message).put("state", "error").put("pct", 0).put("stateAt", System.currentTimeMillis());
        });
        Store.updating(c, false);
        changed(c);
    }

    // ── small web helper (GitHub and the app's own page) ──
    private static String get(String url, String accept) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(20000);
        h.setRequestProperty("Accept", accept);
        h.setRequestProperty("User-Agent", "AmitekApp");
        h.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
            try (InputStream in = h.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { h.disconnect(); }
    }
}
