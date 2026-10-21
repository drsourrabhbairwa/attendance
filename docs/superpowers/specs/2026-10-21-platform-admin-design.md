# Platform Admin + Multi-Company Layer — Design

Date: 2026-10-21 · Status: DRAFT for owner review (no code written)

## 1. What we are building (understanding)
Today the app serves one company (Amitek). The owner wants the app to become a product that many companies use, run by a **platform team** above the companies.

Hierarchy (as the owner stated): **Platform Admin (owner, support, sales) → Company Owner → Dept Admin → Supervisor → Staff.** Only the bottom four exist today.

Success = (a) Amitek keeps working untouched, (b) the platform team can create/suspend companies from their own panel, (c) one company can never see another's data, (d) system mails go out from the platform's mail id.

Out of scope for this phase: self sign-up, Razorpay billing, Coin ledger, Gig board, support tickets, FCM push.

## 2. Company separation (the important decision)
Options:
- **A. `companyId` field on every row, filter on every read.** One forgotten filter leaks data.
- **B. Separate collection path per company** (`tenants/{cid}/Attendance`, …). The backend's `tab()` resolves the path from the logged-in token, so a query *cannot* reach another company. **Recommended.**
- **C. Separate Firebase project per company.** Safest, but costly and slow to operate.

Choice: **B**. Amitek's current root collections are kept as company `amitek` (alias), so **no data migration and no downtime**. New companies live under `tenants/{cid}/…`.

The login token carries `cid`. Every action runs with that `cid`; there is no client-supplied company id.

## 3. Platform side
- New collections (root, never per-company): `companies`, `platformUsers`, `platformAudit`, `platformConfig`.
- `companies` doc: `id, name, status (trial|active|suspended), plan, staffLimit, trialEnds, ownerLogin, createdAt, createdBy`.
- Roles and rights:
  | Role | Can do |
  |---|---|
  | platform_owner | everything, manage platform team, mail sender, suspend/delete |
  | support | view companies, reset a company owner's password, read usage; **no staff data, no billing** |
  | sales | create trial companies, set plan/limits, view status; **no staff data** |
- Separate login action `platformLogin` and token kind `platform`. A company token can never call platform actions and vice-versa.
- Every platform action writes a `platformAudit` row (who, what, which company, when).
- Platform panel = new page `platform.html` in the same repo (same glass look), sections: Companies, Create company, Company detail (status, plan, limits, usage counts), Team, Settings (mail sender).
- Create company: platform user enters company name + owner name/email/phone → system creates the tenant, the company-owner login (role S) and emails a one-time password from the platform sender. Suspended company: login shows "Your account is paused, contact support"; data is kept.
- **Bootstrap:** the first platform_owner is created by a one-time command run on the owner's own PC (password typed there). No password ever goes through chat.

## 4. Mail from the platform id
- `platformConfig/mail`: `fromName`, `fromAddress`, `replyTo`. All `sendMail` calls (OTP, welcome, reports, notices) use it; a company may later set only its own reply-to.
- The transport secret (Brevo/SMTP key) is stored as a Firebase secret by the owner on his own machine. It is never pasted in chat, code or Firestore.
- Until the secret exists, mail keeps using the current setting, so nothing breaks.

## 5. Safety work done first
1. Daily Firestore backup schedule (7-day retention).
2. Firestore rules: deny all direct client access (functions are the only door).
3. Rate-limit on `platformLogin`.

## 6. Build order (each step shippable alone, Amitek never down)
1. Tenant resolver in `tab()` + `cid` in tokens, `amitek` alias. Test: two memory-driver companies, same ids, zero cross-reads.
2. Platform collections, `platformLogin`, role checks, audit, bootstrap command.
3. `platform.html` panel (companies, create, suspend, team).
4. Platform mail sender.
5. Backup + rules + rate-limit.

## 7. Testing
- Node tests: tenant isolation (every action family: attendance, leave, notices, tasks), role matrix for platform roles, suspended-company login, company token rejected on platform actions.
- Playwright: panel login, create company, new owner can log in and sees empty data, Amitek unchanged.
- Existing 176 backend tests + e2e suites must stay green.

## 8. Risks
- Background jobs (`remindersTick`, backups) must loop over all companies; covered by a test.
- Any hard-coded root collection name in `backend.gs` bypasses `tab()`; step 1 starts with a grep audit and a test that fails on direct root access.

## 9. Decisions needed from owner
1. Option B (separate path per company, Amitek as alias) — OK?
2. Platform role rights table in §3 — OK, or change?
3. Plan names/staff limits can wait until billing phase — OK to ship with "trial/active/suspended" and a plain staff limit number only?
