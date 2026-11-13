# Records of processing activities (Art. 30)

_Draft for counsel (#930, epic #917). Engineering facts read from the code on 2026-11-13; not legal advice._

Two records: Uncava as **controller** (Art. 30(1)) and as **processor** for its customers (Art. 30(2)). Retention is
not repeated here: every period is in [retention-schedule.md](retention-schedule.md), which a build test holds to the
schema. Recipients are in [subprocessors.md](subprocessors.md), transfers in [tia.md](tia.md), and the technical and
organisational measures in [security-of-processing.md](security-of-processing.md) and §3 below.

**Controller and contacts.** [Company legal name], [Registered address]. Privacy contact [Privacy email address].
DPO: [appointed / not required — see governance-decisions.md]. EU representative: [name and address]. UK
representative: [name and address].

## 1. Uncava as controller

| Activity | Purpose | Lawful basis ⚖️ | Data subjects | Personal data | Recipients | Transfers | Retention |
|---|---|---|---|---|---|---|---|
| C1. User accounts and sign-in | provide and secure the service | contract; legitimate interests (security) | consultants, admins, client representatives with an account | name, work email, phone, LinkedIn URL, picture, sign-in identities, second factor (sealed), sessions with IP and browser, login failures, terms and privacy-version acceptance (`app_lm_user` and the `_user_*` tables) | Google Cloud, Resend, Google and LinkedIn sign-in | US | `retention-schedule.md`, account rows |
| C2. Security audit trail | detect misuse, answer customers' audit questions | legitimate interests; legal obligation ⚖️ | users; candidates by id only | actor id, target ids, IP, user agent (`app_lm_audit_event`); details carry ids only (#927) | Google Cloud | US | statutory floor ⚖️ (#256) |
| C3. Billing | charge for seats and credits | contract; legal obligation (tax records) | the paying admin | workspace name and id; the card, address and tax id stay with Stripe; usage events and the credit ledger | Stripe, Google Cloud | US | credit ledger statutory ⚖️; usage 400 days ⚖️ |
| C4. Account email | send verification, reset, invitation and service notices | contract | users and invitees | name, email | Resend | [Region] | not stored by Uncava beyond the token tables |
| C5. Vendor caches and sourcing | find and pre-fill executive profiles for every workspace | legitimate interests (LIA §4) ⚖️ | executives in vendor data | profile slug and the vendor's profile record (`app_lm_vendor_person`, `app_lm_vendor_people_search`); companies (`app_lm_vendor_company`) | Bright Data, HarvestAPI, ContactOut (as sources) | US | 30 days (`lightmove.enrichment.people-cache-ttl`) |
| C6. Product logs | run and fix the service | legitimate interests | users; anyone named in a search URL | masked application lines; Cloud Run's request URLs including `?q=` | Google Cloud Logging | global | 30 days |
| C7. Subject-request handling | answer requests made to Uncava | legal obligation | anyone who writes in | what the requester sends; the request log ([subject-requests-procedure.md](subject-requests-procedure.md)) | the controllers the request is forwarded to | — | [request-log period ⚖️] |
| C8. Website | marketing, demo requests | [see the website repo] | visitors | [hosting logs; any form data] | Firebase Hosting [and form processor] | [Region] | [website privacy policy] |

C5 is the one that rests on the hardest legal question: whether Uncava, not only its customers, is a controller of
executive data it caches across workspaces. It is recorded here as controller because the cache is shared by every
workspace and no single customer decides its purpose (#917, "Why").

## 2. Uncava as processor (Art. 30(2))

**Controllers:** each customer firm, for the data in its own workspace. Name and contact details are on the
workspace (`app_lm_workspace`) and the signed DPA [DPA record: #928].

**Categories of processing carried out on each controller's behalf:**

| Processing | Data subjects | Personal data |
|---|---|---|
| P1. Candidate database and mapping | executives the firm maps | `app_lm_person` (name, title, seniority, LinkedIn URL and slug, location, summary, career and education profile), contacts, photo, tags, positions mapped (`app_lm_project_candidate`), custom columns |
| P2. Compensation | executives | base, bonus, allowances, long-term incentive, notice period, breakdown |
| P3. Team notes, documents, tasks | executives; anyone named in them | notes, CVs and references (GCS), tasks, the timeline |
| P4. AI readings and assessments | executives | AI-proposed gender, nationality, years of experience, seniority (flagged in `ai_inferred_fields`), the nationality reading, assessment drafts and scorecards ([dpia.md](dpia.md) §4) |
| P5. Outreach | executives | sequences, emails both ways, attachments, meetings booked |
| P6. Notetaker | executives and consultants on the call | recordings' transcripts, call notes, suggested changes, live copilot lines |
| P7. Client access | client representatives | name, email, the mandates they read |
| P8. Reports and exports | executives | the position report (including diversity counts), CSV and workspace exports |
| P9. Public API and MCP | executives | read-only, by scope, to recipients the firm chooses |

**Sub-processors:** [subprocessors.md](subprocessors.md). **Transfers:** to the US for all of the above ([tia.md](tia.md)).
**Retention:** the firm's own choice for candidates (12–60 months or never, reviewed by an admin), the fixed periods in
[retention-schedule.md](retention-schedule.md) for the rest, and the workspace purge 30 days ⚖️ after deletion.

## 3. Technical and organisational measures (Art. 32), in summary

| Measure | Where |
|---|---|
| Tenant isolation in every query, and forced Postgres row-level security as the backstop | `AuthPrincipal.requireWorkspaceId()`; V184, V190, V192; `docs/security/row-level-security.md` |
| Authorisation by action, re-read from the database each check; client seats read-only and blind to staff-only data | `@workspaceAuthorizer`, `@projectAuthorizer` |
| Append-only audit trail with ids only | `ops/cloudsql/harden.sql`; `AuditDetailKeysTest` |
| Tokens stored as SHA-256; provider secrets and second-factor secrets sealed with Tink; suppression entries as keyed hashes | `core/crypto` |
| Encryption at rest (Google-managed keys) and in transit (TLS) | [security-of-processing.md](security-of-processing.md) |
| Personal data masked in deployed logs | `PersonalDataLogMasker` |
| Contacts taken out of what AI models read; compensation and custom columns never sent | `ContactRedaction`, `CandidateDossier` |
| Data subject rights tooling: erasure, restriction, suppression, access export, account deletion, workspace purge | [use-cases.md](use-cases.md) |
| Login lockout, rate limits, two-factor | `LoginLockout`, `Bucket4jRateLimiter`; `MFA_ENABLED` |

**Measures not yet in place** (also in [dpia.md](dpia.md) §5): automated backups and point-in-time recovery are off;
humans can be granted direct access to every tenant (`ops/cloudsql/grant-db-user.sh`, gap G1); no Data Access audit
logs on Cloud SQL, GCS or Secret Manager; no alert on any audit event; Vertex AI not pinned to a region; the
`DATA_PROTECTION_ENABLED`, `SUPPRESSION_LIST_ENABLED` and `RETENTION_ENABLED` controls are off in production.
