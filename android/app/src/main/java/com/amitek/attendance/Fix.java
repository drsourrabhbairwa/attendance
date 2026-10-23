package com.amitek.attendance;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Gets one fresh position: GPS first, mobile/Wi-Fi network as the fallback. Call off the main thread. */
final class Fix {
    private Fix() {}

    @SuppressLint("MissingPermission")
    static Location get(Context ctx, long timeoutMs) {
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        final Location[] best = {null};
        final Object lock = new Object();
        LocationListener l = new LocationListener() {
            @Override public void onLocationChanged(Location loc) {
                synchronized (lock) { if (better(loc, best[0])) best[0] = loc; }
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };
        HandlerThread ht = new HandlerThread("fix");
        ht.start();
        long t0 = SystemClock.elapsedRealtime();
        try {
            boolean any = false;
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                try {
                    if (lm.isProviderEnabled(p)) { lm.requestLocationUpdates(p, 0, 0, l, ht.getLooper()); any = true; }
                } catch (Exception ignored) {}
            }
            if (!any) return null;
            while (true) {
                SystemClock.sleep(1000);
                long gone = SystemClock.elapsedRealtime() - t0;
                synchronized (lock) {
                    Location b = best[0];
                    if (b != null && (b.getAccuracy() <= 25 || (gone > 20000 && b.getAccuracy() <= 100))) break;
                }
                if (gone >= timeoutMs) break;
            }
        } finally {
            try { lm.removeUpdates(l); } catch (Exception ignored) {}
            ht.quitSafely();
        }
        synchronized (lock) {
            if (best[0] != null) return best[0];
        }
        // nothing new: accept a reading from the last 2 minutes
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location last = lm.getLastKnownLocation(p);
                if (last != null && System.currentTimeMillis() - last.getTime() < 120000) return last;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static boolean better(Location a, Location b) {
        return b == null || a.getAccuracy() < b.getAccuracy();
    }

    /** True when the reading came from a fake-GPS (mock location) app. */
    @SuppressWarnings("deprecation")
    static boolean isFake(Location loc) {
        return Build.VERSION.SDK_INT >= 31 ? loc.isMock() : loc.isFromMockProvider();
    }

    static boolean locationOn(Context ctx) {
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    static double meters(double lat1, double lng1, double lat2, double lng2) {
        float[] r = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, r);
        return r[0];
    }

    /** "Sanganer, Jaipur" style name; coordinates if the phone can't look it up within 8 s. */
    @SuppressWarnings("deprecation")
    static String area(Context ctx, double lat, double lng) {
        String fallback = String.format(Locale.ENGLISH, "%.5f°, %.5f°", lat, lng);
        if (!Geocoder.isPresent()) return fallback;
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            Future<String> f = ex.submit(() -> {
                List<Address> list = new Geocoder(ctx, Locale.ENGLISH).getFromLocation(lat, lng, 1);
                if (list == null || list.isEmpty()) return fallback;
                Address a = list.get(0);
                String local = first(a.getSubLocality(), a.getThoroughfare(), a.getFeatureName());
                String city = first(a.getLocality(), a.getSubAdminArea());
                if (local == null) return city == null ? fallback : city;
                return city == null || city.equals(local) ? local : local + ", " + city;
            });
            return f.get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            return fallback;
        } finally {
            ex.shutdownNow();
        }
    }

    private static String first(String... s) {
        for (String x : s) if (x != null && !x.trim().isEmpty() && !x.matches("^[0-9\\s\\-/]+$")) return x.trim();
        return null;
    }
}
