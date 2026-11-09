# Uncava data protection plan

_Drafted 2026-10-28 from a review of the codebase and ops scripts. Internal — not for clients as written._

## 1. Goal

Clients (search firms and in-house HR teams) ask three things before they put executives' data into Uncava:

1. **Can other firms see our data?**
2. **Can Uncava's own team see our data?**
3. **What happens to the data — where it lives, who processes it, how it is deleted?**

The goal is to answer all three truthfully, in writing, with evidence — first with what we have, then with an
Enterprise tier that gives the customer control (their SSO, their region, their keys, their approval for any
access by us).

## 2. Where we stand today

### Strengths (say these with confidence)

| Area | What exists |
|---|---|
| Tenant isolation (app) | Every workspace query filters by the session's `wsId` (`AuthPrincipal.requireWorkspaceId()`, ~290 call sites); guards re-read the DB on every check; JWT roles never trusted. |
| Access inside a firm | Seat-based `WORK_VIEW` / `WORK_EXECUTE`; client seats read-only and blind to notes, assessments, `added_by`, outreach, tasks. |
| Audit trail | Exports, downloads, public API reads, AI runs, settings changes audited; `app_lm_audit_event` append-only at DB level (`ops/cloudsql/harden.sql`). |
| DB least privilege | `lm_app` stripped of `cloudsqlsuperuser`; Apollo universe and credit ledger read/append-only. |
| Secrets | Tokens stored as SHA-256; own-app OAuth secrets and mailbox/Zoom refresh tokens Tink-encrypted, keyset in Secret Manager, bound to workspace. |
| Documents | Private GCS bucket, uniform access, public access prevention, no versioning; streamed by the API, never signed URLs. |
| AI | Vertex AI (no training on customer data); contacts, compensation and custom fields never sent; emails/phones redacted from notes, CVs, transcripts (`ContactRedaction`); prompt-injection guard. |
| Supply chain | OSV + container scanning in `security.yml`, Dependabot. |
| Auth hygiene | Login lockout, rate limits, vague auth errors, rotating refresh cookie, access token in memory only. |

### Gaps

| # | Gap | Evidence | Client impact |
|---|---|---|---|
| G1 | Humans get direct DB access to **all tenants**, any Google account (consumer included), read or write, outside the app's audit trail. | `ops/cloudsql/grant-db-user.sh` | Fails "can your developers see our data?" |
| G2 | Hosting in `us-central1`; Vertex at `location: global`. | `deploy.yml` `REGION`, `application.yml` | GCC residency questions (UAE/KSA PDPL). |
| G3 | Workspace deletion is soft; all data stays. | `WorkspaceSettingsService.delete` | Contract-end deletion clause. |
| G4 | No erasure of an executive (`app_lm_person`); "remove" only unmaps. | `CandidateController` DELETE | GDPR/PDPL data-subject requests. |
| G5 | No retention policy for people, audit log, transcripts by default. | — | Every DPA asks. |
| G6 | No MFA, SAML/OIDC SSO, or SCIM. | — | First enterprise checkbox. |
| G7 | No DB-level isolation (Postgres RLS). | no `CREATE POLICY` anywhere | Defence in depth on questionnaires. |
| G8 | PII encrypted only by Google default at rest; no CMEK/BYOK. | — | Enterprise "own keys" ask. |
| G9 | No DPA, subprocessor list, security overview, incident/breach policy, certification, pen test. | — | Procurement blocker. |
| G10 | Backups / PITR / restore drill not documented. | instance predates scripts | "What is your RPO/RTO?" |
| G11 | No customer-visible audit log or support-access control. | — | "Show us who accessed our data." |

## 3. Phase 1 — now (~2 weeks): paperwork and lockdown

Mostly non-code. Unblocks client conversations immediately.

| Item | Closes | Done when |
|---|---|---|
| 1.1 **Lock down production DB access.** Remove existing human grants; production access only by break-glass: named person, reason, time-boxed IAM grant (IAM Conditions with expiry), second-person approval, logged (Cloud Audit Logs + a written access log). Restrict `grant-db-user.sh` to the company Google Workspace domain and to the dev DB; debug on anonymised copies. | G1 | `gcloud projects get-iam-policy` shows no standing `cloudsql.instanceUser` humans on prod. |
| 1.2 **Enable Data Access audit logs** for Cloud SQL, GCS, Secret Manager; alert on any human principal. | G1 | Alert fires on a test access. |
| 1.3 **Pin Vertex AI to a region** (`GOOGLE_CLOUD_LOCATION`, e.g. `us-central1` now, matching hosting). | G2 | Config deployed; noted in the security overview. |
| 1.4 **Confirm backups + PITR**, retention (e.g. 7–30 days), run one restore drill; write RPO/RTO. | G10 | Drill recorded with timings. |
| 1.5 **DPA** (Uncava = processor, customer = controller): purpose, confidentiality, 72h breach notice, subprocessors, deletion/return within 30 days of termination, audit rights, SCCs/transfer terms. | G9 | Lawyer-reviewed template. |
| 1.6 **Subprocessor list** with what each receives: Google Cloud (hosting, Vertex AI), Bright Data, ContactOut, Mapbox, Nylas, Recall.ai, Stripe, Resend, Cloudflare; Google/Microsoft/Zoom (customer's own mail/calendar). | G9 | Published page or PDF. |
| 1.7 **Security overview** (2 pages) from §2 strengths. | G9 | Sent to first client. |
| 1.8 **Policies**: incident response + breach notification, access control, data retention (initial values), vendor management. Use a compliance-platform template. | G5, G9 | Signed off internally. |
| 1.9 **Manual deletion runbook** for workspace offboarding and executive erasure until Phase 2 ships. | G3, G4 | Runbook tested on dev. |
| 1.10 **Fill CAIQ-Lite / SIG-Lite once**, reuse per deal. | G9 | Stored answers. |

## 4. Phase 2 — 1–2 months: product work

Each item is its own PR; load the relevant skills (`lightmove-domain`, `java-spring-development`, `react`, `db-ops`).

| Item | Closes | Notes |
|---|---|---|
| 2.1 **MFA (TOTP + recovery codes)**, admin can enforce per workspace. | G6 | Password and OAuth paths; extension and MCP flows unaffected. |
| 2.2 **Executive erasure**: delete person, contacts, notes, documents (GCS objects), recordings, meetings, mappings, vendor-cache rows for the slug; keep a hashed suppression entry (slug/email hash) so no door re-adds them; audited. | G4 | `PersonMatcher` consults the suppression list. |
| 2.3 **Workspace offboarding**: full export (people, positions, notes, documents as a zip), then hard purge after N days from soft delete (`WorkspacePurge`, like `PersonNotePurge`), GCS objects included; deletion certificate. | G3 | Audit rows keep ids only. |
| 2.4 **Retention settings** per workspace: people untouched for X years, transcripts, audit log (min floor); scheduled purge. | G5 | Defaults from the Phase 1 policy. |
| 2.5 **Customer audit log page** (Settings → Security): admin reads/exports their workspace's audit events. | G11 | `WORKSPACE_MANAGE`. |
| 2.6 **Postgres row-level security** on tenant tables keyed on a session variable set per transaction (`SET LOCAL app.workspace_id`). | G7 | Backstop only; app filters stay. Needs care with schedulers and cross-tenant caches (vendor tables are not tenant-scoped). |
| 2.7 **SAML/OIDC SSO** per workspace (domain-verified), enforce-SSO toggle; then **SCIM** provisioning. | G6 | An IdP stays a config block, never a branch on a provider name. |
| 2.8 **Per-workspace vendor switches** (enrichment, notetaker, AI features) so a client can opt out of a subprocessor. | G9 | Surfaces as Settings → Data & AI. |

## 5. Phase 3 — 3–6 months: certification and Enterprise tier

| Item | Closes | Notes |
|---|---|---|
| 3.1 **ISO 27001** (preferred in GCC/EU) or **SOC 2 Type I → Type II** via Vanta / Drata / Secureframe. | G9 | 3–6 months; quarterly access reviews, training, vendor reviews become routine. |
| 3.2 **Annual third-party pen test** + shareable letter. | G9 | Before first Enterprise contract. |
| 3.3 **Support access, customer-approved**: no standing staff access; support sees a workspace only after its admin grants time-limited access in Settings; every view logged in their audit log. GCP Access Transparency / Access Approval for Google's own staff. | G1, G11 | The real answer to "even your developers can't see it". |
| 3.4 **Data residency**: region chosen at onboarding — e.g. `me-central2` (Dammam, KSA), `me-central1` (Doha) — with Vertex and GCS in the same region. Implemented as a regional stack (DB, bucket, Cloud Run) per region. | G2 | Check each subprocessor's regional options (Recall.ai, Nylas have EU/US). |
| 3.5 **CMEK / BYOK**: customer's key in their KMS. Dedicated stacks use native Cloud SQL/GCS CMEK; shared stack uses per-workspace envelope encryption of sensitive columns (pattern already in `core/crypto`). Revoking the key = data unreadable. | G8 | Expensive; Enterprise only. |
| 3.6 **Dedicated deployment** option (own GCP project, DB, bucket, keys, region). | G1, G2, G8 | Priced as an add-on; ops cost per stack. |
| 3.7 **Trust center page** (status, documents on request under NDA). | G9 | |

**Enterprise tier = SSO + SCIM + enforced MFA + audit export + customer-approved support access + residency +
CMEK, optional dedicated deployment.**

## 6. What we can tell clients at each stage

**Today** (only once 1.5, 1.6, 1.9 exist):

> Each firm's data is isolated in its own workspace with role-based access; clients and hiring managers are
> read-only to their own positions. Data is hosted on Google Cloud, encrypted in transit and at rest; documents
> are in private storage; mailbox credentials are encrypted. Sensitive actions are recorded in a tamper-proof
> audit trail. We use Google's enterprise AI — your data never trains a model, and contacts and pay never reach
> it. Our subprocessors are listed, we sign a DPA, and you can export your data and have it deleted when you leave.

On "can your team see our data?" (after 1.1):

> No one has standing access to production data. Access for an incident is time-limited, approved by a second
> person and logged. We are moving to customer-approved access visible in your own audit log.

**After Phase 3:**

> Isolated per firm at application and database level, hosted in the region you choose, encrypted with keys you
> control and can revoke. Nobody at Uncava can access it unless you grant time-limited support access, and every
> access appears in your audit log. Your SSO with enforced MFA, your retention, erasure on request, full export
> and certified deletion at exit. ISO 27001 / SOC 2 certified, pen-tested yearly, standard DPA and subprocessors.

## 7. Residual risks and decisions needed

**What stays true even after Phase 3**

- The running application reads the data (search, AI, enrichment, email need it). Protection is controls +
  logging, not mathematical impossibility. End-to-end encryption would break the product.
- Subprocessors receive what they need under their own terms — mitigated by DPAs with each and per-workspace
  switches (2.8).
- Certification is a yearly cost, and each dedicated deployment is another stack to upgrade and monitor.

**Decisions needed**

1. ISO 27001 or SOC 2 first? (Depends on where the first enterprise clients are.)
2. Which GCC region first, and does the first client need it before Phase 3?
3. Who owns security (named person) and who approves break-glass access?
4. Retention defaults: how long is an untouched executive kept? Audit log floor?
5. Is an executive's erasure global to the workspace, or can a firm keep them on a position under legitimate
   interest? (Legal input.)
6. Enterprise pricing and whether dedicated deployment is offered at all.
