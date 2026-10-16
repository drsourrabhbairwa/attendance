# One person, one login, many roles

Status: draft for review · 2026-10-16 · branch `unified-login`

## Why
Today a supervisor or admin who is also staff signs in twice, with two different ids and passwords, because four separate identity stores exist: `Employees` (staff id), `Managers` (username, role super/dept), `Admin` (one old shared account) and `Supervisors` (name). A person who is both has two records that nothing links. Making someone an admin means creating a second account.

## Success
- A person has **one** record and **one** password. Staff, supervisor, dept admin, super admin are roles on that record.
- One sign-in box for everyone. After sign-in everybody lands on their own employee home (Work ON/OFF, tasks). A person with a management role also sees a **Manage** switch; no second password.
- The Super Admin makes any employee a supervisor / dept admin / super admin from the Staff tab, and takes it away again.
- The owner can mark Work ON/OFF for any employee, visibly labelled, without signing in as them.
- Nothing existing breaks: old staff ids, old manager and supervisor logins keep working until the owner retires them.

## Not in scope
Forgot-password by email (needs the mail service, later), OTP/2FA, new role types beyond the four (the role list is built so HR/Accounts can be added later), changing what each role can see (scope rules stay as they are).

## Identity
- `Employees` gains: `email` (unique, case-insensitive), `roles` (comma list from `supervisor, dept_admin, super`; staff is implicit), `departments` (for dept_admin), `assignedEmp` (for supervisor; moves here from `Supervisors`).
- **Login key:** the sign-in box takes an email **or** a staff id. Staff ids (EMP001) keep working. Email is **required only to hold a management role**; plain staff may have none. Both resolve to the same record and the same password.
- Password hash is the employee's own. Lock-out (8 tries / 10 min) follows the person, as it does for staff now.

## Sign-in and tokens
- New `login` kind `user`. The reply contains a **staff token** (always) and, if the person has any role, a **manage token** plus `roles`. Old kinds `employee`, `manager`, `supervisor` stay.
- Two tokens, not one merged token: staff screens use the staff token (`EMP_FIELD` still forces `empId`); the admin panel uses the manage token (policy letters S / D / V as now). Backend policy code is therefore unchanged.
- **Roles are checked live:** every manage call re-reads the person's roles from `Employees`. Removing a role or switching a person off takes effect on the next call, not when the token expires.
- Manage token lasts 12 hours. **Sensitive actions** (change roles, delete staff, reset a password, retire legacy logins) ask for the password again (`confirmPass`).

## Making an admin
- Staff tab, employee card → **Roles**: toggles for Supervisor (pick the assigned staff), Dept Admin (pick departments), Super Admin. Super Admin only.
- Granting any role needs a unique email on the record (asked in the same dialog).
- Guards: there is always at least one active Super Admin; you cannot remove your own Super role if you are the last; every change goes to the audit log with who did it.

## Owner marks for staff
- `markForStaff {empId, action: on|off, time}` for Super Admin; scope-limited admins may be added later. No photo, no GPS. The attendance row gets `markedBy`, shown as "Marked by Owner" on the home card, admin screens and reports.
- Refused if the day is already punched in/out the same way; the owner cannot edit an existing punch with it.

## Migration
1. Add the fields; nothing is removed.
2. Super Admin screen **Link old logins**: each `Managers`, `Supervisors` and legacy `Admin` row is matched to an employee (suggested by name, owner confirms or picks). Linking copies role, departments and assigned staff onto the employee and asks for an email.
3. Old login kinds keep working. When every row is linked the owner can **retire legacy logins** (a setting, confirmPass), after which they are refused.
4. The legacy shared `Admin` account must be linked to a real person before retirement, so the audit log names a human.

## Review Focus
1. Demoted or switched-off person with a live manage token is refused on the next call.
2. Duplicate or differently-cased email at create, edit and link time.
3. Removing the last Super Admin (self or another) is refused.
4. A staff id that matches nothing, an email with spaces, and ids with different case all fail or succeed identically for the lock-out counter.
5. A supervisor with both staff and supervisor tokens cannot use the staff token to act on another employee, and the manage token cannot be used on `EMP_FIELD` actions as another person.

## Delivery, each step ends with your go-ahead before deploy or release
1. Backend: fields, `login` kind `user`, two tokens, live role check, role editor actions, `markForStaff`, tests.
2. Sign-in screen and the Manage / My work switch.
3. Staff tab Roles dialog and email field.
4. Owner "Mark for staff" and the "Marked by" label.
5. Link old logins, then retire legacy logins.

## Assumptions (shout if wrong)
1. Email only for management roles; everyone else optional.
2. Owner marking has no photo/GPS and is labelled.
3. Password re-ask on sensitive actions only.
4. Existing passwords are unchanged; a linked manager signs in with the employee password (they set it from Profile if they never had one).
