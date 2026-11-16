# Meeting audio recording (phase 1) — short design

Date: 2026-11-16. Status: scope chosen by the user ("only the audio recording part: button + admin request + consent + save").

## Intent and why
Staff meet clients and nobody keeps minutes. The user wants a Record button in the app that staff (and, by request, admins) can use during a meeting or visit, so the audio is saved and minutes can be made from it later. Phase 2 (speech-to-text and written minutes) is out of scope.

## Goals
- Employee taps Record on a running meeting / client visit, taps Stop, the audio is saved and linked to that day's attendance record.
- Admin or supervisor can send a "please record this meeting" request; the employee sees it and accepts with one tap.
- A consent screen the first time (what is recorded, who can listen), and a visible red "Recording" bar the whole time.
- Admin can list and play recordings per person and date.

## Non-goals / anti-goals
- No secret or remote-started recording. Only the employee starts and stops it. An admin request never starts the microphone by itself.
- No speech-to-text, summary or minutes (phase 2).
- No recording while the phone is locked in phase 1 (needs an Android foreground service and a new APK); the app keeps the screen awake while recording and says so.

## Approaches considered
1. Record in the page with MediaRecorder (already used for task voice notes), upload to Cloud Storage. Picked: no APK change, reuses the existing microphone permission in the Android wrapper.
2. Native Android recorder with a foreground service. Works with the screen off but needs a new APK and a bridge; kept for a later step.
3. Admin-started remote recording. Rejected: covert surveillance, legal and trust risk.

## Design
- Frontend (`index.html`): Record button on the meeting/visit screen; consent sheet (stored as `recConsent` timestamp on the employee); red recording bar with timer and Stop; Wake Lock while recording; chunks kept in memory, uploaded on Stop (retry once, keep blob until upload succeeds); hard cap 90 minutes, 24 kbps opus (about 16 MB). Admin side: "Ask to record" button in Employee 360 / live board, and a Recordings list (person, date, length, play).
- Backend (needs approval to deploy): new actions `recStart` (creates the recording record and a signed upload target), `recSave`, `recList`, `recAsk` / `recAskAnswer`; a new Cloud Storage store with no auto-delete (like `idStore`), larger size limit than the 5 MB chat limit; recordings are linked by employee id and date. Only admins/supervisors of that person and the person can read. No existing data is changed.
- Consent line shown to the staff member, and a sentence they can read to the client: "We are recording this meeting for minutes."

## Constraints
- Do not change or delete live data; backend deploy only with the user's explicit "deploy karo".
- Old app versions must keep working (new actions are additive).

## Left to the builder
Exact UI placement, copy, and error messages; chunked upload details.
