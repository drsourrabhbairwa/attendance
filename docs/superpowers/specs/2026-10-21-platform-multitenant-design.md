# Platform: many companies on one app, run from a Platform Admin dashboard

Status: draft for review · 2026-10-21 · branch `unified-login` · builds on `2026-10-16-unified-login-design.md`

## Why
The product is to be sold to other companies. Amitek keeps working exactly as today. The owner of the product (the **Platform Admin**) needs one place to see every client company, what they use, what they paid, and to switch companies or IDs on and off; and each client company owner runs their own staff and promotes employees to admin.

## Success
- A second company can be created next to Amitek and cannot see, change or even detect any Amitek data (and the reverse). Amitek's data does not move and its 141 server tests pass unchanged.
- The Platform Admin dashboard shows per company: status, plan, employee count, features on/off, paid / due, last activity; and can suspend a company, switch features, reset the owner's password, and (in logged support mode) deactivate or reactivate an employee ID.
- Sign-in is one glass page: **Company ID + employee number (or email) + password**; forgot password by email code.
- A company owner promotes any employee to supervisor or dept admin (the unified-login role editor); nobody can promote to owner.

## Names and hierarchy
Platform Admin (+ platform staff: owner, support, sales) → **Company Owner** (today's `super` row, now per company) → Dept Admin → Supervisor → Staff. "Super Admin" in the older spec means the Company Owner.

## IDs
- **Company ID:** letters only, 3 to 12, unique platform-wide, case-insensitive, with a reserved list (`platform`, `admin`, `support`, `test` …). Amitek is `AMITEK`.
- **Employee ID:** numbers inside a company (`001`). Amitek's stored ids (`AMITEK001`, `EMP001` …) are **not rewritten**; sign-in accepts `001`, `1` and the full stored id for that company.
- Email is unique **within** a company (the same person at two companies is two records).
- Wrong company, wrong id and wrong password give the same message (no company-name enumeration).

## Tenancy (shared project, tenant prefix)
- Every document of a tenant lives under `tenants/<cid>/…`. A thin wrapper around the driver (`forTenant(cid)`) prefixes every path and every query; `backend.gs` still sees plain collection names.
- **Amitek's existing root-level data is tenant `AMITEK` in place:** a tenant-to-location table (`platform/companies/<cid>.db`) routes `AMITEK` to the old root paths, so nothing is copied. The same table lets a profitable client be routed later to its own database (`db: dedicated:<ref>`) without code changes in the app.
- The token carries `cid`. `authorize()` fixes the tenant before the first row is read; actions never take a company id from the request body.
- **Everything keyed outside the database is tenant-keyed too:** login lock-out counters, e-mail code keys, mail-quota counters, cache keys, settings. Collection-group queries (`q.group`) are forbidden for tenant data unless filtered to the tenant path.
- Platform data (`platform/…`) is reachable only with a platform token; tenant tokens cannot read it and platform tokens cannot call tenant actions except through support mode.

## Platform data
`platform/companies/<cid>`: name, owner name/email/phone, `status` (active, suspended), `plan`, `features` {module: bool}, `employeeCap`, `paidUntil`, `createdAt`, `db`.
`platform/payments`: cid, amount, date, method, reference, period. `platform/users`: email, name, role (owner/support/sales), passHash, active. `platform/audit`: who, what, which company, why, when.

## Platform Admin dashboard
- A separate page (`platform.html`), desktop first, same glass look, own sign-in (platform users only).
- Companies list with search and status filter; company page with: profile, employee count (live count from the tenant), features switches, plan and cap, payments and due, status switch, **reset owner password** (temporary, forced change at next sign-in), activity (last punch, punches today).
- Passwords are never shown (they are hashes); only reset.
- **Support mode:** a platform user opens a company's staff list for 30 minutes after writing a reason; read-only by default; deactivate / reactivate an employee ID and reset an employee password are allowed and each is logged. Every opening and action is in `platform/audit` and shown to the company owner in their audit log.
- Suspending a company refuses every sign-in of that company with "This account is paused. Contact support."; data stays.

## Features and plans
Modules: core attendance, Work Mode, Leads, Tasks, Site tracking, Travel, Reports. A plan is a preset of `features`. The backend refuses actions of a switched-off module (`featureOn(cid, module)`); the app hides its tabs. Amitek has everything on.

## Sign-up and payment (last step)
Until then the Platform Admin creates companies from the dashboard (manual onboarding). Later a public sign-up page (company name, owner name, email, phone) → payment through Razorpay (keys entered by the owner of the product, never in chat or code) → the payment webhook creates the tenant, picks the company ID from the name (letters, checked unique), mails the owner a temporary password. Nothing is created without a verified payment event.

## Sign-in page
One glass page: Company ID, employee number or email, password; "Email me a code" and "Forgot password?" (existing flows, now company-aware); Manage / My work switch after sign-in for people with a role. Company ID is remembered on the phone, so staff type it once. Mobile OTP is **not** in this phase (SMS needs registration and cost); the email code is used.

## Not in scope
Moving a client to a dedicated database (the table allows it), mobile OTP, invoices/GST, a Platform Admin mobile app, per-company white-label branding, the 2FA for owners.

## Review Focus
1. A token of company A can never read, write or count company B data, through any action, any query, or the `empId` in a body.
2. Lock-out, e-mail code, mail quota and cache keys are separate per company.
3. A suspended company is refused at sign-in **and** on an already-issued token's next call.
4. A tenant token is refused on every platform action; a platform token is refused on tenant actions outside support mode.
5. Amitek behaves exactly as before: the whole existing suite passes with `AMITEK` routed to the old paths.

## Delivery (each step ends with your go-ahead before deploy or release)
1. **Unified login** (separate plan, already specced), written tenant-ready.
2. **Tenant layer:** `forTenant`, `cid` in tokens, tenant-keyed counters, `AMITEK` routed in place, cross-tenant tests.
3. **New glass sign-in page** with the company field (the company field is hidden until a second company exists).
4. **Platform dashboard** and manual company creation.
5. **Features and plans**, suspend.
6. **Sign-up and payment.**

## Assumptions (shout if wrong)
1. Shared project with tenant prefix now; dedicated databases later for profitable clients.
2. Company ID letters, employee ID numbers, as you said; Amitek's stored ids stay.
3. Email-code recovery first; mobile OTP later.
4. The Platform Admin is you (plus staff you add), with its own sign-in, separate from every company.
5. Support mode is time-limited, reasoned and logged.
