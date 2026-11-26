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
workspace (`app_lm_workspace`), with the DPA its founder accepted for the firm where the deployment names one — the
version, the time and who (`dpa_version`, `dpa_accepted_at`, `dpa_accepted_by`, kept on the tombstone; #928).

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
logs on Cloud SQL, GCS or Secret Manager; no alert on any audit event; Vertex AI at `global` ([residency-adr.md](residency-adr.md) §1); the
`DATA_PROTECTION_ENABLED`, `SUPPRESSION_LIST_ENABLED` and `RETENTION_ENABLED` controls are off in production.

## UAE addendum (2026-11-19)

_Draft for counsel; not legal advice. Source: the Alac Partners thread of 2026-10-29 to 2026-11-19
([Uncava_Data_Protection_Raw_Emails.md](../GDPR-discusstion/Uncava_Data_Protection_Raw_Emails.md)). Nothing above this
heading has changed; it stays the EU GDPR record. Every legal reading below is a working assumption for counsel, marked
[Counsel to confirm] where it is not settled._

The lawyer's position is that the UAE has three regimes and that Uncava should be built to the highest common standard
rather than maintaining three versions: the PDPL's approach to consent combined with the DIFC and ADGM requirements on
service providers, documentation, transfers and AI.

| Regime | Instrument | What matters for this record |
|---|---|---|
| Federal | PDPL, Federal Decree-Law No. 45 of 2021 | Consent, or a listed exception (for example data the person made public). **No legitimate-interests basis.** Also reaches companies outside the UAE that process data of people in the UAE. Enforcement is still taking shape. |
| DIFC | Data Protection Law 2020, plus Regulation 10 on AI systems processing personal data (enforced from January 2026) | GDPR-like, with detailed duties on service providers. |
| ADGM | Data Protection Regulations 2021 | GDPR-like. |

### A. Lawful basis under the UAE regimes, row by row

The "Lawful basis" column of §1 is unchanged. This table adds two columns for the same activities, using the same row
IDs. A row marked **LI only** rests on legitimate interests today and has no PDPL equivalent: for those, a PDPL basis
(consent or a listed exception) has to be found or the processing changed.

| Activity | PDPL basis (consent / made-public exception / other) | DIFC / ADGM basis | LI only? |
|---|---|---|---|
| C1. User accounts and sign-in | Contract with the user; consent at sign-up for anything beyond it [Counsel to confirm that a contract exception exists and covers security logging] | Contract; legitimate interests (security) | Partly: the security limb |
| C2. Security audit trail | Legal obligation or another listed exception [Counsel to confirm]; otherwise none identified | Legitimate interests; legal obligation | **Yes** for the part not required by law |
| C3. Billing | Contract; legal obligation (tax records) [Counsel to confirm] | Contract; legal obligation | No |
| C4. Account email | Contract | Contract | No |
| C5. Vendor caches and sourcing | **Made-public exception** for professional profile data the person published (the lawyer's "moderate risk", message 2). **Consent** for private email and phone from ContactOut, which the person did not publish: "no clear legal basis" (the lawyer's "high risk"). Whether the exception reaches a cache shared across every workspace is [Counsel to confirm] | Legitimate interests with a balancing test (LIA §4); the made-public fact weighs in it | **Yes** (the basis in §1), so the PDPL basis above must be built |
| C6. Product logs | No consent-based route is practical; [Counsel to confirm] whether a security or operational exception applies | Legitimate interests | **Yes** |
| C7. Subject-request handling | Legal obligation to answer the request | Legal obligation | No |
| C8. Website | [see the website repo]; consent for cookies and forms [Counsel to confirm] | [see the website repo] | [Unknown] |
| P1 to P9 (Uncava as processor) | The customer is the controller and holds the PDPL basis. For a candidate that basis is the consent statuses proposed on 2026-11-10 (Grey: no consent, public profile only, 6 months; Light green: current process; Dark green: future roles, 24 months; Orange: expired, 3 months; Red: withdrawn, name and link only on a do-not-contact list) or the made-public exception. The consent register that holds them is **not built** (#1267 phases) | The customer's own basis; Uncava acts on documented instructions under a DPA that meets DIFC and ADGM service-provider terms [Counsel to confirm the terms] | Depends on the customer |

Rows that need a decision before a UAE launch: **C5** (the core sourcing basis), **C2** and **C6** (no consent
equivalent), and **P4** and **P6** (see [dpia.md](dpia.md), UAE addendum, for the AI changes agreed on 2026-11-19).

### B. ContactOut, a second controller of what it returns

The main text lists ContactOut only as a source in C5. The lawyer's reading (message 2) is that because Uncava buys
ContactOut data and passes it on to clients, **Uncava is a controller of that data, not a pass-through tool**.
ContactOut is a separate controller of the same data. Two relationships:

| Relationship | Data | Role of ContactOut | Instrument |
|---|---|---|---|
| Data ContactOut returns to us (profiles, work email, phone, titles, companies) | what its search and look-up endpoints return | **Independent controller**; Uncava becomes a controller on receipt | ContactOut DPA §3; controller-to-controller SCCs [Counsel to confirm which module and whether the UAE regimes accept them] |
| Names, LinkedIn URLs and search criteria we send | the query | **Our processor** | ContactOut DPA [Counsel to confirm the clause] |

Facts about ContactOut as stated by the vendor, which Uncava has not independently verified:

- Hosting is AWS `us-west-1`.
- Its stated sources are public directories, web crawl, user-contributed data and licensed data. **It keeps no
  per-record provenance**, so Uncava cannot say, for a given email or phone, where it came from. That weakens any reliance
  on the made-public exception for contact details.
- Storing ContactOut data at Uncava needs **ContactOut's written compliance approval**. Uncava requested it on
  2026-11-19; it is not granted. Until it is, the 30-day vendor cache in C5 (`lightmove.enrichment.people-cache-ttl`) and any
  contact copied onto a workspace person are an open question [Counsel to confirm].

Mitigations the lawyer listed: a contract with ContactOut confirming lawful collection and the right to pass data on;
client terms limiting use to recruitment, no onward passing, and naming the source in the first message; a public Uncava
privacy notice; and an automatic, non-removable outreach footer with the source and a notice link (#924 is built behind
`DATA_PROTECTION_ENABLED`). An alternative she raised is a client-owned ContactOut key, which would make Uncava a
processor and lower its risk at the cost of the business model.

**Proposed new rows** (for counsel to accept, not yet in §1):

| Activity | Purpose | PDPL basis | DIFC / ADGM basis | Recipients and transfers |
|---|---|---|---|---|
| C9. Contact details from ContactOut passed to workspaces | give a consultant a way to reach a mapped executive | Consent, or made-public exception for what the person published; **none identified for private email and phone** [Counsel to confirm] | Legitimate interests with a balancing test [Counsel to confirm] | ContactOut (AWS `us-west-1`) as independent controller; each workspace; see [tia.md](tia.md) UAE addendum |
| C10. Lookups sent to ContactOut | find a profile or contact | as C5 | as C5 | ContactOut as Uncava's processor |
