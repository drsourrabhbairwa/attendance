# Forgotten punch-out, phone push, in-app app update

Date: 2026-10-22. Status: approved by the owner in chat the same day (two decisions below). Builds on `2026-10-15-notifications-design.md` (Phase A is live; this is its Phase B plus two additions).

## Why

1. Staff forget to punch out. The app already receives a location check every 45–75 minutes from every punched-in phone, so it can tell that someone has left the workplace.
2. Notifications never reach a closed app. The Android app is a WebView and a WebView cannot do web push. The server already sends FCM messages (`makePusher`, channels `alarm` / `reminders`); nothing on the phone receives them. The APK has no Firebase Messaging.
3. Every APK change needs a new file handed to every phone. The APK has no way to update itself.

## Decisions taken with the owner (2026-10-22)

- If a person has left and never answers the prompt: **auto punch-out, tagged "Auto"** (at the time they left).
- Reports and reminders that used to go by e-mail: **notification panel only, mail stops**. Kept as mail on purpose: sign-in codes and security notices (a code must arrive even with no app), emergency/SOS (a safety net), and addresses that belong to no employee (there is no panel to send to). Setting `reportVia` (`push` default, `both`, `mail`) lets the owner change it.
- The app update is a small "App Store" screen inside the app (this app only for now; the data format is a list so another app can be added later).

## 1. Forgotten punch-out

Server, on every `logSitePing` of an all-staff session, and again in `remindersTick`:

- **Candidate** = punched in today, no punch-out, ping is a real reading (not "no GPS", not fake) outside the workplace boundary by at least `leftDist` metres (default 300), at or after `leftFromAt` (default 04:00 PM) and not after closing + 5 min.
- **Dwell** = an inside ping exists at least 90 minutes before the first outside ping of the current outside run ("was there a long time, then suddenly far").
- **Not a candidate** when: out on a meeting, customer visit (2 h window), personal work, manual site work, day mode Field, or day mode WFH. (`awayExempt`, `onPersonalAway`.)
- On a candidate: one notice `left|<emp>|<date>` (kind `leftOffice`, `ref` = the time of the first outside ping, e.g. `06:42 PM`): "You seem to have left at 06:42 PM. Punch out at 06:42 PM?" Phone notification with two buttons: **Yes, punch out** and **Still working**.
- `confirmLeft {empId, date}`: finds that notice, punches the person out at `ref` with `poutAuto = 'confirmed'`, closes a running work item with the remark "Left the workplace", marks the notice read. `stillWorking {empId, date}`: marks the notice held; no auto-close that day, next prompt after 2 hours.
- **Auto-close** in `remindersTick` between `trackEnd + 30 min` and +4 h: for people still open, not held, with a `leftOffice` notice and two consecutive outside pings at the end: punch-out at `ref`, `poutAuto = 'auto'`, notice to the employee and to the people who got "away" alerts. People with no location evidence (no APK, phone off) are only counted.
- One notice to admins at the same time: "N people have not punched out" with names.
- A person's own later "Back at work" (`resumeWork`) keeps working as today.
- The Android tracker pings every 15 minutes after `leftFromAt` (session carries `eveMin`, `eveGap`), so a departure is seen within about 15 minutes. Browser-only staff and phones with GPS off have no location, so they get the 7:30 PM reminder only.

Shown as "Auto" next to the punch-out time on the home card, My month, and the admin attendance views.

## 2. Reports to the notification panel

- `sendMail(to, subject, body, opts)` keeps its signature. With `reportVia = push`, each address that matches an Employee (e-mail, case-insensitive) gets a notice (kind `report`, or `alert` for alerts and anything with `opts.alert`) and a push; addresses with no Employee are still mailed. `opts.mailOnly` (codes, security, test mail) always mails. `opts.both` (emergencies) always does both.
- Notice id `rep|<emp>|<date>|<hash of subject+body>`, so a repeated tick does not duplicate.
- New: a personal weekly notice on Monday for each employee ("Last week: present 5/6, late 1, average 8.4 h") from the existing `weekSummary`.
- Push data gets `channel` (`alarm`, `reminders`, `reports`), `route` (`today`, `out`, `tasks`, `notices`), and `actions` (`leftOffice`).

## 3. Android app (build 7 and later)

- Source moves from base64 inside the workflow into `android/` in the repo. The workflow builds on a push that touches `android/**` (and keeps manual run), signs with the same key, publishes a GitHub Release `app-v<run>.<attempt>` with `amitek-attendance.apk`.
- **Push:** Firebase Messaging started with explicit `FirebaseOptions` (no `google-services.json`). `AmkMessagingService` draws data-only messages as real notifications: channels `alarm`, `reminders`, `reports`, `updates`; big text; tap opens the app on `route`; `leftOffice` gets the two buttons, handled by a receiver that posts `confirmLeft` / `stillWorking` with the stored staff token, without opening the app. The page registers the token (`registerPushToken`, platform `android`); a refreshed token is re-registered natively.
- **Updater:** reads `https://api.github.com/repos/drsourrabhbairwa/attendance/releases/latest` (build = number in the tag, notes = release body) and `minBuild` from `version.json`. On app start, resume (at most hourly) and every 6 hours from the tracker. Download to the app's own folder, then a `PackageInstaller` session; the system confirm screen is the one tap Android requires. `REQUEST_INSTALL_PACKAGES` added; if "install unknown apps" is off, the app opens that settings page. A notification (`updates` channel) and a card appear when a newer build exists; below `minBuild` the card cannot be dismissed.
- **Page:** Me → "App Store" sheet (installed build, latest build, notes, Update, progress). Old APKs (no `updateInfo` on the bridge) show a one-time "Install the new app" card with the release link; the browser download plus install is the only manual step, once.
- Bridge additions: `pushToken()`, `setAuth(json)`, `updateInfo()`, `updateCheck()`, `updateStart()`, `takeRoute()`.

## Limits stated plainly

- Silent updates need Play Store or a company-managed phone: each update needs one "Update" tap and one Android confirm (newer phones may skip the confirm after the first update done by this app). The first install of build 7 is by hand.
- Play Protect may show an "unknown developer" warning on the first install.
- A phone that force-stops the app, or an aggressive battery saver, can delay push. The tracker's foreground service runs from punch-in, which helps.
- Detection needs the APK with location on. No location, no detection (reminder only).

## Out of scope

iPhone, a Play Store release, other apps in the store, SMS/WhatsApp, changing how punch-in works.

## Risks

- Backend and page must stay compatible with APK build 6 (already in staff hands): every new bridge call is feature-checked, every new server action is additive.
- Auto punch-out changes attendance records: it is only ever written for people with outside pings after the cut-off, always tagged, and `leftAuto = 0` turns it off.
