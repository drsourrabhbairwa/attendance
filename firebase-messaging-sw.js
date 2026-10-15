/* Shows push messages when the app is closed. Messages are data-only, so we draw the notification ourselves. */
importScripts('firebase-config.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

if (self.AMK_FIREBASE && self.AMK_FIREBASE.apiKey) {
  firebase.initializeApp(self.AMK_FIREBASE);
  const messaging = firebase.messaging();
  messaging.onBackgroundMessage(payload => {
    const d = (payload && payload.data) || {};
    const alarm = d.alarm === '1';
    return self.registration.showNotification(d.title || 'Attendance', {
      body: d.body || '',
      tag: (d.kind || 'info') + '|' + (d.ref || ''),
      renotify: true,
      requireInteraction: alarm,
      vibrate: alarm ? [600, 200, 600, 200, 600, 200, 600] : [200, 100, 200],
      data: { kind: d.kind || '', ref: d.ref || '' }
    });
  });
}

self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
    for (const c of list) { if ('focus' in c) return c.focus(); }
    return self.clients.openWindow('./');
  }));
});
