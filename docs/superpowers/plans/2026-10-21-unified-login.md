# Unified Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One person, one record, one password: staff, supervisor and dept admin are roles on the employee record; one sign-in box; the owner promotes employees and can mark Work ON/OFF for staff.

**Architecture:** Roles live on `employees/<id>` (`roles`, `mgrDepts`, `assignedEmp`, `manageActive`). A sign-in of kind `user` returns a staff token and, for people with a role, a second *manage token* (`role:'D'|'V', via:'emp'`) that `readSession` resolves live from the employee row, so the existing policy letters, `scopeSet()` and `inSet()` stay unchanged. Old logins keep working until the owner links and retires them.

**Tech Stack:** Cloud Functions engine running `src/backend.gs` in the GasBridge sandbox over Firestore (memory driver in tests), `node:test`; the app is the single `index.html` (Playwright e2e in the scratchpad).

**Spec:** `docs/superpowers/specs/2026-10-16-unified-login-design.md` (with `docs/superpowers/specs/2026-10-21-platform-multi-tenant-design.md` for what comes next: keep every new counter/cache key built through one helper so a company id can be added later).

**Backend location (not in this repo's git):** `/tmp/claude-0/-home-claude-attendance/c5ef49d5-bebf-535f-b8a1-989bbdbd5897/scratchpad/firebase/scaffold/functions` (FN). Tests: `npm test` in FN. The importer copy of `sheetMap.js` is `../tools/importer/lib/sheetMap.js` and must stay identical to `src/shared/sheetMap.js`.

## Global Constraints
- Amitek keeps working: all 141 existing tests pass at the end of every task; old kinds `employee`, `manager`, `supervisor` and the legacy `Admin` login are untouched until Task 8's retire switch is turned on.
- Old app versions must keep working: no existing action changes meaning or required fields.
- Roles that can be granted: `supervisor`, `dept_admin` only. There is no way to grant Super Admin; only the `Managers` row with role `super` (the owner) can change roles, and nobody changes their own roles.
- A role needs a unique email on the employee record (case-insensitive, unique across all employees).
- Sensitive actions (`setEmployeeRoles`, retire legacy logins, link legacy logins) require `confirmPass` = the owner's current password hash.
- Errors for wrong id / email / password are one identical message ('Wrong ID or password'); lock-out is 8 tries per 10 minutes and follows the person, however they typed the id.
- UI: glass theme tokens already in the app, text wraps inside boxes, 44 px touch targets, no horizontal scroll at 320 px, English labels.
- Commits end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_0111wkqXGwVNoaxJ5qZHoA6H`. No push to `main`, no deploy, no release without the user's "haan" at the gates.

## Review Focus
1. A demoted person, or one with `manageActive:'0'`, holding an old manage token is refused on the very next call (Task 2 test).
2. Two employees with the same email in different case, at add, edit and link time (Task 3 test).
3. A dept admin or supervisor calling `setEmployeeRoles` for anyone, including themselves, is refused (Task 4 test).
4. A person's staff token cannot act on another employee, and their manage token is not accepted on `EMP_FIELD` actions as another person (Task 2 test).
5. Same lock-out whether the person types email, `7`, `007` or `EMP007` (Task 2 test).

---

### Task 1: Storage fields

**Files:**
- Modify: `FN/src/shared/sheetMap.js`, `FN/../tools/importer/lib/sheetMap.js` (identical copy), `FN/src/legacyRows.js`, `FN/src/engine.js`
- Test: `FN/test/unified.test.js` (create)

**Interfaces:**
- Produces: Employees columns `roles` (comma list), `mgrDepts` (comma list), `assignedEmp` (comma list of employee ids), `manageActive` ('1' default / '0'); Managers and Supervisors column `linkedEmp`; Attendance column `markedBy`. Documents: the four Employees fields on `employees/<id>`, `linkedEmp` on the manager / supervisor docs, `markedBy` on the attendance doc.

- [ ] **Step 1: Write the failing test** `rows with roles, mgrDepts, assignedEmp, manageActive, linkedEmp and markedBy survive a save and read round trip`: build tabs with one Employees row carrying all four, one Managers row with `linkedEmp`, one Supervisors row with `linkedEmp`, one Attendance row with `markedBy:'Owner'`; assert `buildPlan` puts them on `employees/E1`, the manager/supervisor docs and the attendance doc, `rowsFromDocs` returns the same values, and `strip(buildPlan(rows))` equals the first plan (same helpers as `workmode.test.js`).
- [ ] **Step 2: Run** `npm test -- test/unified.test.js` in FN. Expected: FAIL (fields dropped).
- [ ] **Step 3: Implement** the columns in `COLS` and the `put('employees/'+id, …)` / manager / supervisor / attendance mappings in `sheetMap.js` (list fields use `n.list`, `manageActive` via `n.str`), mirror in `legacyRows.js`, add to `engine.js` field maps if the engine lists columns; copy `sheetMap.js` to the importer path.
- [ ] **Step 4: Run** `npm test` in FN. Expected: all pass (141 + new).
- [ ] **Step 5: Commit** (FN local git): `git -C FN add -A && git -C FN commit -m "unified login: storage fields"`.

### Task 2: Manage token, `login` kind `user`, live session

**Files:**
- Modify: `FN/src/backend.gs` (`readSession`, `login`, helpers near `loginOk` / `empSignInReply`)
- Test: `FN/test/unified.test.js`

**Interfaces:**
- Consumes: Task 1 fields; existing `matchEmpId`, `cleanEmpId`, `loginFail(kind, who)`, `failKey`, `empTokenFor`, `makeToken`, `pv8`.
- Produces: `findPerson(typed: string): Row|null` (email exact, case-insensitive and trimmed, else `matchEmpId`); `manageTokenFor(e: Row, role: 'D'|'V'): string` = `makeToken({role, via:'emp', id: e.id, pv: pv8(e.passHash), exp: now + TOKEN_HOURS h})`; `rolesOf(e): string[]` (only `supervisor`, `dept_admin` from `e.roles`, empty when `manageActive==='0'`); login reply `{token, role:'E', id, name, employee, requireEmpToken, roles: string[], manage: null | {token, role, hours}}` for `kind:'user'` with `username` (email or id) and `passHash`; when a person has both roles the manage token is `D`.
- `readSession` branch: `p.via==='emp'` reads `employees/<id>`, requires matching `pv`, `manageActive!=='0'` and `rolesOf(e)` still containing the role the token claims (`D` ↔ `dept_admin`, `V` ↔ `supervisor`); builds `{role:'D', depts: deptList(e.mgrDepts), scope:true, …}` or `{role:'V', assigned: assignedEmp list, scope:true, …}`; label `Dept Admin (name)` / `Supervisor (name)`.
- Lock-out key for `user` kind: `failKey('user', up(e.id))` when the person is found, else `failKey('user', lower-case typed)`.

- [ ] **Step 1: Write failing tests** in `unified.test.js` (copy the `setup()` pattern of `workmode.test.js`; give E1 `email:'a@x.com'`, `roles:'dept_admin'`, `mgrDepts:'Sales'`; E2 `roles:'supervisor'`, `assignedEmp:'E3'`):
  - `user login by email, by id and by 007 form return the same person; wrong password and unknown id give the identical error`
  - `a person with a role gets a manage token that works on a policy action in their scope, and not outside it`
  - `removing the role or manageActive 0 makes the old manage token fail on the next call` (Review Focus 1)
  - `the staff token cannot act as another employee and the manage token is refused on EMP_FIELD actions for another person` (Review Focus 4)
  - `lock-out is shared across email, 7, 007 and EMP007` (Review Focus 5)
  - `the owner's Managers super username signs in through kind user and receives manage role S`
- [ ] **Step 2: Run** `npm test -- test/unified.test.js`. Expected: FAIL (kind user unknown).
- [ ] **Step 3: Implement** `findPerson`, `rolesOf`, `manageTokenFor`, the `readSession` branch, and `case 'login'` kind `user` (also falls back to `Managers` super username → `loginOk('S', …)` reply shaped like `{token:null?}`: for the owner return `{role:'S', token, …}` exactly as `kind:'manager'` does today).
- [ ] **Step 4: Run** `npm test`. Expected: all pass.
- [ ] **Step 5: Commit** (FN local git).

### Task 3: Email uniqueness and editing

**Files:** Modify `FN/src/backend.gs` (`addEmployee`, `updateProfile`, new `setEmpEmail`, `POLICY`, `EMP_FIELD` untouched); Test `FN/test/unified.test.js`.

**Interfaces:**
- Produces: `emailTaken(email: string, exceptId: string): boolean` (trimmed, case-insensitive, over all employees); action `setEmpEmail {id, email}` policy `'SAD'` (scope-checked by the existing `emp(b.id)` rule); `addEmployee` and `updateProfile` refuse a taken email with `'That email is already used by another person'`; an empty email is allowed.

- [ ] **Step 1: Failing tests:** `add, edit and setEmpEmail refuse a duplicate email in any letter case` (Review Focus 2); `an empty email is allowed; a malformed email is refused`; `a dept admin can set the email only inside their department`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** `emailTaken`, the checks, `setEmpEmail` (uses `validMail`, audit `'Email changed'`).
- [ ] **Step 4: Run** `npm test`. Expected: all pass.
- [ ] **Step 5: Commit.**

### Task 4: Role editor actions

**Files:** Modify `FN/src/backend.gs` (`POLICY`, new actions, employee output); Test `FN/test/unified.test.js`.

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: action `setEmployeeRoles {id, supervisor: boolean, deptAdmin: boolean, departments: string[], assigned: string[], confirmPass}` policy `'S'` only. Rules: target must exist; the target needs a unique non-empty email (`'Add an email first'`); `deptAdmin` needs ≥1 department, `supervisor` may have an empty assigned list; `confirmPass` must equal the owner's `Managers` passHash (`'Password is not right'`); the owner row cannot be the target; writes `roles`, `mgrDepts`, `assignedEmp`; audit `'Roles changed'` with before and after; the person's existing manage tokens react at their next call (Task 2). `getEmployees` / `employeesFull()` output for an `S` caller includes `roles`, `mgrDepts`, `assignedEmp`, `manageActive`; never passHash.
- Action `setManageActive {id, active: boolean, confirmPass}` policy `'S'`.

- [ ] **Step 1: Failing tests:** `the owner grants dept_admin and supervisor, and the person can then sign in as that role` ; `a person without an email, or a wrong confirmPass, is refused` ; `dept admin, supervisor and staff tokens are refused setEmployeeRoles, including on themselves` (Review Focus 3); `granting changes the audit log with before and after`; `setManageActive 0 refuses the old manage token on the next call`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** both actions and the output fields.
- [ ] **Step 4: Run** `npm test`. Expected: all pass.
- [ ] **Step 5: Commit.**

### Task 5: Mark for staff

**Files:** Modify `FN/src/backend.gs` (`POLICY`, new action, attendance output); Test `FN/test/unified.test.js`.

**Interfaces:**
- Produces: action `markForStaff {empId, mode: 'on'|'off', date, time}` policy `'S'` only. It writes the same attendance fields as `savePunchIn` / `savePunchOut` (time only, no photo, no location) through the same internal path so Work Mode guards apply (refuses Work OFF while a task runs, as today), sets `markedBy` to the owner's name, and audits `'Marked Work ON/OFF for staff'`. Attendance output rows carry `markedBy` (empty for ordinary punches). A day already punched in refuses `on`; `off` needs a punch-in and refuses an existing punch-out.

- [ ] **Step 1: Failing tests:** `owner marks Work ON then Work OFF for E1, the row shows markedBy, no photo and no location`; `refused when already punched in/out, and while a task is open`; `non-owner roles are refused`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** by extracting the shared body of the two punch cases into helpers if they are inline; keep behavior of the staff actions identical.
- [ ] **Step 4: Run** `npm test`. Expected: all pass.
- [ ] **Step 5: Commit.**

### Task 6: Link old logins and retire switch (backend)

**Files:** Modify `FN/src/backend.gs`; Test `FN/test/unified.test.js`.

**Interfaces:**
- Produces: action `legacyLogins {}` policy `'S'` → `[{kind:'manager'|'supervisor'|'admin', id, name, username, departments, assigned, linkedEmp, suggestedEmp}]` where `suggestedEmp` is the employee whose name matches case-insensitively, else `''`; action `linkLegacyLogin {kind, id, empId, email?, confirmPass}` policy `'S'`: copies the manager's departments to `mgrDepts` + `dept_admin`, or the supervisor's assigned list to `assignedEmp` + `supervisor`, sets `linkedEmp` on the old row, requires the unique email rules of Task 3, audits; the legacy `admin` kind cannot be linked (it has no role to copy) and is retired only through the switch. Setting `retireLegacy` (`'1'`) via `setRetireLegacy {on, confirmPass}` (`'S'`): while on, `login` kinds `manager` (non-super) , `supervisor` and the legacy `Admin` return `'This sign-in is retired. Use your email or ID.'`; the owner's `super` Managers login is never retired. `setRetireLegacy {on:true}` is refused while any manager or supervisor row is not linked.

- [ ] **Step 1: Failing tests:** `legacyLogins lists unlinked rows with a suggested employee`; `linking a manager copies departments and the person can sign in as dept admin through kind user, with the same password they use as an employee`; `retire is refused while rows are unlinked, works after linking, and then old kinds are refused but the owner still signs in`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** the three actions and the checks in `login`.
- [ ] **Step 4: Run** `npm test`. Expected: all pass.
- [ ] **Step 5: Commit.**

### Task 7: Sign-in screen and Manage / My work switch (app)

**Files:** Modify `/home/claude/attendance/index.html`; Test `scratchpad/h_notif/e2e_unified.js` (create), keep `e2e_workmode.js`, `e2e_fieldmeet.js`, `e2e_wl.js` green.

**Interfaces:**
- Consumes: Task 2 reply `{token, manage:{token, role}, roles}`; existing `handleLogin`, `setRole`, `logout`, admin screen start code.
- Produces: the sign-in page has one box "ID or email" + password and a "Sign in" button using `login kind:'user'`; on `Unknown sign-in type` (older backend) it falls back to the old `employee` login; the old Supervisor / Admin role buttons are tucked under an "Old login" link. After sign-in everyone lands on the employee home; when `manage` is present a **Manage** button (header) opens the admin / supervisor panel with the manage token, and the admin panel has a **My work** button back. The owner's username signs in through the same box and lands directly in the admin panel.

- [ ] **Step 1: Write the failing e2e** `e2e_unified.js` (real server, memory driver, same harness as `e2e_wl.js`): E1 has `dept_admin`; sign in by email → employee home with a Manage button → Manage opens the panel → My work returns; a plain staff member sees no Manage; wrong password shows one error; the owner's username lands in the admin panel; 320 px no overflow.
- [ ] **Step 2: Run** `node e2e_unified.js`. Expected: FAIL.
- [ ] **Step 3: Implement** the screen and switch, keeping `handleLogin` for the fallback; hold the manage token in memory and the existing session storage keys for the panel (do not store passwords).
- [ ] **Step 4: Run** `e2e_unified`, `e2e_workmode`, `e2e_fieldmeet`, `e2e_wl`, `e2e_leads`, `e2e_tasks`. Expected: all pass.
- [ ] **Step 5: Commit** on branch `unified-login` and push it.

### Task 8: Roles dialog, email field, Mark for staff, and Link old logins (app)

**Files:** Modify `/home/claude/attendance/index.html`; extend `e2e_unified.js`.

**Interfaces:**
- Consumes: Tasks 3–6 actions.
- Produces: Staff tab employee card gets an email field (`setEmpEmail`), a **Roles** dialog (toggles Supervisor with a staff picker, Dept Admin with departments, password confirm, `setEmployeeRoles`), **Switch off access** (`setManageActive`) and, for the owner, **Mark Work ON / OFF** (`markForStaff`). The home card, Attendance table and reports show "Marked by Owner" when `markedBy` is set. Owner-only **Link old logins** screen (inside the owner's settings area) lists `legacyLogins` with the suggested employee and a Link button, then a **Retire old logins** switch.

- [ ] **Step 1: Extend the failing e2e:** owner opens a staff card, adds an email, grants Dept Admin with a department and password confirm → that person's next sign-in shows Manage; a duplicate email shows the error; owner marks Work ON for a person and the row shows "Marked by Owner"; Link old logins links a seeded manager, then retire switch becomes available; 320 px no overflow on the dialog.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** the dialogs reusing `fmSheet` / `fm-chip` styles and the glass tokens; all user text through `hx()`.
- [ ] **Step 4: Run** the full e2e set of Task 7 Step 4. Expected: all pass.
- [ ] **Step 5: Commit and push** `unified-login`; ledger line.

## Gates
- **Gate A (after Task 6):** copy changed FN files to the PC, `firebase_deploy only=functions` (retry once on the code-load timeout), only after the user's "haan".
- **Gate B (after Task 8):** screenshots at 320 / 390 px, then merge to `main` and `python3 tools/release.py "One login: roles, Manage switch, owner tools"` only after the user's "haan".
