# GDPR people controls: suppression, erasure, restriction (#918, #920)

Three pieces of epic #917 (section D of #1080) that let a firm act on a person's data-protection request. Each shipped
as its own PR:

| Piece | Issue | PR | Migration(s) | Status |
|---|---|---|---|---|
| Suppression list: no door files a listed person again | #920, part 1 (Art. 21) | #1178 | V195 | merged |
| Erase a person for good | #918 (Art. 17) | #1179 | V196, V197 | merged |
| Restrict processing of a person | #920, part 2 (Art. 18) | #1180 | V198 | merged |

They depend on each other in one direction. The erasure writes suppression entries so an erased person isn't filed
again, and restriction can add an objection to the same list. All three rely on forced row-level security (V184,
V190), which was merged before them.

---

## 1. Suppression list (#920 part 1, #1178)

### What it covers

A workspace keeps a list of people that no door may file again. Each entry has a reason:

| Reason | Written by |
|---|---|
| `erased` | the erasure itself (the API refuses it from an admin) |
| `objected` | an admin, or the restriction dialog's "also suppress" tick |
| `manual` | an admin, for anything else (the default) |

A person is recognised by their **LinkedIn profile slug** or an **email address**, never by phone or name. Once a key
is listed:

- the add form, the browser plugin and a spreadsheet row are refused with `409 PERSON_SUPPRESSED`;
- mapping someone the workspace already knows is refused when that person's own stored keys are listed, whatever the
  request carries;
- an edit is refused only if it adds a suppressed key, so a person's own keys never block their edits;
- Find executives, People Search and Add to position leave the person out and count them as `suppressed`.

### How it is built

- `app_lm_workspace_suppression` (V195, forced RLS) stores `kind`, `value_hash`, `key_id`, `reason`, `note` and who
  added it, never the key itself.
- `value_hash` is an HMAC-SHA-256 of `workspaceId:kind:normalisedKey` under `lightmove.crypto.suppression-keyset`
  (`KeyedHasher` / `TinkKeyedHasher`), so a copy of the table can't be tested against a guessed email.
- `SuppressionKey` normalises a key the way `PersonMatcher` does (`LinkedInUrls.profileSlugOrNull`, a lower-cased
  address).
- `SuppressionGate` holds the doors' reads. `SuppressionListService` holds the admin's writes and the erasure's.
- The admin API is `GET | POST /api/v1/workspace/suppressions` and `DELETE …/{id}`, all `WORKSPACE_MANAGE` and audited
  as `PERSON_SUPPRESSION_ADDED` / `_REMOVED`.
- A workspace holding entries the keyset can't read (a key removed too early) is refused at every door with
  `SUPPRESSION_UNAVAILABLE`, never let through.

The list's screen is Settings → Data & retention (#1186).

---

## 2. Erasure (#918, #1179)

### What it covers

An admin deletes a person and everything held about them, for good, from the Candidates drawer's ⋯ → **Delete
person permanently…**, with the person's name typed back.

### How it is built

- **Permission:** `PERSON_ERASE` (V196), seeded to ADMIN alone.
- **Preview:** `GET /api/v1/candidates/{personId}/erase-preview` counts what will go: positions, notes, documents,
  files, emails, outreach runs, meetings, recordings and tasks. `blockedBy` names a refusal that would apply right now.
- **Erase:** `POST /api/v1/candidates/{personId}/erase` with `{"confirmName": "...", "suppress": true}`. `suppress` is on
  unless sent `false`. It answers 204.
- **One transaction** locks the person row and runs each `PersonErasureContributor`. The interface is declared in
  `candidate` and implemented where the rows live:
  - `outreach` refuses while an email is mid-send (`PERSON_ERASE_SEND_IN_FLIGHT`);
  - `notetaker` queues the person's Recall bots in `app_lm_recall_bot_erasure` (V197);
  - `enrichment` forgets the vendor cache's copy of the profile;
  - `notification`, `task` and `enrichment/sourcing` scrub the name from the bell and from Find executives' runs.

  It then deletes `app_lm_person`, and everything cascades from it.
- **Files:** every document version's bucket key is queued in `app_lm_storage_deletion` (V196: global, keys only) and
  deleted by `StorageDeletionSweeper`. Failures back off to a day and are never given up.
- **Recall:** `RecallBotErasureSweeper` calls each queued bot back and deletes its media. Failures back off to an hour,
  and a bot is given up only past Recall's own 7-day retention.
- **Suppression:** the person's profile and every email they held go on the list as `erased`, unless the admin unticks
  it.
- **Audit:** `PERSON_ERASED` is recorded by person id and counts, never the name.

---

## 3. Restriction of processing (#920 part 2, #1180)

### What it covers

Anyone on the team restricts processing of a person from the Candidates drawer's ⋯ → **Restrict processing…**, with a
reason (500 characters at most). They can also tick "add to the suppression list as an objection". The record stays
readable and editable, and nothing uses it until someone lifts the restriction.

| What | Behaviour while restricted |
|---|---|
| AI: Suggest/Assess with AI, openers, interview plan, copilot | refused `PERSON_PROCESSING_RESTRICTED` at `CandidateService.dossierOf`, the one read every model run goes through |
| Automatic background run, the assistant's profile tool | skip the person |
| Contact lookup, an own email, Book a call / reschedule, Notetaker | refused before anything is spent |
| Add to sequence, openers, Start | the person is skipped as `PROCESSING_RESTRICTED` |
| A live run, a waiting own email | stopped or failed at the next send as `PROCESSING_RESTRICTED` |
| Bots waiting to join or recording | called off in the restriction's own transaction and queued for `RecallBotErasureSweeper` |
| A transcript arriving later | not summarised; the recording fails as `PROCESSING_RESTRICTED`, and Suggest changes is refused |
| Any door mapping them to a further position | refused at filing; Find executives and People Search count a skip; Add to position counts them `restricted` |

### How it is built

- **Schema:** `app_lm_person.processing_restricted`, plus `_reason`, `_by` and `_at` (V198), in the shape do not
  contact uses: the reason, who and when are kept only while the restriction is set, enforced by CHECKs. V198 also
  adds `PROCESSING_RESTRICTED` and `PROCESSING_RESTRICTION_LIFTED` to the activity kinds, and `PROCESSING_RESTRICTED`
  to the enrollment stop reasons and the message fail reasons.
- **API:** `PUT /api/v1/candidates/{personId}/processing-restriction` with
  `{"restricted": true, "reason": "...", "suppress": false}` (`CANDIDATE_POOL_MANAGE`, `ProcessingRestrictionService`).
- **Lifting** is allowed only to whoever restricted the person or a `WORKSPACE_MANAGE` holder; anyone else gets
  `403 PERSON_PROCESSING_RESTRICTION_NOT_YOURS`. The record's `processingRestriction.mayLift` tells the drawer whether
  to offer Lift.
- **Records:** each change is a timeline line and an audit event (`PERSON_PROCESSING_RESTRICTED` / `_LIFTED`). An
  objection added to someone already restricted is audited on its own as `PERSON_SUPPRESSION_ADDED`.
- **Notetaker:** `PersonProcessingRestricted` is heard inside the restricting transaction
  (`ProcessingRestrictionNotetaker`). The bots are locked, called off, audited (`NOTETAKER_CANCELLED`) and queued
  before the commit, so a crash afterwards can't leave a bot scheduled at Recall.
- **SPA:**
  - a "Processing restricted" pill and a note in the Candidates drawer (reason, who, when, and Lift for someone allowed);
  - a strip in a position's executive drawer;
  - `RestrictProcessingDialog`;
  - the extension says why a capture was refused.

---

## What remains, or is blocked

| Item | Why it is open | Where it is tracked |
|---|---|---|
| Keep a restricted person out of what a **client seat** reads (Companies grid, talent map, report, export, public API and MCP universe) | Needs counsel to say whether showing an already-mapped person to the hiring company counts as processing | Follow-up issue to file (asked for in the #1180 review) |
| The assistant's executive **list** still sends names and titles to the model | Only the profile tool is held back so far | #1180, "Not in this PR" |
| **Cross-workspace** suppression for an objection sent to Uncava as controller (vendor-sourced data) | Needs a decision from #920 | #920 checklist; not built |
| **Names in older audit lines.** `CANDIDATE_REMOVED` still stores `fullName`, the audit trail is append-only (`harden.sql`), and the erasure leaves earlier lines as they are | Needs a decision from #918 (keep as a legal-obligation/security log, recorded in the RoPA, or stop writing names) | #918 notes, and the logs issue |
| **Backups** keep erased data until they age out | By design: an erasure never rewrites backups | Document the retention window in the privacy notice |
| `CandidateReadsGoldenIntegrationTest` and `OutreachReplyMigrationTest` fail locally | They fail the same on `main` (a session-time-zone assumption and a golden recording); this work doesn't cause them | CI decides |

## Risks

1. **The suppression keyset is now what protects the list.**
   - `lightmove-suppression-keyset` must exist before `SUPPRESSION_LIST_ENABLED` is set in `deploy.yml`.
   - A key may leave the keyset only once no entry uses it (`key_id`). Until then, every workspace holding such
     entries is refused at every door.
2. **Hashes depend on normalisation forever.** A change to how `LinkedInUrls.profileSlugOrNull` or the email key reads
   a value silently misses existing entries. Change either only with a re-key.
3. **The Recall queue is best-effort past 8 days.** A bot Recall keeps refusing is dropped once its media would have
   aged out anyway, with an `ERROR` log ("Recall still refused queued bot …"). Watch for it in the logs.
4. **Storage deletion never gives up.** A permanently refused bucket key retries daily forever. Watch the size of
   `app_lm_storage_deletion`.
5. **Restriction does not hide anyone from client seats yet** (see above). Until counsel decides, a client
   representative can still read a restricted person on a mandate they already sat on.
6. **Erasure is irreversible.** Only ADMIN holds `PERSON_ERASE`, the name must be typed back, and the preview counts
   first. Beyond that there is no undo, and backups are the only copy.
7. **Lifting a restriction is gated** to whoever set it or an admin. If that colleague leaves, an admin has to lift it.

---

## Testing locally, step by step

### 0. Start a clean stack

```bash
npm run dev:db:reset      # optional: a fresh database, so V1 → V198 all run
npm run dev               # Postgres :55433, API :8080, web :5173
```

`ops/dev/api.sh` supplies dev-only `CREDENTIAL_KEYSET` and `SUPPRESSION_KEYSET`, so the suppression list works with
nothing else configured. Email goes to the API console (`LIGHTMOVE_EMAIL_PROVIDER=log`): copy verification and
invitation links from there.

1. Open http://localhost:5173, sign up as **alok@acme.test** and found a workspace. You are its ADMIN.
2. Invite **sara@acme.test** as MEMBER from the Team page (`/team`), then open the invitation link from the console in
   a private window and sign up as Sara.
3. As Alok, create a position (Positions → New position), then In universe → Create ▾ → **Add executive**:
   - Full name: Noor Haddad
   - LinkedIn: `https://www.linkedin.com/in/noor-haddad/`
   - Email: `noor@example.com`
4. In Noor's drawer, add a note and upload a PDF under Profile → Documents (needed for the erasure checks).

For the API steps below, get a token in Git Bash:

```bash
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alok@acme.test","password":"<your password>"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
PERSON=<Noor's person id: open /candidates, click Noor, copy the ?person= value from the URL>
```

Run the database checks with `npm run dev:db:psql`. It connects as `postgres`, so it sees every workspace.

### 1. Suppression list

1. Add a manual entry for someone not yet filed:
   ```bash
   curl -s -X POST localhost:8080/api/v1/workspace/suppressions -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' -d '{"email":"blocked@example.com","reason":"objected","note":"Asked by phone"}'
   curl -s localhost:8080/api/v1/workspace/suppressions -H "Authorization: Bearer $TOKEN"
   ```
   **Expect:** the list shows a `kind: email`, `reason: objected` entry with no address in it.
2. In the UI, Add executive with email `blocked@example.com`. **Expect:** a toast saying they're on the suppression
   list (`PERSON_SUPPRESSED`), and nobody added.
3. The same with a LinkedIn URL: add `{"linkedinUrl":"linkedin.com/in/blocked-person"}`, then try to add an executive
   with `https://www.linkedin.com/in/Blocked-Person/`. **Expect:** refused. Case and scheme don't matter.
4. In psql:
   ```sql
   select kind, reason, length(value_hash), key_id from app_lm_workspace_suppression;
   ```
   **Expect:** hashes only, never the address.
5. Remove the entry with `DELETE /api/v1/workspace/suppressions/{id}`, then add the person again. **Expect:** they're
   filed now.
6. As Sara, call `GET /api/v1/workspace/suppressions`. **Expect:** 403, because the list is an admin's.

### 2. Restriction of processing

1. As **Sara**, go to /candidates, open Noor → ⋯ → **Restrict processing…**, enter a reason and confirm.
   **Expect:**
   - a "Processing restricted" pill, and a note with the reason, "Restricted by Sara Al-Mansour" and Lift;
   - the Activity tab shows a "Processing restricted" line.
2. Open Noor from the position's In universe grid. **Expect:** the executive drawer shows the restricted strip.
3. Try each gate from the executive drawer:
   - Profile → Background → **Suggest with AI**, and Assessment → **Assess with AI**: **expect** a toast saying
     processing is restricted;
   - Contact → **Find email** (where ContactOut is configured): **expect** the same refusal, with no credit spent;
   - Outreach → Write email / Book a meeting / Add to sequence: **expect** a refusal, or a skip with the reason.
4. Add Noor to a second position: Candidates page → tick Noor → Add to position. **Expect:** a toast saying their
   processing is restricted, so they were left out.
5. **Who may lift:** sign in as a third member (or invite one), open Noor. **Expect:**
   - no Lift in the ⋯ or on the note, and the line "Only Sara Al-Mansour or an admin can lift it.";
   - a direct `PUT` with `{"restricted":false}` answers `403 PERSON_PROCESSING_RESTRICTION_NOT_YOURS`.

   As Alok (admin) or Sara, Lift works.
6. **Objection with the restriction:**
   - restrict again with "also add to the suppression list" ticked. **Expect** two `OBJECTED` rows in
     `app_lm_workspace_suppression`;
   - lift it. **Expect** the entries to stay.
7. Audit:
   ```sql
   select event_type, metadata from app_lm_audit_event
   where target_id = '<person id>' order by id;
   ```
   **Expect:** `PERSON_PROCESSING_RESTRICTED` / `_LIFTED`, each with no reason text. An objection added while already
   restricted shows as its own `PERSON_SUPPRESSION_ADDED`.
8. Notetaker and live runs need Recall and a mailbox, which `npm run dev` doesn't have. They're covered by the
   integration tests in step 4.

### 3. Erasure

1. As **Alok**, open Noor on /candidates → ⋯ → **Delete person permanently…**. **Expect:** the dialog counts their
   positions, notes, documents and files.
2. Type a wrong name. **Expect:** Delete stays disabled. Type `Noor Haddad`, leave "add to the suppression list"
   ticked, and confirm. **Expect:** the drawer closes and Noor is gone from /candidates and from the position.
3. As Sara, check that the menu has no Delete, and that `GET …/erase-preview` answers 403.
4. In psql:
   ```sql
   select count(*) from app_lm_person where id = '<person id>';                -- 0
   select count(*) from app_lm_project_candidate where person_id = '<person id>';  -- 0
   select count(*) from app_lm_person_note where person_id = '<person id>';    -- 0
   select count(*) from app_lm_person_activity where person_id = '<person id>';  -- 0
   select * from app_lm_storage_deletion;                                   -- empty once the sweeper ran (1 min)
   select kind, reason from app_lm_workspace_suppression;                   -- PROFILE_SLUG/EMAIL : ERASED
   select event_type, metadata from app_lm_audit_event where event_type = 'PERSON_ERASED';  -- counts, no name
   ```
5. The uploaded PDF is gone from `apps/api/.data/documents`, which is the filesystem store's directory.
6. Add Noor again with the same LinkedIn URL or email. **Expect:** `PERSON_SUPPRESSED`. Remove the `ERASED` entries
   through the API, and the add goes through as a brand-new person.

### 4. Automated tests

```bash
cd apps/api && ./mvnw test -Dtest='PersonSuppressionIntegrationTest,SuppressionKeyTest,SuppressionEntriesTest,SuppressionGateTest,PersonErasureIntegrationTest,RecallBotErasureSweeperIntegrationTest,PersonProcessingRestrictionIntegrationTest,NotetakerIntegrationTest,ContactLookupIntegrationTest,MeetingIntegrationTest,OutreachDispatchIntegrationTest,OutreachOwnEmailIntegrationTest,OutreachSequenceIntegrationTest,ExecutiveSourcingIntegrationTest,CandidatePool*,AuditLog*'
cd apps/web && npm run build && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/candidates src/features/outreach
cd apps/extension && npx vitest run
```

Docker is needed for the API tests (Testcontainers). On Node 26, `--no-experimental-webstorage` stops Node's own
`localStorage` from hiding jsdom's, which otherwise breaks the drawer suites.
