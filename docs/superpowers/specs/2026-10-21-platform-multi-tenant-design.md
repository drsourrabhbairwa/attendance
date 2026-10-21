# Platform: sell the attendance app to many companies

Status: draft for review · 2026-10-21 · branch `platform` (builds on `unified-login`)

## Why
Amitek's app works for one company. The owner wants to sell it: a company buys, gets its own company ID and owner login, and the vendor (us) manages every client from one dashboard without disturbing Amitek, which keeps running as it is.

## Names
- **Platform Admin**: us (the vendor). Platform team roles: owner, support, sales.
- **Company Owner**: the buyer; today's `super` role, one per company.
- Below them, per company: Dept Admin → Supervisor → Staff (see the unified-login spec).

## Success
- Amitek keeps working with no data move and no re-install; old phones and tokens keep signing in.
- A new company's data is invisible to every other company, whatever a client sends.
- The Platform Admin sees every company: employees, features, plan, paid-until, status; can suspend or activate a company, switch features, record a payment, reset the owner's password, deactivate a person's ID.
- Later, a profitable company can be moved to its own database by changing one setting, with no code change.

## Not in scope now
Own-database tenants (the routing is built, the move is a later task), mobile-number OTP (needs an SMS provider and India DLT registration; email code stays), automatic billing (payments are recorded by hand first), white-label branding, one person in two companies.

## IDs
- **Company ID**: 3–12 letters A–Z, unique, chosen at sign-up (suggested from the company name), upper-cased. A reserved list is refused (ADMIN, PLATFORM, SUPPORT, API, WWW, TEST, …). Amitek is `AMITEK`.
- **Employee ID**: digits, `001`, `002`, … per company (a counter on the company record). The full ID is the company ID plus the number, `AMITEK001`.
- **Sign-in** accepts: company ID + employee number in two boxes, or `AMITEK001` typed in one, or the person's **email**. An email belongs to one company only (unique across the platform), so it needs no company box.
- Amitek's existing IDs (EMP001, E1, …) stay valid as they are: the company record keeps `legacyIds: true` and the current `matchEmpId` rules apply inside the Amitek tenant. A token or login with no company ID means Amitek, so old phones are unaffected.

## Tenant layer
- Storage: each call opens the database for its company. `driver.forTenant(cid)` returns the same driver with every path prefixed `tenants/<cid>/` (shared project). Amitek is `pathMode:'root'`: no prefix, so its existing documents are untouched.
- Later dedicated database: the company record holds `dbRef` (another Firestore database or project); `forTenant` returns a driver for it. `backend.gs` never knows.
- Every token carries `cid`; `authorize` sets the tenant before any row is read; a call can never name another company. Tokens without `cid` mean Amitek.
- **Everything that is keyed by a person or a setting must include the company**: lock-out counters, e-mail codes, cached settings, script locks, push registrations, photo/storage paths, audit log.
- Scheduled jobs (reminders, weekly reports, push, auto-close) loop over active companies, one tenant at a time, a failure in one company never stops the others.
- Firestore rules and indexes are written per tenant path; the browser never talks to Firestore directly.

## Platform data and login
- Root collections (outside any tenant): `platform_companies`, `platform_plans`, `platform_payments`, `platform_users`, `platform_audit`, `platform_usage`.
- Company record: `id, name, status (active|suspended), planId, features[], employeeLimit, paidUntil, ownerEmail, nextEmpNo, pathMode, dbRef, legacyIds, createdAt`.
- Platform team signs in on its own page with its own email + password and its own token (`plat:true`). A platform token can call only platform actions; a company token can never call them.
- **Support view**: to look inside a company the platform user gives a reason; the view is read-only except that support may deactivate or reactivate an employee ID and send an employee a password reset, each logged and visible to the company owner; expires in 30 minutes, and writes a `platform_audit` entry (who, which company, why, when). Passwords are never shown anywhere; the only password action is **reset** (a temporary password shown once).
- **Suspended** company: every person of that company gets "This company's account is paused. Contact your admin." at sign-in and on every call.

## Features and plans
- Feature keys: `work_mode, leads, tasks, site, travel, places, overtime, reports, quotation`. The company's `features` list is the switch; Amitek has all.
- Backend: one gate (`featureOn(key)`) per action group, so a direct call to a switched-off feature is refused; the app hides the matching tiles and tabs.
- A plan is a named bundle of features plus `employeeLimit`. Adding a person beyond the limit is refused with a clear message.

## Platform dashboard
A separate desktop page (`platform.html`, glass theme). One row per company: name, ID, status, plan, employees (active / limit), features, paid-until, last activity. Actions: create company (manual onboarding), suspend/activate, change plan and features, record a payment, reset owner password, deactivate or reactivate a person's ID, open support view. Filters and a search by company ID, name or email.

## Sign-up and payment
- **First**: the Platform Admin creates a company from the dashboard (company name → suggested ID → owner name, email, phone → plan). This sends the owner a temporary password by email and lets sales onboard before any payment automation exists.
- **Later**: a public `signup.html` with Razorpay checkout; a payment-confirmed webhook creates the same company record. The Razorpay keys are entered by the owner, never by me.

## New sign-in page (glass)
Company ID, employee number or email, password; "Forgot password?" sends an email code (existing flow); a Sign-up link for new companies (later phase). Same glass tokens as the Work Mode screens, text inside boxes, 44 px targets, no horizontal scroll at 320 px.

## Review Focus
1. Company A's token (and a forged `cid` in the body) never reads, writes or lists company B's data, through any action, including `getAll`-style reads and the export/report actions.
2. Employee `001` exists in two companies: sign-in, lock-out, scope, tokens and audit never mix them up.
3. Old Amitek phones (no `cid`, EMP-style IDs) still sign in and work with the new backend.
4. A suspended company, or a feature that is switched off, is refused on direct calls, not only hidden in the UI.
5. Cached values (settings, codes, counters, locks) and scheduled jobs are per company.

## Delivery, each step ends with your go-ahead before deploy or release
1. Unified login (its own plan), written tenant-ready.
2. Tenant layer: `forTenant`, `cid` in tokens, per-company keys, scheduled jobs per tenant, with the isolation tests. Amitek moved onto it as `pathMode:'root'`.
3. Company and platform data, platform login, features and plan gates, employee limit.
4. Platform dashboard.
5. New glass sign-in page with the company ID box.
6. Sign-up page and Razorpay.

## Assumptions (shout if wrong)
1. Shared project with tenant prefix now; a dedicated database later for profitable clients.
2. Company ID letters, employee ID numbers; Amitek's old IDs stay valid.
3. An email is unique across all companies.
4. Payments are recorded by hand until the Razorpay step.
5. The Platform Admin can look into a company only through the logged, read-only support view.
