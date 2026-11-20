# Backup and recovery procedure

_Draft for counsel, 2026-11-19, unreviewed. Requested by Alac Partners (gap analysis §5b, item 9; internal, ISO 27001
style). The facts come from [security-of-processing.md](security-of-processing.md), read on 2026-11-10 with
`gcloud sql instances describe bright-gcc --project hak-talent-mapping`; **re-read them before relying on this page**.
**Automated backups and point-in-time recovery are OFF today.** Targets below are targets, not capabilities._

## 1. What exists today

| Item | State on 2026-11-10 | Consequence |
|---|---|---|
| Automated backups (`backupConfiguration.enabled`) | **false** | none are taken |
| Point-in-time recovery | **off** | nothing between backups can be recovered |
| Backups on hand | one `ON_DEMAND`, 2026-10-01 | the newest restorable state is 1 October; it never ages out until someone deletes it |
| Availability | `db-g1-small`, Enterprise edition, zonal, `us-central1` | one zone: a zone outage is an outage |
| Disk encryption | Google-managed keys; no CMEK | |
| Documents bucket `hak-talent-mapping-lightmove-documents` | private, uniform access, public access prevention, **no versioning** | an object deleted or overwritten is not recoverable from the bucket. Whether bucket soft-delete is on: [Owner to confirm] |
| Secret Manager | secrets and keysets (`lightmove.crypto.keyset`, `lightmove.crypto.suppression-keyset`) | losing a keyset makes sealed values (mailbox refresh tokens, second-factor secrets) unreadable and the suppression list unmatchable. Who holds an offline copy: [Owner to confirm] |
| Schema and code | in git; Flyway rebuilds the whole schema from V1 | a lost database is not a lost schema |
| Apollo company universe (`app_lm_apollo_companies`) | ETL-owned, about 100k rows, holds companies not people | reloadable by the ETL: [Owner to confirm the ETL's own recovery path] |
| Shared instance | the Bright Data ETL uses `bright-gcc` too | changing backup settings can restart it, so schedule a quiet window |
| Cloud Logging | `_Default` bucket, 30 days | not a backup of anything |

Two consequences stated in `security-of-processing.md`: a lost or corrupted database cannot be restored to anything
newer than 1 October, and the on-demand backup holds every person erased since it was taken.

## 2. Objectives

| Objective | Today | Target |
|---|---|---|
| RPO (data loss tolerated) | up to the age of the 2026-10-01 backup (weeks) | seconds, with point-in-time recovery over 7 days of logs. Approved value: [Owner to confirm] |
| RTO (time to restore service) | never measured | [Owner to confirm]; set from the drill's measured clone time plus repointing Cloud Run's `CLOUD_SQL_CONNECTION_NAME` |
| Backup retention | the on-demand one never expires | 21 daily automated backups (decision E3, open) |
| Availability | zonal | [Owner to confirm whether high availability is wanted] |

## 3. Procedure to switch on (not yet run)

Owner: [Owner to confirm]. Run once, in a quiet window.

| Step | Command or action |
|---|---|
| 1. Enable daily backups, 21 kept, PITR with 7 days of logs | `gcloud sql instances patch bright-gcc --project hak-talent-mapping --backup-start-time=01:00 --retained-backups-count=21 --enable-point-in-time-recovery --retained-transaction-log-days=7` |
| 2. Pin where backups live | add `--backup-location=us-central1`, or leave the nearest multi-region; the choice is decision F9 (open, with the residency work in [residency-adr.md](residency-adr.md)) |
| 3. Confirm the first automated backup shows `SUCCESSFUL` | `gcloud sql backups list --instance bright-gcc` |
| 4. Delete the old on-demand backup | `gcloud sql backups delete 1790857353254 --instance bright-gcc --project hak-talent-mapping` |
| 5. Any later on-demand backup (before a risky migration) | delete it within the same 21 days |
| 6. Alerting on backup failure | not set up: [Owner to confirm] |

## 4. Restore drill

Once now, then once a year, and after any change of instance. A point-in-time **clone** is a new instance, so the drill
never touches production.

1. Clone: `gcloud sql instances clone bright-gcc "bright-gcc-drill-$STAMP" --project hak-talent-mapping --point-in-time=<one hour ago, UTC>`, timing it.
2. On the clone as `postgres`, bypass row-level security for counting only: `SET app.rls_maintenance = 'on';` then
   `SELECT count(*) FROM app_lm_person;`, `SELECT count(*) FROM app_lm_workspace;`,
   `SELECT max(occurred_at) FROM app_lm_audit_event;` (within an hour of the clone point).
3. Compare the counts with production and record the result below.
4. Delete the clone: `gcloud sql instances delete "bright-gcc-drill-$STAMP" --project hak-talent-mapping`.

This tests the database only. A drill of the documents bucket, the keysets and a full repoint of Cloud Run is not
written: [Owner to confirm].

| Date | Clone point | Time to restore | Rows checked | Result | By |
|---|---|---|---|---|---|
| — | — | — | — | — | — |

## 5. Real recovery

| Situation | Action |
|---|---|
| Wrong or lost data in the database | restore by clone to a new instance at a point in time (once PITR is on); do not restore over production; decide with the incident lead which rows to copy back. Today: only the 2026-10-01 backup exists |
| Zone or instance outage | wait for Google, or clone to a new instance and repoint `CLOUD_SQL_CONNECTION_NAME`: [Owner to confirm who may do it] |
| Bad deploy or migration | Flyway runs before the new revision takes traffic and redeploying the previous tag does not undo an applied migration (`row-level-security.md` §0); a corrective migration or a restore is needed |
| Incident declared | follow [breach-runbook.md](breach-runbook.md); preserve evidence before any restore |
| After a restore | re-check that nobody erased since the restore point is back: erasures, workspace purges and suppression entries made after that point are in the restored copy. No automatic replay is built: [Owner to confirm the manual step] |

## 6. Backups and deletion

| Question | Answer from the repository |
|---|---|
| How long does a deleted row survive in a backup | at most the backup retention once automated backups run: 21 days targeted, inside the 30 days the screens promise. Point-in-time logs hold 7 days. Nothing rewrites a backup (`retention-schedule.md`) |
| What the product tells people | `ErasePersonDialog`: "Copies in Uncava's backups are not restored and age out"; `DueForDeletionCard`: "Uncava's backups age out within 30 days" |
| Is that true today | **No**, while the on-demand backup exists: it holds everyone erased since 2026-10-01 for as long as it is kept |
| The 21-day window | decision E3, open: counsel signs off 21 days of backups and 7 of point-in-time recovery. The agreed UAE position on deletion "including backups and subprocessors" (gap analysis P6) needs the same answer |
| Workspace deletion | `WorkspacePurge` deletes every tenant row and the workspace's files 30 days after deletion and writes a certificate; the certificate cannot yet say backups are clear until the window above is in force |
| Audit trail and credit ledger | append-only, kept by id; not purged ([retention-schedule.md](retention-schedule.md)) |
| Subprocessor copies | not covered by Uncava's backups: Recall deletes audio once the transcript is filed; other suppliers' retention is [Owner to confirm] |
| Where the backups live | decision F9: multi-region nearest the instance unless pinned with `--backup-location`; a regional stack pins its own |

## 7. Review

Reviewed [Owner to confirm: yearly] and after every drill. The facts in §1 must be re-read from `gcloud` at each review.

## 8. Gaps

1. Automated backups and point-in-time recovery are off; the only backup is dated 2026-10-01 and never expires.
2. The product's "backups age out" sentences are not true while that backup exists.
3. No RPO, RTO, restore drill or recorded result.
4. Single zone; no high availability.
5. The documents bucket has no versioning; no tested way back for a deleted file.
6. No recovery plan for the keysets and secrets.
7. No alert on backup failure.
8. Backup location (F9) and window (E3) undecided; the cap on how long a deleted row survives is therefore unproven.
9. No written step to re-apply erasures after a restore.
