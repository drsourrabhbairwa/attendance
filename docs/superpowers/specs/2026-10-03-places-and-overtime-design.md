# Places (office / factory boundaries) and Overtime — design

Approved by the owner on 2026-10-03. Backend: Apps Script v19. Front end: `index.html` (no APK rebuild needed).

## 1. Places

- **Super Admin / Admin** add any number of offices and factories: name, type, latitude/longitude (map tap, "My location" or typed), boundary radius (50–2000 m, default 100 m), **Strict** switch, Active switch.
- Staff are assigned per place (place → Staff, with "add whole department") or per person (📍 on the staff row). A person may belong to several places; being inside any one is fine. **Staff with no place are never checked** (sales, site technicians).
- Department admins can assign staff inside their scope but cannot create/edit/delete places. Supervisors do not see the tab.
- **Punch in / out** (checked on the phone *and* again on the server):
  - inside → normal
  - outside, Strict **off** → allowed, flagged, admin/supervisor emailed (once per 120 min)
  - Strict **on** → refused when clearly outside, when GPS is too weak to tell, or when there is no GPS (no "network problem" excuse; the escape route is the existing "Fix punch" request)
  - exempt: meetings, customer visits, site work, personal-away
- **All-day tracking** is measured against the place centre/radius. Two consecutive "OUT" checks → one email per time away.
- **Honesty score**: punched in/out clearly outside −5 each; away episode (2 checks in a row) −5, max 3 per day; weak GPS is never penalised.
- Verdict rule (client = server): `YES` if distance ≤ radius; `NO` if distance − accuracy > radius; otherwise `ROUGH`; no coordinates → `NO GPS`. The nearest place by (distance − radius) is used.
- Honest limits: phone GPS is 10–50 m accurate indoors → use ≥100 m for a factory; a web page cannot read the Wi-Fi network, so the boundary is GPS-only.

## 2. Overtime (separate section)

- Employee taps **Start overtime** (photo + GPS + reason: Urgent client work / Site work / Delivery pending / Other) and **Stop overtime** (photo + GPS). The tile appears only after the normal punch-out and after office end time (setting `officeEnd`, default 06:30 PM), or on Sunday/holiday. "My overtime" lists requests with status pending / approved / rejected.
- Admin / supervisor get an **Overtime** tab (scoped by role): pending requests with hours, reason, both photos and map pin. **Approve** (hours adjustable) or **Reject** — "not needed / not authorised" (no penalty) or "not genuine / couldn't verify" (−10). Only approved hours count. The employee is emailed; the supervisor/admin is emailed on a new request.
- Overtime left open from a previous day is closed automatically and flagged "never stopped".
- Reports and the salary sheet get an **OT hrs** column (information only — no ₹ and no comp-off), in the screen, PDF and CSV.
- **Honesty**: genuine approved overtime changes nothing. OT start/stop with GPS off −5, fake GPS −25, never stopped −10, admin "not genuine" −10. Where places are assigned, OT start/stop must be inside a place (Strict → blocked; otherwise flagged −5). Working late without starting overtime stays a normal day.
