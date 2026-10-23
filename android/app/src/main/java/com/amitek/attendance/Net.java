package com.amitek.attendance;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Posts to the Google Apps Script web app (which answers a POST with a redirect to the result). */
final class Net {
    private Net() {}

    /** The server understood the request and refused it (wrong secret etc.): no point retrying. */
    static final class Rejected extends Exception {
        Rejected(String m) { super(m); }
    }

    /** The server refused because the private sign-in is missing, ended or too old: keep the check and ask the person to open the app. */
    static boolean signInNeeded(Rejected r) {
        String m = r.getMessage() == null ? "" : r.getMessage();
        return m.startsWith("Please update") || m.startsWith("Session expired");
    }

    static JSONObject post(String url, JSONObject body) throws IOException, Rejected {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "text/plain;charset=utf-8");
        try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = c.getResponseCode();
        String text;
        if (code >= 300 && code < 400) {
            String next = c.getHeaderField("Location");
            c.disconnect();
            if (next == null) throw new IOException("redirect without location");
            HttpURLConnection g = (HttpURLConnection) new URL(next).openConnection();
            g.setConnectTimeout(15000);
            g.setReadTimeout(30000);
            g.setInstanceFollowRedirects(true);
            code = g.getResponseCode();
            text = read(code < 400 ? g.getInputStream() : g.getErrorStream());
            g.disconnect();
        } else {
            text = read(code < 400 ? c.getInputStream() : c.getErrorStream());
            c.disconnect();
        }
        if (code != 200) throw new IOException("HTTP " + code);
        JSONObject j;
        try { j = new JSONObject(text); } catch (Exception e) { throw new IOException("bad reply"); }
        if (!j.optBoolean("ok")) throw new Rejected(j.optString("error", "rejected"));
        return j;
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        }
    }
}
