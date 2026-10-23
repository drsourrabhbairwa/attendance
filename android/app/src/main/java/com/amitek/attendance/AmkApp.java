package com.amitek.attendance;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

/** Starts Firebase Cloud Messaging (push) and creates the notification channels before anything else runs. */
public class AmkApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            // The app is built without google-services.json, so the project's public client settings are given here
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this, new FirebaseOptions.Builder()
                        .setApplicationId("1:572781418868:android:0936ad5b8beaf912def421")
                        .setApiKey("AIzaSyBsMpw4GwC3nWIli69LkEmzCeYbEYxJPVI")
                        .setProjectId("amitek-attendance-59ccc")
                        .setGcmSenderId("572781418868")
                        .setStorageBucket("amitek-attendance-59ccc.firebasestorage.app")
                        .build());
            }
        } catch (Exception ignored) {
            // push stays off; the rest of the app is not affected
        }
        try { Push.channels(this); } catch (Exception ignored) {}
    }
}
