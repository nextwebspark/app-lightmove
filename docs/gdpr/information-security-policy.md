# Information security policy

_Draft for counsel, 2026-11-19, unreviewed. Requested by Alac Partners (gap analysis §5b, item 4: internal, ISO 27001
style, with a summary for clients). Every statement is read from this repository; where a fact is unknown it says
**[Owner to confirm]**, and where a control is not built it says so. Nothing here is a claim of certification: Uncava
holds no ISO 27001 or SOC 2 certificate and has had no third-party penetration test (`data-protection-plan.md` G9)._

## 1. Purpose and scope

This policy sets how Uncava protects the personal and commercial data its customers (executive search firms and
in-house teams) put into the product, and the data of Uncava's own users and staff.

| In scope | Where it lives |
|---|---|
| The Uncava product: API (`apps/api`), web app (`apps/web`), Chrome extension (`apps/extension`) | Google Cloud Run, `us-central1` |
| Customer data: people, positions, notes, documents, outreach, call transcripts | Cloud SQL `bright-gcc` and the documents bucket, `us-central1` ([subprocessors.md](subprocessors.md)) |
| Uncava's own data: accounts, billing, audit trail, logs, the vendor cache | same, and Cloud Logging |
| Secrets and keysets | Secret Manager |
| Staff, contractors and the tools they use to build and operate the product | [Owner to confirm: head count, laptops, MDM] |

Out of scope here: the marketing website (`uncava-website`, its own repository), and customers' own mailboxes and
calendars, which they connect themselves.

## 2. Roles and ownership

| Role | Holder | Responsibility |
|---|---|---|
| Security owner (policy owner, annual review) | [Owner to confirm] | keeps this policy and the controls in §4 current |
| Incident lead and decision owner | [Owner to confirm] (`governance-decisions.md` A7, open) | [breach-runbook.md](breach-runbook.md) §1 |
| Data protection officer | [Owner to confirm] (A3, open: counsel to say whether one is required) | advice and contact for regulators and data subjects |
| Production database and cloud administrators | [Owner to confirm: list from `gcloud projects get-iam-policy`] | [access-control-policy.md](access-control-policy.md) §8 |
| Security mailbox | [Security email address] (A2, open: no inbound mailbox exists today) | where reports arrive |
| Approver of exceptions to this policy | [Owner to confirm] | |

## 3. Principles

| Principle | How it is applied today |
|---|---|
| Customers' data is separate | Every workspace query is filtered by the session's workspace, and forced row-level security backs it in the database ([access-control-policy.md](access-control-policy.md) §7). |
| Least privilege, by action | Authorisation asks for an action (`@PreAuthorize`), never a role name; guards re-read the database on every check. |
| Personal data is minimised in what leaves | Contacts are taken out of AI inputs (`ContactRedaction`); compensation and custom columns are never sent (`CandidateDossier`); audit details hold ids, never names (#927). |
| Secrets are never stored raw | Tokens are SHA-256 hashes; provider secrets are sealed (§4). |
| Nothing is trusted from the client | The JWT `roles` claim is never used for a decision. |

## 4. Controls that exist

| Area | Control | Evidence |
|---|---|---|
| Tenant isolation | Workspace filter on every query (`AuthPrincipal.requireWorkspaceId()`); forced Postgres row-level security on 79 tenant tables (V184, V190, V192); the boot refuses an unprotected tenant table (`TenantTableGuard`) | `docs/security/row-level-security.md` |
| Authentication | Work-email identity; invitation-only membership; password login with BCrypt strength 12; lockout after 5 failures for 15 minutes; login rate limit (10 a minute by default); vague auth errors with one timing | `application.yml` (`lightmove.auth`) |
| Sessions | Access token 15 minutes, in browser memory only; refresh token 30 days in an httpOnly, Secure, SameSite=Strict, host-only cookie, rotated on every use with replay detection; Chrome extension refresh 14 days | `application.yml`; `lightmove-domain` skill |
| Two-factor | TOTP and recovery codes, sealed secret; behind `MFA_ENABLED` (default false); no per-workspace enforcement | [access-control-policy.md](access-control-policy.md) §6 |
| Tokens and keys | Refresh, reset, invitation and API-key tokens stored as SHA-256. An MCP token carries its own audience and signing key; an API key reaches only `/api/v1/public/**` | CLAUDE.md security invariants |
| Encryption at rest | AES-256 by Google on Cloud SQL, the bucket and Secret Manager, Google-managed keys. Provider refresh tokens, a second factor's secret and an own app's client secret are additionally sealed in the application (Tink, `core/crypto`), keyset in Secret Manager | [security-of-processing.md](security-of-processing.md) |
| Encryption in transit | TLS from browser to Cloud Run; Cloud Run to Cloud SQL through the Cloud SQL connector; TLS to providers | same |
| Suppression list | Keyed HMAC of the value, never the value | `lightmove.crypto.suppression-keyset` |
| Audit trail | `app_lm_audit_event` is append-only at database level (`ops/cloudsql/harden.sql`); customer admins read it in Settings; ids only (`AuditDetailKeysTest`) | `logs-and-audit-927.md` |
| Logging | Deployed log lines and stack traces are masked for emails, LinkedIn links and `+` phone numbers (`PersonalDataLogMasker`, #927); Cloud Logging keeps 30 days | `security-of-processing.md` |
| Browser hardening | Content-Security-Policy names every origin a page may call; SPA fonts self-hosted; Mapbox and Nylas widget telemetry off (#929) | `subprocessors.md` |
| Secure development | Dependency scan (OSV) and container scan (Trivy) in `security.yml` on every PR and weekly; a CRITICAL finding (CVSS 9.0 or more) fails the check, which blocks a merge only if the check is required on `main` (§6); Dependabot; schema changes only by reviewed Flyway migration | `.github/workflows/security.yml`; CLAUDE.md |
| Secrets management | Secrets in Secret Manager, passed to Cloud Run with `--set-secrets` | `deploy.yml` |
| Monitoring | Cloud Monitoring alerts on ERROR lines and 5xx (5 or more in 10 minutes) and an uptime check; a daily log digest | `ops/gcp/monitoring.sh`, `log-digest.sh` |

## 5. Supplier management

| Item | Position |
|---|---|
| Register of suppliers that receive personal data | [subprocessors.md](subprocessors.md): Google Cloud (Cloud Run, Cloud SQL, Storage, Vertex AI, Logging, Secret Manager), Resend, Stripe, Mapbox, Bright Data, HarvestAPI, ContactOut, Nylas, Recall.ai; Google, Microsoft and Zoom as the customer's own providers |
| Where each runs | Stated per supplier; Resend, Stripe, Bright Data and HarvestAPI regions are [Region] (decision F2 open). ContactOut states AWS `us-west-1` (vendor-stated, not verified; `ropa.md` UAE addendum) |
| Contract status | The "DPA signed" and "Transfer mechanism" columns of `subprocessors.md` are all unfilled: [Owner to confirm] per supplier. ContactOut says an API agreement and DPA exist; Uncava has asked for them and not signed |
| Onboarding a new supplier | A new script, call or frame to another host is refused by the browser until the Content-Security-Policy names it, so the change is a reviewed pull request that must also update `subprocessors.md`. Images are not held to the list and are caught by review only. No written vendor risk assessment form exists: [Owner to confirm] |
| Periodic review | Not scheduled: [Owner to confirm cadence] |
| Supplier breach | Provider notices are a detection source in [breach-runbook.md](breach-runbook.md) §2; the mailbox each provider writes to is [Owner to confirm] |

## 6. People and training

| Item | Position |
|---|---|
| Joiner and leaver checklist (cloud IAM, GitHub, Secret Manager, database grants) | not written: [Owner to confirm] |
| Confidentiality terms for staff and contractors | [Owner to confirm] |
| Security and data protection training | none documented; decision G5 open. [Owner to confirm: content, frequency, record of attendance] |
| Review of code by a second person | pull-request review is the practice (`pr-cleanup` skill); whether branch protection requires it, and whether `Dependencies` and `Container image` are required status checks on `main`, is [Owner to confirm] |

## 7. Incidents

[breach-runbook.md](breach-runbook.md) is the incident response and breach notification procedure: roles, detection
sources, containment, severity S1 to S3, who is told and when, notice templates, the breach register and a yearly
tabletop. Its register is empty and no tabletop has been run. The deadlines for UAE regimes (PDPL, DIFC, ADGM) are not
yet in it: [Owner to confirm with counsel].

## 8. Related documents

[access-control-policy.md](access-control-policy.md), [backup-and-recovery.md](backup-and-recovery.md),
[data-flow-map.md](data-flow-map.md), [subprocessors.md](subprocessors.md), [retention-schedule.md](retention-schedule.md),
[subject-requests-procedure.md](subject-requests-procedure.md), [ropa.md](ropa.md), [tia.md](tia.md).

## 9. Review

This policy is reviewed [Owner to confirm: yearly] and after any S1 or S2 incident. Last reviewed: never (draft).

## 10. Gaps

1. No named security owner, incident lead or DPO; no monitored security mailbox (A2, A3, A7).
2. Humans can be granted direct database access to every tenant, from any Google account, outside the app's audit
   trail (gap G1); no break-glass procedure yet (`data-protection-plan.md` 1.1).
3. No Data Access audit logs on Cloud SQL, Cloud Storage or Secret Manager.
4. Alerts go to personal inboxes (`ALERT_EMAILS` in `ops/gcp/monitoring.sh`), and no alert fires on any audit event.
5. Automated backups and point-in-time recovery are off ([backup-and-recovery.md](backup-and-recovery.md)).
6. Two-factor exists but is behind `MFA_ENABLED`, with no per-workspace enforcement, no SSO and no SCIM.
7. No customer-managed encryption keys (CMEK).
8. No staff training, joiner/leaver checklist, supplier risk assessment or supplier review cycle written down.
9. Supplier DPAs and transfer mechanisms unrecorded; several supplier regions unknown.
10. No ISO 27001 or SOC 2 certification and no penetration test.
11. The people controls (`DATA_PROTECTION_ENABLED`, `SUPPRESSION_LIST_ENABLED`, `RETENTION_ENABLED`) are off in
    production (`ropa.md` §3).
12. Cloud Run's request log keeps full URLs, including `?q=` searches, for 30 days (decision F10).

## 11. Summary for clients

_Not to be sent as written: confirm each line is still true on the day, and add the DPA and sub-processor list._

- **Separate workspaces.** Each firm's data is kept apart in the application and, as a second lock, by row-level
  security in the database.
- **Role-based access.** Permissions are checked per action on every request. Client representatives are read-only and
  see only the positions they are attached to, never notes, documents, tasks or assessments.
- **Encryption.** Data is encrypted in transit (TLS) and at rest (AES-256, Google-managed keys). Mailbox credentials
  and second-factor secrets get an additional layer of encryption in the application.
- **Hosting.** Google Cloud, `us-central1` (United States). AI features use Google Vertex AI, which serves from a
  global endpoint, and Google states it does not train on customer data. Nothing is hosted in the UAE today.
- **AI minimisation.** Email addresses and phone numbers are removed from what AI models read; pay and custom fields
  are never sent.
- **Audit and logging.** Security-relevant actions are recorded in an append-only audit trail that firm admins can
  read; personal data is masked in application logs.
- **Sub-processors.** The list of providers that receive personal data, and where, is [subprocessors.md](subprocessors.md)
  (publish alongside the DPA).
- **Honest limits.** Uncava is not yet ISO 27001 or SOC 2 certified, two-factor sign-in is being rolled out, and
  automated backups are being switched on [confirm status on the day].
