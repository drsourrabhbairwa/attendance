# Notifications, message center, reminders, app update: design

Date: 2026-10-15. Status: draft for review (nothing built yet).

## Goal

Staff should not miss a punch or a location request: they get a reminder with sound, inside the app and as a push when the app is closed. The Android app can update itself with a notification. Works on the current UI and on the new glass theme.

## What exists today (checked in code)

- Reminders go by e-mail only (`remindersTick`): punch-in mail at the office "late" time, punch-out mail at `remindOutAt` (07:30 PM).
- A location request from admin or supervisor is a "check". The employee app finds it by polling `pollEmp` every ~60 s while the app is open, then shows a modal and plays `beep(4)`. A closed app never hears it.
- No service worker, no push, no message center, no update check.
- Android app (WebView, `com.amitek.attendance`, `versionName 1.0.<build>`) has a foreground location service for site work, boot receiver, and already declares `POST_NOTIFICATIONS` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. No Firebase Messaging, no installer permission.
- There is no shift field on an employee. `officeFor(emp)` gives office start and end.

## Decisions taken with the owner

- Location sound means: when admin or supervisor asks an employee for location, the employee's phone plays an alarm-style sound that repeats until answered.
- Two phases: A first (server, web, message center), then B (Android app).

## Phase A: server, web, message center

### Data

New collection `notices` (one document per notice), written by the server only:

`{ id, empId, kind: 'punchIn' | 'punchOut' | 'locationCheck' | 'info', title, body, createdAtMs, date, readAtMs?, ref? }`

`ref` carries the check id for `locationCheck`. A notice is created once per employee, kind, and day (id = `kind|empId|date`), so a repeated tick never duplicates it.

### Server (inside the v24 logic, as new actions plus extra steps in `remindersTick`)

1. Settings `remindInAt` (default `10:00 AM`) and `remindOutAt` (existing, `07:30 PM`). Editable in the admin settings screen.
2. In `remindersTick`, at or after `remindInAt` (window of 3 hours): for each employee with a day office (start before 12:00 and end after 16:00), not on leave, not on a holiday or Sunday, with no punch-in today, and with reminders on for them: create the `punchIn` notice and send a push. At or after `remindOutAt` (window of 4 hours): the same for employees who punched in and have not punched out: `punchOut`.
3. When a location check is created for an employee, create a `locationCheck` notice and send a push marked as an alarm.
4. New actions: `getNotices` (own notices, newest 50), `markNoticesRead`, `registerPushToken` (stores the device token on the employee, up to 3 tokens, drops tokens the push service reports as dead).
5. A per-employee switch "Send me reminders" (default on) and an admin switch per employee. Existing e-mail reminders stay as they are.

Push is sent with Firebase Cloud Messaging through the Firebase Admin SDK from the Cloud Function. It is free at this size.

### Web app (one new file `notify.js` plus one script tag in `index.html`)

- Bell icon with an unread count and a message center sheet listing notices; tapping one marks it read.
- When a new notice arrives (picked up on the existing `pollEmp` response, so no extra requests): popup and a sound. Reminders use a short two-tone chime; location checks use a louder alarm that repeats every few seconds until the employee answers or taps "Later".
- Web push: `firebase-messaging-sw.js` at the site root, permission asked after sign-in with a one-line reason. On iPhone, push works only when the page is added to the Home Screen (iOS 16.4 or later); the app says so when it detects iPhone in a browser tab.
- The module reads the theme through CSS variables with plain fallbacks and builds its own markup, so it does not depend on either UI. The glass-theme work only needs to place the bell in its header and use the same two functions: `AmkNotify.mount(container)` and `AmkNotify.onPoll(data)`.
- Page update banner: the page checks `version.json` on focus and shows "New version, tap to reload". This covers GitHub Pages caching.

### Phase A checks

- Server: unit tests on the memory driver for the 10:00 and 7:30 rules (skips leave, holiday, Sunday, already punched, night office, duplicate tick), notice ids, token registration.
- Web: browser test that a notice from `pollEmp` opens the popup, rings, appears in the message center, and marks read.
- Live: deploy functions, then one real push to the owner's phone before staff see anything.

## Phase B: Android app (new APK)

- Firebase Messaging service: high-priority data push. Channel `alarm` (sound = alarm tone, bypasses silent where Android allows, full-screen intent for location checks) and channel `reminders` (default sound).
- Token goes to `registerPushToken` after sign-in.
- Background behaviour is the normal one for any push app: FCM high-priority push reaches the phone even when the app is closed. No extra "keep alive" screen, no permanent notification. (Owner decision 2026-10-15: keep this like a typical app's notifications.)
- Update: on app start and every 6 hours the app reads `version.json` (`latestBuild`, `apkUrl`, `notes`, `minBuild`). If newer, it shows a notification and an in-app card. "Update" downloads the APK to the app's own storage and opens the system installer. Needs `REQUEST_INSTALL_PACKAGES`; the first time Android asks the staff member to allow installs from this app. `minBuild` forces the card to stay until updated.
- The APK file is hosted on GitHub Releases (free).
- Each staff member installs this first new APK by hand once. After that updates come from inside the app.

## Limits stated plainly

- Push delivery is as reliable as in any normal app. On some phones (Xiaomi, Oppo, Vivo) aggressive battery saving can still delay it; if staff report that, a one-time settings guide can be added later.
- Silent updates need Play Store or a company-managed phone. Here, staff tap "Install" once per update.
- iPhone has no APK; it gets web push only from a Home Screen install.

## Out of scope

SMS or WhatsApp reminders, per-department reminder times, snooze rules for reminders, Play Store release.

## Risks

- `index.html` is shared with the glass redesign (working copy not committed). Phase A touches `index.html` only for one script tag, the bell mount, and the `pollEmp` hook, so merging is small. Apply it to whichever file is live at that time.
- New server actions go through the v24 verbatim logic. They are added as a small separate block with their own tests, and the existing 11 suites plus `npm test` must stay green.
- FCM needs the Firebase web config (public values) and a Web Push certificate key pair from the console, which the owner creates in a few clicks.
