# Requests sent to Uncava by executives and users

_Draft for counsel (#930, nextwebspark/uncava-website#28, epic #917). Engineering facts read from the code on
2026-11-13; not legal advice._

The internal procedure behind the website's "Requests about you" section. A firm answers a request about its own
workspace itself ([use-cases.md](use-cases.md)); this page is for a request that arrives at **Uncava**: from an
executive who has never heard of the firm holding them, or from a user about their own account.

## 1. Intake

- **Channel:** [Privacy email address]. A form needs its own processor and a CSP `form-action` change on the website,
  and waits for a design (website #28).
- **Log it** the day it arrives in the request log (§5). The clock is **one month** from receipt (Art. 12(3)),
  extendable by two more for complex or numerous requests, telling the person within the first month.
- **Ask only what finds the person:** full name, the LinkedIn profile URL, and the email addresses or phone numbers
  they think are held. Nothing else.
- **Verify proportionately.** Reply from the address on file, or ask the person to confirm control of the LinkedIn
  profile or the email named. Do not ask for identity documents unless doubt remains ⚖️.

## 2. Which role Uncava has

| The request is about | Uncava is | Who answers |
|---|---|---|
| The user's own account (name, email, sessions, sign-in, billing) | controller | Uncava (§3) |
| What Uncava's vendor cache holds on an executive | controller | Uncava (§3) |
| An executive's record in a firm's workspace | processor | the firm (§4) |
| Not wanting to be sourced again by anyone | both | Uncava for the cache; each firm for its workspace ⚖️ |

## 3. Uncava as controller

**A user's own account.**
- Access and portability: the user can download it themselves (Settings → Profile, `GET /api/v1/auth/me/export`,
  `AccountExportService`); or ops runs the same on their behalf.
- Erasure: the user deletes the account themselves (`DELETE /api/v1/auth/me`, `AccountDeletionService`). Notes, tasks
  and audit lines that name its id stay the workspace's ([retention-schedule.md](retention-schedule.md)).

**The vendor cache.** Look up the person by profile slug (the last path part of their LinkedIn URL, lower-cased) in
`app_lm_vendor_person` and `app_lm_vendor_people_search`, as `postgres`. These tables are not tenant-scoped. Send
what is held for access; delete the rows for erasure. Rows age out after 30 days anyway once `RETENTION_ENABLED` is on.

## 4. Uncava as processor: routing to the firms

1. **Find the workspaces.** There is no app tool. Today the only path is ops SQL on production, run by
   [named person] with a second person's approval, recorded in the request log:

   ```sql
   SET app.rls_maintenance = 'on';   -- bypasses row-level security: ops only (db-ops skill)
   SELECT DISTINCT p.workspace_id
     FROM app_lm_person p
    WHERE p.profile_slug = :slug
   UNION
   SELECT DISTINCT p.workspace_id
     FROM app_lm_person_contact c
     JOIN app_lm_person p ON p.id = c.person_id
    WHERE c.value_key = :key;   -- CandidateContact.keyOf: an email lower-cased, a phone's digits only
   ```

   Write down workspace ids only. Do not open the records.
2. **Forward** the request to each workspace's admins within [5] working days, through [the contact on the DPA],
   naming the person only as they named themselves. The firm answers within its own month with the tools in
   [use-cases.md](use-cases.md): export, erasure, restriction, suppression.
3. **Tell the requester** which role Uncava has, that the request has been passed to [the number of] firms using
   Uncava, and that each will answer them. Whether Uncava may name those firms is ⚖️.
4. **Act on Uncava's own copy** (§3: the vendor cache) for an erasure or objection.
5. **Never change a firm's data on Uncava's own decision.** A processor acts on the controller's instructions
   (Art. 28(3)(a)).

## 5. Request log

Kept in [location, restricted access] for [retention ⚖️].

| Ref | Received | Requester (as they named themselves) | Right | Verified | Uncava's role | Workspaces (ids) | Forwarded | Uncava's action | Answered | By |
|---|---|---|---|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — | — | — | — | — |

## 6. Follow-ups this procedure needs

- **A platform-admin lookup** ⚖️: "which workspaces hold this slug or address" as a `SUPER_ADMIN`-only endpoint
  (`@platformAuthorizer`, audited), so step 4.1 needs no raw SQL. It returns workspace ids only. Open it as its own app
  issue once website #28's page is designed (#917 step 5).
- **Cross-workspace suppression** (#920) ⚖️: suppression hashes are keyed per workspace
  (`SuppressionKey.hashInput` takes the workspace id), so an objection to Uncava cannot today stop *every* firm filing
  the person again. Whether it should is the first open decision in #917.
