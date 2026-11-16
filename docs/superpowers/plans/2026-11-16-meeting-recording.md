# Meeting Audio Recording (phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Staff can record a meeting/visit in the app (with consent), admins can ask for a recording and listen later.

**Architecture:** The phone records with MediaRecorder in 15-minute parts; each part is uploaded as soon as it ends (crash-safe) to a private, never-purged Cloud Storage store, mirroring the existing `labIdPut/labIdGet` pattern (bridge moves the bytes, `backend.gs` only decides who may). Metadata lives in a new `Recordings` tab. "Ask to record" is a normal notice to the employee; nothing starts the mic except the employee.

**Tech Stack:** `index.html` (vanilla JS, MediaRecorder, Wake Lock), `backend.gs` + `gasBridge.js` + `runtime.js` (Cloud Functions), node:test.

**Spec:** `docs/superpowers/specs/2026-11-16-meeting-recording-design.md`

## Global Constraints
- Do not change or delete any live data; backend deploy only on the user's explicit "deploy karo". Frontend release is held until the backend is live.
- Recording starts only from the employee's own tap. Red "Recording" bar visible the whole time. Max 90 minutes per recording; parts of 15 minutes at 24 kbps opus.
- Old app versions keep working (new actions are additive).
- Frontend release flow: `python3 tools/release.py "<notes>"`, `git add -A`, separate commit with the Co-Authored-By and Claude-Session trailers, separate `git push origin main`.

## Review Focus
- Upload fails mid-recording (offline): part is kept in memory and retried; Stop never loses audio silently.
- Microphone denied / in use by a call: clear message, no red bar left on screen.
- Phone locked or app backgrounded: warn once at start that the screen must stay on in this version; Wake Lock re-requested on return.
- Employee not consented yet / not in a meeting: Record button refuses with a reason.
- Admin asks a resigned or other-department person: refused by permission check.
- A non-admin tries `recGet` for someone else's recording: refused.

---
### Task 1: Backend — Recordings tab, actions, store (`backend.gs`, `gasBridge.js`, `runtime.js`, `shared/sheetMap.js`, `legacyRows.js`, `engine.js`)
**Files:** Modify those six; Test: `test/recordings.test.js`
**Interfaces — Produces:**
- Tab `Recordings`: `['id','empId','name','date','meetingSeq','startedAt','parts','dur','consentAt','status']` (`parts` is JSON `[{n,size,dur}]`, `dur` total seconds, `status` `'open'|'done'`), registered in all four tab maps.
- `recStart {empId, date, meetingSeq}` → `{id}` (employee only; needs `trackConsent`-style `recConsent` accepted via `recConsent {empId}` → `{consentAt}`).
- `recCheck {op:'put'|'get', id, n, size}` → `{key, mime}` (put: owner only, size ≤ 6 MB, n ≤ 6; get: owner or admin/dept-admin/supervisor of that person).
- `recFinish {id, parts:[{n,size,dur}]}` → `{ok}` sets `status:'done'`, `dur`.
- `recList {empId?, date?}` → rows for admins (their scope) or own rows for staff.
- `recAsk {empId, note}` → notice `kind:'rec'` to that employee, admin/supervisor in scope only.
- Bridge: `recPut`/`recGet` handled like `labId`, with `opts.recStore` and path `recordings/<key>`; `runtime.js` adds `recStore: makeMediaStore(bucket, Infinity, prefix)`.
- [ ] Step 1: In `test/recordings.test.js` (MemoryDriver + GasBridge as in `test/bridge.test.js`): test names `employee can start, put and finish a recording`, `another employee cannot get it`, `admin can list and get it`, `recAsk makes a notice for the employee only`, `put over 6 MB or part 7 is refused`, `recStart refused without consent`. Run `node --test test/recordings.test.js` → FAIL.
- [ ] Step 2: Implement the actions and the bridge `recPut/recGet`; register the tab. Run the new test and the existing four test files → all PASS.
- [ ] Step 3: Copy to the PC with `device_commit_files` (`expectedMtimeMs`), do not deploy. Commit the repo-side copy if any.

### Task 2: Phone — consent, Record bar, upload (`index.html`)
**Files:** Modify `index.html` (new `rec*` functions next to `tkRecStart`; CSS in `<style id="proUI">` block end).
**Interfaces — Consumes:** Task 1 actions. **Produces:** `recOpen(date, seq)` (opens consent or recorder), `recStartNow()`, `recStop()`, `recBar(on)`, `window._rec` state.
- [ ] Step 1: Add `recOpen`: if no `recConsent` on the employee record, show a sheet (text: what is recorded, who can listen, "tell the client") with Accept → `recConsent`; then `recStartNow`.
- [ ] Step 2: `recStartNow`: `getUserMedia({audio:true})`, `MediaRecorder` 24 kbps, request `navigator.wakeLock` (re-request on `visibilitychange`), `recStart` then red bar with timer; every 15 min stop/restart the recorder and upload the finished part with `recPut` (base64 `data:` body as `taskMediaPut` does); failed uploads stay queued and retry every 20 s.
- [ ] Step 3: `recStop`: stop, upload last part, wait for the queue, `recFinish`, toast "Saved". Hard stop at 90 min.
- [ ] Step 4: Verify: parse inline scripts with `node` + `vm.Script`; run headless Chrome with `--use-fake-device-for-media-stream --use-fake-ui-for-media-stream` against a stub `api()` and assert the 2 uploaded parts and the finish call.
- [ ] Step 5: Commit (no release yet).

### Task 3: Entry points and admin screens (`index.html`)
- [ ] Step 1: Add a "🎙 Record" button on the running meeting / customer-visit screen, shown only when the server supports `recStart` (probe once with a harmless `recList`; hide on "Unknown action").
- [ ] Step 2: Notice `kind:'rec'` taps open `recOpen`. Admin: "🎙 Ask to record" button in Employee 360 → `recAsk`.
- [ ] Step 3: Admin "Recordings" list (person, date, length) in Employee 360 with an `<audio controls>` player that fetches parts with `recGet` and plays them in order.
- [ ] Step 4: Render check with headless Chrome screenshots of the sheet, the red bar and the list; fix what looks off.
- [ ] Step 5: Commit.

### Task 4: Ship (needs the user's approval)
- [ ] Step 1: Ask "deploy karo". Deploy functions, check status.
- [ ] Step 2: Release the frontend with the flow above; poll `version.json`.
- [ ] Step 3: Ask the user to try one 1-minute recording from a phone and listen to it in Employee 360.
