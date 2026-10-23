package com.amitek.attendance;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** Receives push messages from the office, also while the app is closed. */
public class AmkMessagingService extends FirebaseMessagingService {
    @Override public void onMessageReceived(RemoteMessage m) {
        Push.handle(this, m.getData());
    }

    /** Firebase changed this phone's push address: remember it and tell the office. */
    @Override public void onNewToken(String token) {
        Store.fcmToken(this, token);
        Push.register(this);   // already on a background thread
    }
}
