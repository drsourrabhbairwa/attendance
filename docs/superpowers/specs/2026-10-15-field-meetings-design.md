# Field meetings flow — design

Status: draft for the owner's review. Nothing here is built yet.

## Goal
A field sales employee runs a day of 4–5 customer meetings from one screen, in about 10 seconds per step, one-handed, with almost no typing. Admin sees each person's day as one timeline with customer, result and km. Punch Out opens only after "Back".

## Today (what exists)
- `Meetings` rows: `seq, outTime/Photo/Lat/Lng/Area, note, backTime/…, kind` (`saveMeetingOut`, `saveMeetingBack`). "Out" = left for a meeting, "Back" = returned. `kind: personal` is non-work absence.
- `Visits` rows: `customer, note, photo, lat/lng` (`saveVisit`), one-shot.
- No customer record, no result, no follow-up, no start/end *at* the customer.

## The day flow (employee)
1. **Punch In.**
2. **Leave** (1 tap): time + GPS recorded. Meeting #n created, state `travelling`.
3. **Customer**: search the shared list (name or phone) → name/phone/address fill in. New customer: name + phone, address from GPS. **Reason** chip: Quotation · Demo · Site visit · Payment · Follow-up · Complaint · Other.
4. **Start meeting**: time + GPS (distance to the customer's saved address shown). Photo optional.
5. **End meeting**: **Result** chip is required: Interested · Quotation asked · Order · Follow-up · Not interested. Optional: next follow-up date, one-line note.
6. Back to step 2 for the next meeting, up to any number (typical 4–5).
7. **Back** (return to office/home): enables **Punch Out**, with a day summary (meetings, km, time in meetings vs travelling).
Rule: while a meeting is `travelling` or `in meeting`, Punch Out is hidden. The existing "Back" button stays the way to close the day's field work.

## Data
- `Customers` (new, shared by the team): `id, name, phone, address, lat, lng, createdBy, createdAt, lastMeetingAt, ownerEmpId`. Phone is the dedupe key; saving an existing phone reuses the record.
- `Meetings` (extended, old rows stay valid): add `customerId, customerName, phone, address, reason, startTime, startLat, startLng, startArea, endTime, result, followUpDate, state` (`travelling | in_meeting | done`). `outTime…` stays "left", `backTime…` stays "returned to base" for the final Back.
- Same Sheet-shaped path as today (`legacyRows`/`sheetMap`), so the Apps Script rollback keeps working.
- Offline: actions queue on the phone with a client id and replay (same idea as `saveVisit`'s `id`); every write is idempotent.

## Admin
- Per-employee day timeline: left → at customer (stay minutes) → result, per meeting, with map links.
- Customer page: all meetings with that customer, last result, next follow-up.
- Km travelled per day from consecutive GPS points (the "km travel report" and "employee travel report" are the same report, one with a per-employee and one with a per-day view).
- Follow-up list: "due today" for the employee and for the admin.

## Version-1 additions (approved by the owner)
1. **Arrival prompt.** When the phone is within 150 m of the customer's saved address while a meeting is `travelling`, show "Start meeting?" (and a notification if the app is in the background, using the live notification system). Needs the customer's `lat/lng`; without it the button stays manual. Start time is the tap time, with the arrival time kept as `arrivedAt`.
2. **Vertical and expected value.** End meeting also asks the vertical (Seamless Floorings · Waterproofing · Road Marking Paint · Home Automation & Security) and an expected value in rupees (optional). Columns: `vertical`, `expectedValue`. Admin gets pipeline value per employee/vertical against the sales target.
3. **Today's plan.** On Punch In the employee sees today's follow-ups (from `followUpDate`) and customers the admin planned, ordered by distance from the current location. Admin can add a planned customer for an employee. Column on `Customers`/`Meetings`: `plannedFor` date.
4. **Quotation link.** Result "Quotation asked" shows "Create quotation"; it opens the quotation tool with name, phone, address and vertical filled in. Needs the quotation tool's URL/parameters (open question: how it accepts prefilled data).

## Not in this step
Face recognition, bulk upload, announcements, support, file section, full geo-fencing (separate specs). Customer ownership/locking is not enforced; admin only sees who created and who visited.

## Review focus (inputs that will bite)
- Phone/GPS lost mid-meeting: reopening the app must resume the open meeting, never create a second one.
- Same customer entered twice with different spellings: phone dedupe, name typo tolerated.
- Result missing because the employee forgot: End meeting is blocked until chosen; the next day's open meeting is auto-closed `unknown` by the existing end-of-day job.
- 0 meetings day: Punch Out works as today.
- Old `Meetings` rows without the new columns still render in admin and reports.

## Build order (each is one daily push, glass theme)
1. Customers collection + list/search API.
2. Leave → Customer → Start → End flow on the employee screen (with vertical/value).
3. Arrival prompt.
4. Admin day timeline + customer page + pipeline value.
5. Today's plan and follow-up reminders (uses the notification system already live).
6. Quotation link.
7. Km report.

## Open decisions (defaults assumed, tell me if wrong)
- Customer list shared by the whole team (assumed yes).
- Quotation tool: how it takes prefilled data is not yet known (see item 4).
- Result required at End (assumed yes), note optional.
- Customer details entered at Leave/Start, result at End (assumed yes).
