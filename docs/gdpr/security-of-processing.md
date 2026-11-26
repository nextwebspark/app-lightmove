# Security of processing (Art. 32): backups, recovery, encryption, logs

What protects the data Uncava holds at rest, how long a copy outlives a deletion, and how the database is restored
(#927, epic #917). It is the source for the erase dialog's and Data & retention's "backups age out" sentence, the
workspace deletion certificate, the DPA (nextwebspark/uncava-website#29) and the website's `/security` page.

⚖️ marks a choice awaiting counsel or the business (#930).

## Where things stand (read 2026-11-10)

`gcloud sql instances describe bright-gcc --project hak-talent-mapping`:

| Setting | Value | Meaning |
|---|---|---|
| `backupConfiguration.enabled` | **false** | No automated backups are taken. |
| `pointInTimeRecoveryEnabled` | unset (off) | No point-in-time recovery: nothing between backups can be recovered. |
| Backups on hand | one `ON_DEMAND`, 2026-10-01 | An on-demand backup is kept until someone deletes it — it never ages out. |
| `diskEncryptionConfiguration` | none | Google-managed encryption keys. |
| Tier / edition / availability | `db-g1-small`, Enterprise, zonal, `us-central1` | One zone: a zone outage is an outage. |

**Two consequences, both to fix before the people controls go live:**

1. A lost or corrupted database cannot be restored to anything newer than 1 October.
2. "Copies in Uncava's backups are not restored and age out" (`ErasePersonDialog`) and "Uncava's backups age out within
   30 days" (`DueForDeletionCard`) are not true while that on-demand backup exists: it holds every person erased since
   it was taken, for as long as it is kept.

## Backups and point-in-time recovery

**Target:** daily automated backups kept **21 days**, point-in-time recovery over **7 days** of transaction logs. A
deleted row then leaves the last backup holding it at most 21 days later, inside the 30 days the screens promise, and
the database can be put back to any second of the past week. ⚖️ counsel signs off the window with the rest of
`retention-schedule.md`.

Run once, in a quiet window — the instance is shared with the Bright Data ETL, and changing backup settings can restart
it:

```bash
gcloud sql instances patch bright-gcc --project hak-talent-mapping \
  --backup-start-time=01:00 \
  --retained-backups-count=21 \
  --enable-point-in-time-recovery \
  --retained-transaction-log-days=7
```

Backups go to the multi-region nearest the instance unless `--backup-location` names one; for `us-central1` that is
the US multi-region (`us`). F9 (recommended 25 Nov 2026, not yet confirmed; revisit with F3 and F11) keeps them in the
US: `--backup-location=us` only makes that default explicit. Same-region backups would be
`--backup-location=us-central1`.

Then, once the first automated backup shows `SUCCESSFUL` in `gcloud sql backups list --instance bright-gcc`, delete the
old on-demand one so nothing outlives the window:

```bash
gcloud sql backups delete 1790857353254 --instance bright-gcc --project hak-talent-mapping
```

Any later on-demand backup (before a risky migration, say) must be deleted within the same 21 days.

## Restore drill

Once a year, and once now. A point-in-time **clone** is a new instance, so the drill never touches production:

```bash
STAMP=$(date -u +%Y%m%d)
time gcloud sql instances clone bright-gcc "bright-gcc-drill-$STAMP" --project hak-talent-mapping \
  --point-in-time="$(date -u -d '-1 hour' +%Y-%m-%dT%H:%M:%SZ)"
```

On the clone, as `postgres` (row-level security hides tenant rows from anyone else; `db-ops`):

```sql
SET app.rls_maintenance = 'on';
SELECT count(*) FROM app_lm_person;
SELECT count(*) FROM app_lm_workspace;
SELECT max(occurred_at) FROM app_lm_audit_event;   -- within an hour of the clone point
```

Compare the counts with production, record the result below, then delete the clone:

```bash
gcloud sql instances delete "bright-gcc-drill-$STAMP" --project hak-talent-mapping
```

| Date | Clone point | Time to restore | Rows checked | By |
|---|---|---|---|---|
| — | — | — | — | — |

**RPO/RTO**, once the drill has run: the recovery point is seconds (point-in-time recovery); the recovery time is the
drill's measured clone time plus repointing Cloud Run's `CLOUD_SQL_CONNECTION_NAME`.

## Encryption

| Where | At rest | Keys |
|---|---|---|
| Cloud SQL `bright-gcc` | AES-256, by Google | Google-managed |
| Documents bucket `hak-talent-mapping-lightmove-documents` (`us-central1`) | AES-256, by Google | Google-managed (no default KMS key) |
| Secret Manager | AES-256, by Google | Google-managed |
| Provider refresh tokens, an own app's client secret, a second factor's secret | Tink envelope encryption in the application (`core/crypto`), a data key per value wrapped by `lightmove.crypto.keyset`, bound to its workspace or user | Uncava's keyset, in Secret Manager |
| Suppression list entries | Keyed HMAC, never the value (`lightmove.crypto.suppression-keyset`) | Uncava's keyset, in Secret Manager |

In transit: TLS from the browser to Cloud Run, and from Cloud Run to Cloud SQL through the Cloud SQL connector.

**Customer-managed keys (CMEK) are not used.** ⚖️ Decide with counsel whether an enterprise customer's "own keys"
request (gap G8 in `data-protection-plan.md`) is answered by CMEK on Cloud SQL and the bucket; changing an existing
instance to CMEK means a new instance and a migration.

## Logs

- **Retention:** Cloud Logging's `_Default` bucket keeps every entry **30 days** (`gcloud logging buckets describe
  _Default --location=global`).
- **The application's lines** carry ids, never an email or a name, and since #927 every deployed line's message and
  stack trace is masked (`PersonalDataLogMasker`: emails, LinkedIn profile links, phones written with `+`).
- **Cloud Run's own request log** (`run.googleapis.com/requests`) records each request's full URL, query string
  included, and is outside the application's reach. A search or lookup is a `GET` whose query holds what was typed or
  captured: an email address, a name, a LinkedIn link. Two `_Default` exclusions in `ops/gcp/bootstrap.sh` keep such
  entries from being stored at all:
  - `email-images`: the email-picture route, a record of who opened what.
  - `query-terms` (#1494, decision F10): any request whose query carries `q=`, `search=`, `executiveQuery=`, `name=`
    or `linkedinUrl=`, or a raw token in `token=` or `t=`.
    - The text parameters cover the Candidates page and its export, the grid, the pipeline, Outreach, the company
      typeaheads and the "already held?" checks (`/candidates/held`, `/triage/held`). The browser extension calls those
      checks with a profile link on every capture, and they made up most of the matching entries in a two-week sample.
    - The token parameters cover the invitation preview, the verification and reset links as the SPA page loads them,
      and the Recall live webhook's shared secret. Tokens are never stored raw, the request log included.
    - `RequestLogExclusionCoverageTest` fails when a controller adds a text query parameter that the filter and the
      safe-to-log list both leave out. That includes the keys `PoolCriteria` reads from the Candidates page's raw query.
- **What the exclusion costs:** those requests have no platform log line, so Logs Explorer and the daily
  `ops/gcp/log-digest` do not see them (the digest's status and 5xx counts miss them). The application's own masked
  `request` line and any ERROR line remain. Cloud Run's built-in request metrics are not logs and are unaffected. **Open:** does
  `monitoring.sh`'s project-level 5xx log metric still count an excluded 5xx? Until that is answered, #927's box stays
  unticked. If it does not, the 5xx alert moves to Cloud Run's own `request_count` metric (`response_code_class=5xx`),
  which no exclusion touches. The bucket keeps 30 days (F10).
- **Applied to production:** not yet. Recorded here, with the date and the Logs Explorer check, once it is.
