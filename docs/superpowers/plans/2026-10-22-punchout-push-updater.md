# Forgotten punch-out, phone push, in-app update: implementation plan

> For agentic workers: execute inline, task by task, tests first. Spec: `docs/superpowers/specs/2026-10-22-punchout-push-updater-design.md`.

**Goal:** detect a forgotten punch-out and fix it in one tap; deliver reports and reminders as phone notifications; let the installed app update itself.

**Tech:** Cloud Functions (Apps Script logic on the bridge, memory-driver tests, `npm test` in `functions/`), single-file page `index.html` + `notify.js` (Playwright e2e in the scratchpad harness), Android Java (Gradle 8.x, AGP 8.5.2, compileSdk 34, built on GitHub Actions and checked locally).

## Global constraints

- Everything stays compatible with APK build 6 and the current page: new server actions are additive, new bridge calls are feature-checked (`typeof AndroidTracker.x === 'function'`).
- No mail for reports once `reportVia = push`, except `mailOnly` (codes, security, test), `both` (emergency) and addresses with no Employee.
- Auto punch-out is always tagged (`poutAuto` = `auto` or `confirmed`) and can be switched off (`leftAuto = 0`).
- Deploy of functions only after the owner says go; APK published only after page + server are live.

## Review focus

1. A person on a meeting, personal work, site work, Field or WFH day must never be prompted or auto-closed.
2. A person who answers "Still working" must not be auto-closed that day.
3. A repeated tick or a second ping must not create a second prompt or a second report notice.
4. Punch-out at `ref` must not be earlier than the punch-in, and a running work item must be closed first.
5. An address that is not an Employee must still get its mail.

---

### Task S1: reports and alerts to the notification panel (functions)

**Files:** `src/backend.gs` (sendMail, notify, schema Notices `state`, remindersTick weekly personal notice, push fields), `src/runtime.js` (makePusher data), `test/reportpush.test.js`.

**Interfaces:** `sendMail(to, subject, body, opts)` with `opts = {mailOnly, both, alert}`; `notify(have, emp, kind, id, title, body, alarm, ref)` unchanged; `PushApp.send` gets `{channel, route, actions}`.

- [ ] Tests: (a) with default settings a report mail to an address that matches an Employee creates a `report` notice, pushes to that employee's device and sends no mail; (b) an address with no Employee still gets the mail; (c) `reportVia = both` does both, `mail` only mails; (d) OTP mail with `mailOnly` is always mailed; (e) emergency with `both` mails and pushes; (f) the same report twice in a tick is one notice; (g) Monday personal weekly notice per employee, once.
- [ ] Implement; map each existing `sendMail` caller to opts (OTP 2463, email-changed 1854, test 1696 → `mailOnly`; SOS and escalations → `both` + `alert`; away, site, battery, overtime, plan → `alert`; others plain).
- [ ] `makePusher`: derive `channel` (`alarm` if alarm, `reports` for report/alert, else `reminders`), `route`, `actions` from kind; keep old fields.
- [ ] `npm test` green; commit.

### Task S2: forgotten punch-out (functions)

**Files:** `src/backend.gs` (logSitePing hook `leftCheck`, actions `confirmLeft` `stillWorking`, `autoPunchOut`, `leftSweep` called from remindersTick, schema Attendance `poutAuto`, settings), `test/leftoffice.test.js`.

**Interfaces:** actions `confirmLeft {empId,date}` → `{ok, time}`, `stillWorking {empId,date}` → `{ok}`; both in PUBLIC and EMP_FIELD. Settings `leftPrompt`(1) `leftFromAt`(04:00 PM) `leftDist`(300) `leftAuto`(1) `leftCloseAfter`(30 min after trackEnd).

- [ ] Tests for the five review-focus items plus: prompt only after `leftFromAt`; needs 90 min dwell; confirm punches out at `ref` with `poutAuto=confirmed` and a running task closed with the remark; hold blocks auto; sweep auto-closes with `poutAuto=auto` only with two outside pings; no evidence → untouched but counted in the admin notice; admin "N people have not punched out" once.
- [ ] Implement; `npm test` green; commit.

### Task P1: page changes for push and punch-out

**Files:** `index.html`, `notify.js`, e2e `e2e_leftoffice.js`.

- [ ] `notify.js`: native path (`AndroidTracker.pushToken`) registers the token; `leftOffice` item renders two buttons (`confirmLeft` / `stillWorking`); report/alert text kept with line breaks; `route` open handler (`AmkApp.route('out'|'tasks'|'notices'|'today')`).
- [ ] "Auto" tag: home card, My month, admin attendance table and employee detail show `poutAuto`.
- [ ] Admin settings: `reportVia`, `leftFromAt`, `leftDist`, `leftAuto`.
- [ ] `nativeStart` passes `eveMin` (from `leftFromAt`) and `eveGap` 15.
- [ ] e2e: prompt notice shows buttons, Yes punches out and shows "Auto"/"confirmed"; route `out` opens the Work OFF flow; page without the new bridge calls still works.

### Task A1: Android app

**Files:** `android/**` (moved from the workflow), `.github/workflows/build-android-app.yml`, new `Push.java`, `AmkMessagingService.java`, `NoticeActionReceiver.java`, `Updater.java`, `UpdateLogic.java`, `InstallResultReceiver.java`, edits to `MainActivity`, `TrackerService`, `Store`, `AndroidManifest.xml`, `app/build.gradle`, JUnit `UpdateLogicTest`.

**Interfaces (bridge):** `pushToken(): String`, `setAuth(json): void` (`empId`, `token`, `scriptUrl`, `secret`), `updateInfo(): String` (`{current, latest, notes, size, minBuild, canInstall, state}`), `updateCheck(): void`, `updateStart(): void`, `takeRoute(): String`; page callbacks `AmkApp.onUpdate(json)`, `AmkApp.route(name)`.

- [ ] Move the source to `android/`, workflow builds from it, triggers on push to `android/**`, default `app_url`; local `gradle assembleRelease` works with a throwaway key.
- [ ] `UpdateLogic` (pure): parse tag `app-v<N>.<M>`, compare with versionCode, pick asset, `minBuild` rule; JUnit.
- [ ] Push service, channels, rich notification, tap routing, `leftOffice` buttons posting `confirmLeft` / `stillWorking`; token refresh re-registers.
- [ ] Updater: check (start, resume hourly, every 6 h from the tracker), download with progress, `PackageInstaller` session, permission prompt, `updates` notification.
- [ ] Evening cadence in `TrackerService.runCheck` using `eveMin` / `eveGap`.
- [ ] Local build passes; commit.

### Task P2: App Store screen and one-time install card

**Files:** `index.html`, e2e `e2e_appstore.js` with a stubbed `AndroidTracker`.

- [ ] Me → "App Store" sheet; update card on Today when `latest > current`; `minBuild` cover that cannot be dismissed; progress from `AmkApp.onUpdate`.
- [ ] Old app (bridge has no `updateInfo`): one-time "Install the new app" card with the release link and four short steps; hidden once the new bridge exists.
- [ ] e2e: new app shows versions and starts the update; old app shows the install card; web browser shows neither.

### Task G: gates and release

- [ ] All suites green (`npm test`, page e2e one at a time).
- [ ] Ask the owner for the go-ahead; copy `functions` to the PC, deploy, check logs; release the page (`tools/release.py`), verify live.
- [ ] Push the `android/` change, wait for the Release, check the asset and its build number through the releases API, send the owner the one-time link and the shortest install steps.
