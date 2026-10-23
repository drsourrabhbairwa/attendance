package com.amitek.attendance;

import android.content.Context;

/** App version without depending on the generated BuildConfig class. */
final class BuildConfigLite {
    private BuildConfigLite() {}
    static String version(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Exception e) { return "1"; }
    }
}
