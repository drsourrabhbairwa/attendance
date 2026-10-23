package com.amitek.attendance;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The attendance web app in a full-screen WebView, plus the bridge that hands site work to TrackerService. */
public class MainActivity extends Activity {
    private static final int REQ_BASIC = 1, REQ_BACKGROUND = 2, REQ_FILE = 3, REQ_MEDIA = 4;
    private ValueCallback<Uri[]> fileCb;   // the web page is waiting for the file the person picks
    private WebView web;
    private String appUrl;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private PermissionRequest camRequest;
    /** The screen that is open right now (null when the app is in the background). */
    private static volatile MainActivity front;
    static boolean isVisible() { return front != null; }

    /** Runs a small script in the open page. Used when a push arrives while the person is looking at the app. */
    static void evalInPage(String js) {
        final MainActivity a = front;
        if (a != null) a.runOnUiThread(() -> { if (a.web != null) a.web.evaluateJavascript(js, null); });
    }
    private WebView printView;   // the hidden page being printed; kept so it is not collected while the print screen is open

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#3D6B50"));
        appUrl = getString(R.string.app_url);
        web = new WebView(this);
        setContentView(web);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUserAgentString(ws.getUserAgentString() + " AmitekApp/" + BuildConfigLite.version(this));
        web.addJavascriptInterface(new Bridge(), "AndroidTracker");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                Uri home = Uri.parse(appUrl);
                if (u.getHost() != null && u.getHost().equals(home.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;   // maps and other links open in their own apps
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) showOffline();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (hasLocation()) cb.invoke(origin, true, true);
                else { geoOrigin = origin; geoCallback = cb; askBasic(); }
            }
            @Override public void onPermissionRequest(PermissionRequest req) {
                runOnUiThread(() -> {
                    List<String> need = new ArrayList<>();
                    for (String r : req.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.CAMERA);
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.RECORD_AUDIO);
                    }
                    if (need.isEmpty()) req.grant(req.getResources());
                    else { camRequest = req; requestPermissions(need.toArray(new String[0]), REQ_MEDIA); }
                });
            }
            /** Lets the page's file buttons (documents, gallery, camera) open the phone's own picker. */
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try { startActivityForResult(params.createIntent(), REQ_FILE); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        askBasic();
        takeRouteFrom(getIntent());
        web.loadUrl(appUrl);
        resumeTracking();
        Push.refreshToken(this);   // the phone's push address; also registered with the office as soon as someone is signed in
    }

    /** A notification tap names the screen to open; the page picks it up as soon as it is ready (AmkApp.flush). */
    private void takeRouteFrom(Intent i) {
        String r = i == null ? null : i.getStringExtra(Push.EXTRA_ROUTE);
        if (r != null && !r.isEmpty()) { Store.route(this, r); i.removeExtra(Push.EXTRA_ROUTE); }
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        takeRouteFrom(i);
        if (web != null) web.evaluateJavascript("window.AmkApp&&AmkApp.flush&&AmkApp.flush()", null);
    }

    /** Tells the page what the App Store screen should show (progress, a new version, an error). */
    private void sendUpdateToPage(String json) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript("window.AmkApp&&AmkApp.onUpdate&&AmkApp.onUpdate(" + json + ")", null); });
    }

    /** If site work is running but the tracker was stopped (phone restart, app killed), start it again. */
    private void resumeTracking() {
        if (Store.session(this) != null) {
            try { TrackerService.start(this); } catch (Exception ignored) {}
        }
    }

    @Override protected void onPause() {
        if (front == this) front = null;
        Updater.listen(null);
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        front = this;
        Updater.listen(this::sendUpdateToPage);
        new Thread(() -> { try { Updater.maybeCheck(MainActivity.this); } catch (Exception ignored) {} }).start();
        if (web != null) web.evaluateJavascript("window.dispatchEvent&&document.dispatchEvent(new Event('visibilitychange'))", null);
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void askBasic() {
        List<String> need = new ArrayList<>();
        for (String p : new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA})
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_BASIC);
    }

    @Override protected void onActivityResult(int code, int res, Intent data) {
        super.onActivityResult(code, res, data);
        if (code != REQ_FILE || fileCb == null) return;
        Uri[] picked = null;
        try { picked = WebChromeClient.FileChooserParams.parseResult(res, data); } catch (Exception ignored) {}
        fileCb.onReceiveValue(picked);
        fileCb = null;
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_MEDIA) {   // camera and/or microphone asked by the page: pass on whatever was allowed
            if (camRequest != null) {
                List<String> ok = new ArrayList<>();
                for (String r : camRequest.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) ok.add(r);
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ok.add(r);
                }
                if (ok.isEmpty()) camRequest.deny(); else camRequest.grant(ok.toArray(new String[0]));
                camRequest = null;
            }
            return;
        }
        if (code != REQ_BASIC) return;
        if (geoCallback != null) { geoCallback.invoke(geoOrigin, hasLocation(), hasLocation()); geoCallback = null; }
        if (camRequest != null) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camRequest.grant(camRequest.getResources());
            else camRequest.deny();
            camRequest = null;
        }
    }

    /** "Allow all the time" lets tracking restart by itself after the phone is switched off and on. */
    private void askBackgroundAndBattery() {
        boolean needBg = Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED;
        PowerManager pm = getSystemService(PowerManager.class);
        boolean needBattery = pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName());
        if (!needBg && !needBattery) return;
        new AlertDialog.Builder(this)
                .setTitle("Keep site tracking running")
                .setMessage((needBg ? "• On the next screen choose “Allow all the time” for location, so tracking continues after the phone restarts.\n\n" : "")
                        + (needBattery ? "• Then allow the app to run in the background (battery: “Allow” / “Unrestricted”)." : ""))
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> {
                    if (needBg) requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BACKGROUND);
                    if (needBattery) {
                        try {
                            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) {}
                    }
                })
                .show();
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    private void showOffline() {
        String html = "<html><body style='font-family:sans-serif;text-align:center;padding:60px 24px;color:#18201C;background:#F0F1E8'>"
                + "<div style='font-size:42px'>📶</div><h2>No internet</h2><p>Site work tracking keeps running and sends its checks when the internet is back.</p>"
                + "<button style='padding:12px 24px;font-size:16px;border:0;border-radius:8px;background:#3D6B50;color:#fff' onclick='location.href=\"" + appUrl + "\"'>Try again</button></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else moveTaskToBack(true);   // keep the app alive instead of closing it
    }

    /** Called from the web page (window.AndroidTracker). Runs on a background thread. */
    private class Bridge {
        @JavascriptInterface public boolean available() { return true; }
        /** The page checks this to know the app can open the file picker and record voice. */
        @JavascriptInterface public boolean hasMedia() { return true; }

        /** json: scriptUrl, secret, token, siteId, empId, name, date, siteLat, siteLng, radius, minGap, maxGap, retry */
        @JavascriptInterface public boolean start(String json) {
            try {
                JSONObject s = new JSONObject(json);
                JSONObject cur = Store.session(MainActivity.this);
                if (cur == null || !cur.optString("siteId").equals(s.optString("siteId"))) Store.begin(MainActivity.this, s);
                else if (!s.optString("token").isEmpty()) Store.setToken(MainActivity.this, s.optString("token"));   // same site work, fresh sign-in
                runOnUiThread(() -> {
                    try { TrackerService.start(MainActivity.this); } catch (Exception ignored) {}
                    askBackgroundAndBattery();
                });
                return true;
            } catch (Exception e) { return false; }
        }

        /** The page calls this whenever the staff sign-in token is new or renewed, so the background checks keep using a valid one. */
        @JavascriptInterface public void setToken(String token) { Store.setToken(MainActivity.this, token); }

        @JavascriptInterface public void stop(String siteId) {
            JSONObject cur = Store.session(MainActivity.this);
            if (cur == null || siteId == null || siteId.isEmpty() || cur.optString("siteId").equals(siteId))
                TrackerService.stop(MainActivity.this);
        }

        @JavascriptInterface public boolean running(String siteId) {
            JSONObject cur = Store.session(MainActivity.this);
            return cur != null && cur.optString("siteId").equals(siteId);
        }

        @JavascriptInterface public String info() {
            PowerManager pm = getSystemService(PowerManager.class);
            try {
                return new JSONObject()
                        .put("version", BuildConfigLite.version(MainActivity.this))
                        .put("build", Updater.build(MainActivity.this))
                        .put("background", Build.VERSION.SDK_INT < 29 || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        .put("battery", pm != null && pm.isIgnoringBatteryOptimizations(getPackageName()))
                        .put("locationOn", Fix.locationOn(MainActivity.this))
                        .toString();
            } catch (Exception e) { return "{}"; }
        }

        /** Saves a text file (a CSV report) in the phone's Downloads folder. Returns false if it could not be saved. */
        @JavascriptInterface public boolean saveFile(String name, String mime, String text) {
            try {
                String file = (name == null ? "" : name).replaceAll("[^A-Za-z0-9._-]", "_");
                if (file.isEmpty()) file = "attendance.csv";
                String type = (mime == null || mime.isEmpty()) ? "text/csv" : mime;
                byte[] data = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, file);
                    v.put(MediaStore.Downloads.MIME_TYPE, type);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u == null) return false;
                    try (OutputStream o = getContentResolver().openOutputStream(u)) {
                        if (o == null) return false;
                        o.write(data);
                    }
                    toast("Saved in Downloads: " + file);
                } else {
                    // Android 8 and 9 cannot write to Downloads without a storage permission: offer the text to share instead
                    final String shown = file;
                    final String body = text == null ? "" : text;
                    runOnUiThread(() -> {
                        try {
                            Intent send = new Intent(Intent.ACTION_SEND);
                            send.setType("text/plain");
                            send.putExtra(Intent.EXTRA_SUBJECT, shown);
                            send.putExtra(Intent.EXTRA_TEXT, body);
                            startActivity(Intent.createChooser(send, shown));
                        } catch (Exception ignored) {}
                    });
                }
                return true;
            } catch (Exception e) { return false; }
        }

        /** Saves a received chat file (document, photo, voice) in Downloads. data is base64. Android 10+ only; returns false otherwise. */
        @JavascriptInterface public boolean saveBlob(String name, String mime, String b64) {
            try {
                if (Build.VERSION.SDK_INT < 29 || b64 == null || b64.isEmpty()) return false;
                String file = (name == null ? "" : name).replaceAll("[^A-Za-z0-9._-]", "_");
                if (file.isEmpty()) file = "amitek-file";
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, file);
                v.put(MediaStore.Downloads.MIME_TYPE, (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) return false;
                try (OutputStream o = getContentResolver().openOutputStream(u)) {
                    if (o == null) return false;
                    o.write(Base64.decode(b64, Base64.DEFAULT));
                }
                toast("Saved in Downloads: " + file);
                return true;
            } catch (Exception e) { return false; }
        }

        /** Prints a report page through the phone's print screen (which also has "Save as PDF"). */
        @JavascriptInterface public void printHtml(String html, String title, boolean landscape) {
            runOnUiThread(() -> {
                try {
                    final String job = (title == null || title.isEmpty()) ? "Attendance" : title;
                    final boolean[] started = {false};
                    WebView pv = new WebView(MainActivity.this);
                    pv.setWebViewClient(new WebViewClient() {
                        @Override public void onPageFinished(WebView v, String url) {
                            if (started[0]) return;
                            started[0] = true;
                            try {
                                PrintManager pm = (PrintManager) MainActivity.this.getSystemService(Context.PRINT_SERVICE);
                                PrintAttributes.MediaSize size = landscape ? PrintAttributes.MediaSize.ISO_A4.asLandscape() : PrintAttributes.MediaSize.ISO_A4;
                                pm.print(job, v.createPrintDocumentAdapter(job), new PrintAttributes.Builder().setMediaSize(size).build());
                            } catch (Exception ignored) {}
                        }
                    });
                    printView = pv;
                    pv.loadDataWithBaseURL(null, html == null ? "" : html, "text/html", "utf-8", null);
                } catch (Exception ignored) {}
            });
        }

        // ── push, sign-in for notification buttons, and the in-app store ──

        /** This phone's push address ("" until Firebase has made one; the page asks again a few times). */
        @JavascriptInterface public String pushToken() {
            String t = Store.fcmToken(MainActivity.this);
            if (t.isEmpty()) Push.refreshToken(MainActivity.this);
            return t;
        }

        /** The signed-in person (json: empId, token, scriptUrl, secret), so notification buttons can answer; "" signs the phone out. */
        @JavascriptInterface public void setAuth(String json) {
            JSONObject before = Store.auth(MainActivity.this);
            Store.auth(MainActivity.this, json);
            JSONObject after = Store.auth(MainActivity.this);
            String was = before == null ? "" : before.optString("empId").toUpperCase(), now = after == null ? "" : after.optString("empId").toUpperCase();
            if (!was.equals(now)) Push.clearAll(MainActivity.this);   // the old person's questions must not stay for the next one
            if (after != null) new Thread(() -> Push.register(MainActivity.this)).start();
        }

        /** A message was answered or read inside the app: its notification in the list goes too. */
        @JavascriptInterface public void clearNotice(String id) { Push.clear(MainActivity.this, id); }

        /** The screen a notification tap asked for, once ("" when none). */
        @JavascriptInterface public String takeRoute() { return Store.takeRoute(MainActivity.this); }

        /** What the App Store screen shows: build, latest, newer, forced, notes, size, state, pct, err, canInstall. */
        @JavascriptInterface public String updateInfo() { return Updater.info(MainActivity.this).toString(); }

        /** Looks for a newer app now; the answer arrives through AmkApp.onUpdate. */
        @JavascriptInterface public void updateCheck() {
            new Thread(() -> Updater.check(MainActivity.this)).start();
        }

        /** Downloads and installs the newer app. Returns ok, permission (Android must first allow installs from this app), none or busy. */
        @JavascriptInterface public String updateStart() { return Updater.start(MainActivity.this); }

        /** Opens this app's settings page (permissions, battery). */
        @JavascriptInterface public void openSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {}
            });
        }
    }
}
