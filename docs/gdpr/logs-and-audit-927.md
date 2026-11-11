# GDPR #927: personal data out of logs and audit details

Step 2 of epic #917. The audit trail (`app_lm_audit_event`) is append-only, so a name or an address written into an
event's details outlives the person's erasure (#918). Deployed logs had no masking either. This step sets the rule
"audit details hold ids, never a person's name or contact", enforces it with a test, masks the deployed logs, and
documents backups, recovery and encryption at rest.

None of it is behind `DATA_PROTECTION_ENABLED`. It is hygiene, not a feature.

| Piece | PR | Branch | Base |
|---|---|---|---|
| Audit details hold ids only | #1276 | `feature/927-audit-ids-only` | `main` |
| Mask personal data in deployed logs | #1279 | `feature/927-log-masking` | `main` |
| Backups, PITR, encryption, log retention (docs only) | #1280 | `docs/927-backups` | `main` |

**Merge order:** #1276 first, then #1279, which was built on it and has since been merged with `main`. #1280 is
independent and can merge at any time.

---

## 1. What has been done

### PR 1 (#1276): audit details hold ids only

**Call sites now record ids, not people:**

| Event | Before | After |
|---|---|---|
| `CANDIDATE_REMOVED` | `fullName` | `personId` beside `candidateId` |
| `PERSON_DOCUMENT_ADDED`, `PERSON_DOCUMENT_VERSION_ADDED` | `fileName` | `documentId` only. A CV's file name is its person's. |
| Duplicate signup (both doors) | `email` | the existing account is the target |
| `LOGIN_FAILED` (`no_such_user`) | `email` | `domain` only (the part after `@`) |
| `MEMBER_INVITED`, `INVITATION_REVOKED` | `email` | nothing. The invitation is the target, and the customer audit log names it from the live row. |
| `PROFILE_UPDATED` | `fullName` | nothing |

A position description's `fileName` and a spreadsheet import's `fileName` are kept, since they are business data, not a
person's.

**Recent activity reads names from live rows.** `project` declares the port `ActivityPersonNames`, and `candidate`
implements it (`ActivityPersonNamesAdapter`). `ProjectActivityService` resolves each page's `personId`s to current names
in one batch. It still answers `details.fullName`, so the SPA's `lib/activity.ts` is unchanged. An erased person, or a
row written before #927, reads as "removed an executive".

**The rule is enforced in code:**

- `AuditService.Builder` throws `IllegalArgumentException` for the keys `email`, `fullName`, `firstName`, `lastName`,
  `phone`, `linkedinUrl` and `profileSlug`. That is a programming error, and any test that runs the path surfaces it.
- It also replaces an email address found under any other key with `[email removed]`, as a backstop.
- `AuditDetailKeysTest` scans `src/main/java`. Every literal detail key that sounds personal (`email`, `name`, `phone`,
  `linkedin`, `slug`, `address`, `file`) must be on a reviewed allowlist, per class, with a reason. So a company's
  `name` cannot quietly become a person's somewhere else.
- `AuditLine`'s `#927` comment is updated. Its allowlist stays.

**Older rows.** `ops/cloudsql/scrub-audit-pii.sql` is a one-off for the table's owner. In one transaction it lifts FORCE
row-level security and the append-only trigger. It then:

- turns `email` into `domain` on an unknown sign-in;
- drops `email` and `fullName` from every row;
- drops `fileName` from a person's document events only.

Then it puts the trigger and FORCE back, and prints a count per step. A failure rolls all of it back, and a second run
changes nothing. It is not a Flyway migration, because the runtime role cannot disable the trigger.

**`ContactRedaction` moved** from `core/llm` to `core/text`. It is no longer only for model input, and PR 2's log masker
uses it too. LLM callers behave as before.

**Web.** The Assessment tab's unsaved scorecards (`lm.scorecard-draft.*` in `sessionStorage`) are now cleared by
`forgetScorecardDrafts()` (`features/candidates/lib/scorecardDrafts.ts`) on:

- sign-out;
- a session lost for good;
- account deletion.

They are kept on a workspace switch, so a draft survives it.

**Docs:**

- `retention-schedule.md` gains an "Audit details" rule.
- `use-cases.md`, `people-controls-918-920.md` and `candidate-crm.md` no longer say that older lines keep names.

**Tests:**

- `AuditServiceTest`: denied keys, an address under an innocent key, ordinary details.
- `AuditDetailKeysTest`.
- `ProjectFlowIntegrationTest.removedExecutiveIsNamedFromTheLiveRecord`: the trail holds the person's id and no name.
  The feed shows the current name, or none once the person is deleted.
- `scorecardDrafts.test.ts`, plus a sign-out case in `routes.test.tsx`.

### PR 2 (#1279): mask personal data in deployed logs

- **`PersonalDataLogMasker`** (`core/logging/service`) is a logstash `ValueMasker`. It runs on the deployed JSON encoder
  through `MaskingJsonGeneratorDecorator` in `logback-spring.xml`, and reads only the `message` and `stack_trace`
  fields:
  - emails become `[email removed]`;
  - LinkedIn profile links become `[profile removed]`: under `/in/` or the older `/pub/`, any subdomain, any case, and
    percent-encoded as a query value (`linkedin.com%2Fin%2F…`, as a vendor's request line quotes it);
  - phones written with a leading `+` and at least 8 digits become `[phone removed]`. There is no upper bound, so a
    phone followed by a year or a count (`+971 50 123 4567 2026`) is masked with it rather than handed back.
- **It costs next to nothing on an ordinary line.** Each pass runs only when the line holds `linkedin`, `@` or `+`.
  The email and profile patterns start a match only where an address or a host name does
  (`ContactRedaction.EMAIL`'s lookbehind matches the same addresses as before), so a long run of word characters, such
  as base64 in a stack trace, is read in linear time rather than retried from every position.
- **What it leaves alone:** the MDC fields (`correlationId`, `userId`, `workspaceId`, `projectId`) and the request
  line's numbers. A phone without a `+` is left too, because the looser rule reads a UUID's last group, or an epoch
  timestamp, as a phone number.
- **The `local`, `test` and `e2e` profiles keep the plain console log, unmasked.** e2e reads emailed links out of it.
- **The call sites stop logging contacts:**
  - `ResendEmailSender` logs the recipient's domain alone (`EmailAddressValidator.domainOf`), never the address, and
    no subject, since an invitation's or a mention's names a colleague;
  - the auto-verify warning in `AuthenticationService` logs the user id;
  - `HarvestApiProfileEnricher`, `BrightDataProfileEnricher` and `LogProfileEnricher` log no profile URL or slug.
- `docs/production-observability.md` says what is masked, and that a person is found in the logs by their id.
- **Tests** (`PersonalDataLogMaskerTest`):
  - it masks an address, a profile link and a `+971` number;
  - it masks a `+` number followed by a year or a count;
  - it masks a percent-encoded, a `/pub/` and an upper-case profile link;
  - a 100,000-character line ending in `@`, `linkedin.com/` or `+` is masked within 1 s (`assertTimeoutPreemptively`).
    This test was checked to fail against the old, unanchored email pattern;
  - it leaves a UUID, an epoch timestamp, `AED 1,200,000`, a company slug and a Stripe request id alone;
  - an encoder built like the deployed one masks the message and an exception's message, and leaves the MDC id;
  - `logback-spring.xml` itself, loaded with no profile active (the deployed case), masks through its `JSON` appender.
    This test was checked to fail when the decorator is removed.

### PR 3 (#1280): backups, recovery, encryption, logs (docs only)

A read-only `gcloud` check on 2026-11-10 found:

- `bright-gcc` takes **no automated backups**, and **point-in-time recovery is off**.
- The only backup is an on-demand one from 2026-10-01 (id `1790857353254`). On-demand backups never expire, so the
  screens' "backups age out within 30 days" (`DueForDeletionCard`, `ErasePersonDialog`) is **not true today**.
- Encryption at rest is Google-managed on Cloud SQL and the documents bucket. There is no CMEK.
- Cloud Logging's `_Default` bucket keeps entries 30 days. Cloud Run's request log records full URLs, so a search typed
  as an address (`?q=`, `?search=`) is kept there.

`docs/gdpr/security-of-processing.md` (new) covers:

- the command for 21 daily backups and 7 days of PITR, which keeps the 30-day promise;
- deleting the old on-demand backup;
- a restore drill as a point-in-time clone, with a table to record it;
- encryption at rest and in transit;
- log retention and the request-log question.

It also updates:

- `data-protection-plan.md` (G10, item 1.4);
- `production-observability.md` §6 item 4, and its stale `db-f1-micro`;
- `retention-schedule.md` (Logs and Backups rows).

---

## 2. What remains

### Code and review

- [ ] Review #1276, #1279 and #1280 (fable or codex).
- [ ] Merge #1276, then #1279, then #1280.
- [ ] Run the e2e matrix after #1279 (`cd e2e && PROFILE=e2e ./run-all.sh`). It reads the local log.

### Ops steps after merge (by hand, in production)

- [ ] **Scrub old audit rows**, once, as `postgres`, **after #1276 is deployed**. Run before it, the old revision
  keeps writing names into new rows in the meantime:
  ```bash
  gcloud sql connect bright-gcc --user=postgres --database=lightmove < ops/cloudsql/scrub-audit-pii.sql
  ```
- [ ] **Turn on backups and PITR.** Use the `gcloud sql instances patch bright-gcc …` command in
  `security-of-processing.md`, in a quiet window: the instance is shared with the Bright Data ETL and may restart.
- [ ] **Delete the on-demand backup** `1790857353254`, once the first automated backup shows `SUCCESSFUL`.
- [ ] **Run the restore drill** and record its RPO and RTO in the doc's table.
- [ ] **Keep searched addresses out of Cloud Run's request log**: an exclusion filter on `run.googleapis.com/requests`,
  or a log bucket with shorter retention.

### Follow-ups and open decisions

- [ ] Website: once backups are on, the `/security` page drops "[to confirm]". This is not yet noted on #927.
- [ ] `PERSON_VIEWED` audit event: deferred to its own issue (not yet opened).
- [ ] ⚖️ Backup location, with #925's residency decision.
- [ ] ⚖️ CMEK: not adopted. Revisit with counsel.
- [ ] ⚖️ Counsel signs off the 21-day backup window with the rest of `retention-schedule.md`.
- [ ] Close #927 once the PRs are merged and the ops steps are done.

### If the PRs merge but the ops steps are not done

Merging all three without the ops steps is safe for the app. Nothing breaks, no code waits on a manual step, and there
is no migration. Masking turns on at the next deploy, and every new audit row holds ids only. What is left is a gap
between what the docs and screens promise and what is actually true.

| Ops step skipped | What stays true |
|---|---|
| Scrub script | Rows written before #927 keep their `email`, `fullName` and a CV's `fileName`. New rows are clean. |
| Backups and PITR | No automated backups and no point-in-time recovery. A lost or corrupted database restores to 1 October 2026 at best. |
| Deleting the on-demand backup | That backup holds everyone erased since 1 October, and never expires. |
| Restore drill | No measured RPO/RTO. Nothing changes in the app. |
| Request-log exclusion | An email typed into a search box stays in Cloud Run's request log for 30 days. |

**Scrub script not run.**

- Customers cannot see the old values:
  - the customer audit log screen and its CSV phrase each line from allowlisted keys;
  - the workspace export's `audit-log.csv` is that same view;
  - Recent activity no longer reads `fullName`, so an old row reads "removed an executive" whether or not it is
    scrubbed.
- **The real problem is erasure.** The audit table is append-only. Anyone removed or erased before the deploy still has
  their name in it, so an erasure request for them is never fully done.
- `retention-schedule.md` (from #1276) says "Rows written before #927 were scrubbed once", which would be false.

**Backups and PITR not turned on.** This is the most serious gap. The PRs do not cause it, but it stays as it is today.
It is a business-continuity risk, not only a GDPR one.

**Old on-demand backup not deleted.**

- "Backups age out within 30 days" stays untrue. `ErasePersonDialog` and `DueForDeletionCard` tell admins this, and the
  workspace deletion certificate relies on it.
- This holds even after backups are turned on: until this backup is deleted, the promise is not kept.

**Restore drill not run.**

- You cannot answer "What is your RPO/RTO?" (G10 in `data-protection-plan.md`).
- The DPA and the website's `/security` page cannot drop "[to confirm]".

**Request log left as is.** #1279's masking does not reach it. The masking covers the app's own log lines, not the
request log Cloud Run writes, so the logs are only partly clean.

**Recommended order:**

1. Merging first is fine.
2. Do not switch `DATA_PROTECTION_ENABLED` on for customers, and do not send the DPA or the `/security` wording, until
   backups and PITR are on and the on-demand backup is deleted. Those two steps are what make a sentence users read
   untrue.
3. The scrub takes a minute and can follow the merge at any time.
4. The drill and the request-log decision can wait.

---

## 3. How to test it locally

The steps below assume the branch under test is checked out: `feature/927-log-masking` holds both code PRs, since it is
merged with `main`, which holds #1276.

### Automated tests

```bash
cd apps/api && ./mvnw test        # needs Docker (Testcontainers)
```

Or run just the #927 tests:

```bash
cd apps/api && ./mvnw test -Dtest='AuditServiceTest,AuditDetailKeysTest,PersonalDataLogMaskerTest,ContactRedactionTest,RetentionScheduleCoverageTest,ProjectFlowIntegrationTest'
```

```bash
cd apps/web && npx vitest run src/features/candidates/lib/scorecardDrafts.test.ts src/app/routes.test.tsx
cd apps/web && npm run build      # the real typecheck
```

Known failures that are already on `main` and are not caused by #927:

- `CandidateReadsGoldenIntegrationTest` (the talent-map golden);
- `OutreachReplyMigrationTest`;
- web tests that mount `useTheme`, under Node 26 (its global `localStorage` shadows jsdom's).

### Audit details, by hand

1. `npm run dev`, then sign in to a workspace with a position.
2. Do each action below, then look at the newest rows in `npm run dev:db:psql`:
   ```sql
   SELECT event_type, metadata FROM app_lm_audit_event ORDER BY id DESC LIMIT 10;
   ```

| Action | Expect in `metadata` |
|---|---|
| Remove an executive from a position | `personId` and `candidateId`, no `fullName` |
| Upload a CV to a person | `documentId`, no `fileName` |
| Invite someone, then revoke the invitation | no `email` |
| Change your name in Settings → Profile | no `fullName` |
| Sign in with an address that has no account | `reason: no_such_user` and `domain`, no `email` |
| Sign up again with an existing account's address | no `email`; the target is the existing user |

3. Open the projects list and click the position's row. The side panel's **Recent activity** still names the removed
   executive, read from the live person.
4. Rename that person on the Candidates page. Recent activity shows the new name.

`dev:db:psql` connects as the container's superuser `postgres`, so row-level security does not hide any workspace's
rows.

### The scrub script, against the local database

1. Put a few rows back into the old shape, in `npm run dev:db:psql`. The append-only trigger has to be lifted to write
   them, as the script does:
   ```sql
   BEGIN;
   ALTER TABLE app_lm_audit_event DISABLE TRIGGER app_lm_audit_event_append_only;
   UPDATE app_lm_audit_event SET metadata = metadata || '{"fullName":"Old Name"}'
    WHERE id = (SELECT max(id) FROM app_lm_audit_event WHERE event_type = 'CANDIDATE_REMOVED');
   UPDATE app_lm_audit_event SET metadata = metadata || '{"email":"someone@example.com"}'
    WHERE id = (SELECT max(id) FROM app_lm_audit_event WHERE event_type = 'LOGIN_FAILED');
   ALTER TABLE app_lm_audit_event ENABLE TRIGGER app_lm_audit_event_append_only;
   COMMIT;
   ```
   If those event types are missing, do the matching action in the app first.
2. Run the script:
   ```bash
   docker exec -i lm-dev-pg psql -U postgres -d lightmove < ops/cloudsql/scrub-audit-pii.sql
   ```
   It prints a count per step. The unknown sign-in now holds `domain: example.com`, and the removal holds no
   `fullName`.
3. Run it a second time. Every count is 0.
4. Check the trigger is back: a plain update as the superuser must fail with the append-only error.
   ```sql
   UPDATE app_lm_audit_event SET metadata = metadata WHERE id = (SELECT max(id) FROM app_lm_audit_event);
   ```

### Log masking

`npm run dev` runs the `local` profile, which keeps the plain console log on purpose, so masking does not show there.
Two ways to see it:

- **The test (recommended).** `PersonalDataLogMaskerTest.deployedConfigurationMasks` loads the real
  `logback-spring.xml` with no profile active and asserts the masked JSON.
- **A real boot.** Run the API with the `dev` profile instead of `local`. `dev` is not one of the console profiles, so
  it gets the deployed JSON encoder, and `JwtConfig` still lets it mint its own keys. Take the command and environment
  from `ops/dev/api.sh`, and change `-Dspring-boot.run.profiles=local` to `dev`. OAuth sign-in is not available
  without `application-local.yml`; password sign-in is.
  1. Sign in with an unknown address, or do anything that logs an email or a `+971…` number.
  2. In the console's JSON lines, the address reads `[email removed]` in `message` (and in `stack_trace`, if any).
  3. `correlationId`, `userId` and `workspaceId` are intact.

### Scorecard drafts on sign-out

1. Open an executive's **Assessment** tab and change a score without saving.
2. In DevTools → Application → Session Storage, a `lm.scorecard-draft.<candidateId>` key is present.
3. Switch workspace from the topbar menu. The key stays.
4. Sign out. The key is gone.

### Backups doc (#1280)

It is docs only, with nothing to run locally. Read `docs/gdpr/security-of-processing.md` and check its commands against
`gcloud sql instances describe bright-gcc --project hak-talent-mapping` before running any of them.
