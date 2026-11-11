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

Backups go to the multi-region nearest the instance unless `--backup-location` names one; pin it (`us-central1`) or
leave it with #925's residency decision ⚖️.

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
  included, and is outside the application's reach. A search typed as an address — the Candidates page, the grid, the
  Outreach list — is a `GET` with `?q=` or `?search=`, so that address is kept there for the 30 days. The email-image
  route is already excluded (`ops/gcp/bootstrap.sh`). ⚖️ Choose one: an exclusion filter for request entries whose
  URL carries `q=` or `search=` (those requests then have no platform log line, though the application's own `request`
  line remains), or a shorter retention on a bucket of their own.
