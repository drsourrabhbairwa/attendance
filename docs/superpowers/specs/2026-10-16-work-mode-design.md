# Work Mode — one clean block for Field, Office and Work From Home

Status: draft for review · 2026-10-16 · branch `work-mode`

## Why
In a small Indian company one person does many jobs in a day: a bit of factory work, a customer visit, some calling from home or the office. Today the Punch screen has separate, overlapping buttons (Going Out for Meeting, Urgent personal work, Customer Visit, Start Site Work) and the employee has to know which one fits. Admins cannot tell what a person was actually doing. We replace those with **one block** that always shows only what the person can do next, and that records what they did, for whom, and for how long.

## Success
- An employee is never shown more than one primary action row. After Punch In: choose a mode. After that: only the buttons for the current state.
- A person can change mode during the day, with one tap, whenever nothing is running.
- The admin sees, per person: mode, purpose or task, client name and number, since when, Active or Paused. Daily and weekly reports (CSV and PDF) split time into Field / Office / WFH and by purpose.
- No existing Attendance, Leads, Travel, Quotation or Site report breaks.

## Not in scope
Coins, gig board, new geo-fence enforcement (we only *tag* places), Support ticket system (does not exist yet), face recognition.

## Employee flow (the only things shown, by state)

| State | What is on screen |
|---|---|
| Not punched in | Punch In (as today, with photo/GPS) |
| Punched in, nothing running | Three big cards: **Field Work · बाहर का काम**, **Office Work · ऑफिस**, **Work From Home · घर से**. Small "More" link: Urgent personal work, Apply Leave, Sign out |
| Mode chosen, nothing running | Only that mode's chips (purpose for Field, task type for Office/WFH). A "Change mode" link |
| Running | Timer, client name, **Pause**, **Stop** |
| Paused | Paused timer, **Resume**, **Stop** |
| Stopping | Feedback form. It cannot be skipped |
| Always (under the block) | Today's timeline: each visit/task, its time and result, and the day's split |

Labels are English with a short Hindi hint on the three mode cards only. Every chip/text field wraps inside its box (no overflow), tested at 320 px width.

### Field Work
Purposes: Sales Meeting, Site Supervision, Complaint, Payment Collection, Installation, Brand Building, Material Delivery, Other (the text is required).
- This **is the existing Customer Meeting flow, rebuilt**: it keeps the `Meetings` sheet, the `Customers` (leads) pipeline, the "on the way → start → end" steps, the 150 m arrival prompt and the quotation link.
- Purpose replaces the old reason list (`FM_REASONS`).
- Client name + phone + address are required (pick an existing lead or add one). GPS is taken at start and at stop.
- **Pause / Resume** on a running meeting: a pause list `{from,to}`; active minutes = elapsed − pauses.
- Stop needs feedback. For **Sales Meeting** the existing result (Interested / Quotation asked / Order / Follow-up / Not interested) is required and still moves the lead's stage. For every other purpose a one-line outcome is required.
- **Site Supervision** starts the existing Site Work tracking (location pings, alerts) and stops it on Stop. The separate "Start Site Work" button is removed.
- **Complaint:** there is no support-ticket feature in the app yet, so for now the complaint is saved as the client + a required "what is the issue" line, in a form that a future Support system can pick up.
- The travel step ("on the way") stays: choosing Field starts it, so travel time and the arrival prompt keep working.

### Office Work and Work From Home
Tasks: Client Calling (client name, number, result), Accounts Setting, Accounts Settling, Task Stack Settling, Training, Client Quotation Creation, Client Presentation Making, Client Quotation Creation (client name, number), Demo to a client who came to the office (client name, number), Other (text required).
- New collection `WorkLogs` (one row per task): `empId, date, mode, kind, kindOther, clientName, clientPhone, customerId?, start, end, pauses[], status (Active|Paused|Done), outcome, place, placeTag`.
- Client Calling and Demo link to an existing lead or add one (same picker as Field).
- Start/Stop, Pause/Resume, short remarks required on Stop.
- No GPS requirement. Optional single location stamp so the admin can see "At Factory" or "At Office".

### Many jobs in one day
- Mode can be changed at any time **while nothing is running**; a running item must be stopped (or paused) first.
- If the phone is inside one of the person's assigned Places (office or factory), the matching mode card is highlighted as a suggestion and the place name is shown as a tag ("At Factory"). It is a hint, never a block. Hard geo-fence rules remain a separate roadmap item.
- The day's mode time is the sum of Active time of each item, grouped by mode. Time between items is "unlogged" and is shown as such.

### Hours shown to the employee
The home block shows **Total working hours** (Work ON until now, or until Work OFF) and **Productive working hours** (Active time of all meetings and tasks; pauses, travel and unlogged time do not count). The old "in office so far" caption is removed.

## Data
- `Meetings`: add `purpose`, `purposeOther`, `outcome`, `pauses` (JSON), `pausedAt`, `activeMin`. Existing `reason` is kept and filled from `purpose`, so old reports and the Leads tab still read it.
- `WorkLogs`: new, shape above.
- `Attendance` row: add `workMode` (last mode of the day). Optional for old rows.
- Old Customer Visit rows (`Visits`) stay and still show on the route map. The Customer Visit button goes away.
- Backend actions (all through the existing auth, `EMP_FIELD` forcing `empId`): `workStart`, `workPause`, `workResume`, `workStop`, `workToday`, `workLive`, `workReport`; plus `pauseMeeting` / `resumeMeeting` and purpose fields on the existing meeting actions.

## Admin
- **Live view** (reuses the Attendance/Travel tab area, no new top-level tab): one row per person who is punched in: name, mode, purpose/task, client name + phone (tap to call), since, Active/Paused, place tag.
- **Reports:** daily and weekly, by mode and by purpose, per person Field / Office / WFH split, CSV and PDF. Added inside the existing Reports tab.
- **Filters before download:** the admin can pick several employees, several modes, several purposes/tasks, status and a client name/phone search, plus a date range (Today, Last 7 days or custom). CSV and PDF contain exactly the filtered rows and state the filters used.
- **Visibility:** the existing scope helper (`inSet`) decides: Super Admin sees all, Dept Admin their department, Supervisor their own staff.

## UI rules
Glass theme (same tokens as Tasks), line icons, 44 px minimum touch targets, wrapping text, no horizontal scroll.

## Delivery in four steps, each ends with your go-ahead before any push to `main`
1. Backend + tests (`WorkLogs`, pause/resume on Meetings, live and report actions, scope).
2. Employee block (Punch → mode → purpose/task → run/pause/stop → timeline), replaces old buttons.
3. Admin live view.
4. Reports (CSV + PDF).
Each step is tested in the browser harness plus the server tests, then deployed with `firebase_deploy` and released only after your OK.

## Assumptions (shout if wrong)
1. The travel step stays for Field.
2. Office/WFH need no GPS.
3. Old Customer Visit data stays as is.
4. Hindi hints appear only on the three mode cards.
5. "Urgent personal work" stays, moved into "More".
