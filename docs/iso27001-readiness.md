# ISO/IEC 27001:2022 readiness — Uncava (GCC market)

Status: **draft gap assessment**, written against the repository at `main` on 2026-10-29. It is the starting
point for an ISMS, not a certificate claim: every "Implemented" row below is evidenced by code or config in this
repository, and everything an auditor would ask for that lives outside the repository (policies, HR records,
supplier contracts, GCP console settings) is marked as a gap until someone produces it.

GCC regulation is summarised for planning only and must be confirmed with counsel in each jurisdiction before a
customer commitment is made.

## 1. Scope (proposed)

| Item | Proposed scope |
|---|---|
| Service | Uncava SaaS — the API (`apps/api`), the SPA (`apps/web`), the Capture extension (`apps/extension`), the public API and the MCP server |
| Hosting | Google Cloud: Cloud Run, Cloud SQL Postgres 16 (`bright-gcc`), Cloud Storage (person documents, outreach attachments), Secret Manager, Vertex AI |
| Locations | Today: `us-central1` only (`ops/gcp/bootstrap.sh`, `deploy.yml`). Target: a GCC region — see §4 |
| Information | Workspace (tenant) data: positions, client records, executives' profiles, contacts, compensation, notes, documents, call transcripts, emails; account and billing data |
| People | Engineering and operations staff with repository, GCP or database access |
| Out of scope (initially) | Customers' own endpoints and mailboxes; the Apollo ETL pipeline (separate system — decide whether to include) |

## 2. ISMS clauses 4–10

None of the management-system clauses is evidenced in the repository. They are the first workstream, because an
auditor will not look at Annex A until these exist.

| Clause | Requirement | Status | What to produce |
|---|---|---|---|
| 4.1–4.2 | Context, interested parties | ❌ | Context register: GCC customers, executives whose data we hold, regulators (§4), subprocessors (§5) |
| 4.3 | ISMS scope | 🟡 | Ratify §1 of this document |
| 4.4 | ISMS established | ❌ | ISMS manual linking everything below |
| 5.1–5.3 | Leadership, policy, roles | ❌ | Signed Information Security Policy; named ISMS owner, DPO/privacy lead, security lead |
| 6.1 | Risk assessment and treatment | ❌ | Risk methodology, risk register, risk treatment plan, **Statement of Applicability** (§3 is its draft) |
| 6.2 | Objectives | ❌ | Measurable objectives (e.g. critical CVEs fixed in 7 days, restore test each quarter) |
| 6.3 | Planning of changes | ❌ | Change procedure for the ISMS itself |
| 7.1–7.5 | Resources, competence, awareness, communication, documented information | ❌ | Training records, document control (this `docs/` folder under PR review can serve as controlled documents) |
| 8.1–8.3 | Operational planning, risk assessment and treatment in operation | ❌ | Evidence that the risk process runs |
| 9.1 | Monitoring and measurement | ❌ | Metrics against 6.2 |
| 9.2 | Internal audit | ❌ | Audit programme and one completed internal audit before stage 1 |
| 9.3 | Management review | ❌ | One minuted management review before stage 1 |
| 10.1–10.2 | Improvement, nonconformity | ❌ | Nonconformity / corrective-action log (GitHub issues with a label is acceptable) |

## 3. Annex A — draft Statement of Applicability

Legend: ✅ implemented and evidenced in the repo · 🟡 partial · ❌ gap · ☁️ inherited from Google Cloud (cite the
GCP ISO 27001 certificate and SOC 2 report) · ➖ proposed not applicable (justify in the SoA).

### 5. Organisational controls

| # | Control | Status | Evidence / gap |
|---|---|---|---|
| 5.1 | Policies for information security | ❌ | No policy set. Write the top-level policy and topic policies (access, crypto, supplier, backup, incident, SDLC, acceptable use) |
| 5.2 | Roles and responsibilities | ❌ | No named roles |
| 5.3 | Segregation of duties | 🟡 | Separate deploy and runtime service accounts (`ops/gcp/bootstrap.sh`); separate `lm_migrate` and `lm_app` DB roles (`ops/cloudsql/create-migrate-role.sh`, `harden.sql`); releases are cut from a tagged build (`release.yml`). Gap: no CODEOWNERS or recorded branch-protection rule enforcing a second reviewer |
| 5.4 | Management responsibilities | ❌ | |
| 5.5 | Contact with authorities | ❌ | List each GCC regulator and breach-notification contact (§4) |
| 5.6 | Contact with special interest groups | ❌ | |
| 5.7 | Threat intelligence | 🟡 | OSV-Scanner and Trivy feeds (`security.yml`), Dependabot security updates (`.github/dependabot.yml`). Gap: no documented threat-intel process |
| 5.8 | Information security in project management | 🟡 | Security invariants enforced per change (`CLAUDE.md`, `lightmove-domain` skill); PR template asks for verification. Gap: no security sign-off step in the PR template |
| 5.9 | Inventory of assets | ❌ | Produce an asset register: GCP projects, Cloud SQL instance, buckets, secrets, OAuth apps, domains, third-party accounts (§5) |
| 5.10 | Acceptable use | ❌ | |
| 5.11 | Return of assets | ❌ | |
| 5.12 | Classification of information | 🟡 | The code already separates staff-only data (notes, assessments, transcripts, `added_by`) from what a client seat reads (`CandidateResponse`), and the public API narrows to public DTOs. Gap: no written classification scheme mapping to this |
| 5.13 | Labelling of information | ❌ | Define labels for exports and reports (exports are audited — `dataexport`) |
| 5.14 | Information transfer | 🟡 | One origin, TLS only; CSV export audited and capped (`lightmove.export.*`); documents streamed by the API, never signed URLs. Gap: transfer policy, customer-facing DPA |
| 5.15 | Access control | ✅ | Action-based authorisation, never role-based (`@PreAuthorize` with `@workspaceAuthorizer`/`@projectAuthorizer`, re-read from the DB each check); tenant isolation through `AuthPrincipal.requireWorkspaceId()`; seat-gated `WORK_VIEW`/`WORK_EXECUTE`. Needs a written access-control policy that points at this |
| 5.16 | Identity management | ✅ | Work-email identity, invitation-only membership, verified email required (`lightmove-domain` skill) |
| 5.17 | Authentication information | 🟡 | BCrypt passwords (`core/security/service/PasswordPolicy.java`); every token stored as SHA-256 only (`RefreshToken`, `VerificationToken`, `ApiKey`, `HashingAuthorizationService`, `Invitation`); API keys shown once. Gap: password rule is 8 characters + one number — raise to 12 and screen against breached passwords |
| 5.18 | Access rights | 🟡 | Roles and seats managed in-app and audited; API keys expire (90-day default, 365 max). Gap: no periodic access review of staff GCP, GitHub and database access |
| 5.19 | Supplier relationships | ❌ | Supplier policy and due-diligence record for each subprocessor in §5 |
| 5.20 | Supplier agreements | ❌ | DPAs and security terms on file for each subprocessor |
| 5.21 | ICT supply chain | 🟡 | Dependency and image scanning (`security.yml`), Dependabot. Gap: GitHub Actions pinned to tags, not commit SHAs; Dependabot auto-merge (`dependabot-automerge.yml`) needs a documented rule |
| 5.22 | Supplier service monitoring | ❌ | Annual review of each supplier's certificates |
| 5.23 | Use of cloud services | 🟡 | GCP with Workload Identity Federation, no stored service-account keys (`deploy.yml`), per-secret `secretAccessor` grants (`bootstrap.sh`). Gap: cloud-service policy, GCP org policies and region constraint not recorded |
| 5.24 | Incident management planning | ❌ | Incident response plan with GCC notification clocks (§4) |
| 5.25 | Assessment of events | ❌ | Severity matrix |
| 5.26 | Response to incidents | ❌ | Runbooks: credential leak, tenant data exposure, vendor breach, key compromise (JWT, MCP, Tink keyset rotation) |
| 5.27 | Learning from incidents | ❌ | Post-incident review template |
| 5.28 | Collection of evidence | 🟡 | Append-only audit trail (`app_lm_audit_event`, trigger in `V1__auth_core.sql`, `lm_app` INSERT/SELECT only via `harden.sql`). Gap: no log retention or export for forensics |
| 5.29 | Security during disruption | ❌ | |
| 5.30 | ICT readiness for business continuity | ❌ | BCP/DR with RTO/RPO; Cloud SQL backups and PITR are an open item in `docs/production-observability.md` |
| 5.31 | Legal and regulatory requirements | ❌ | Legal register — start from §4 |
| 5.32 | Intellectual property | 🟡 | Third-party data used under vendor terms (e.g. Mapbox storage limits honoured by `lightmove.mapbox.cache-ttl` unless permanent geocoding is licensed). Gap: licence register, OSS licence scan |
| 5.33 | Protection of records | 🟡 | Audit and credit ledger append-only by trigger and by grant; financial records outlive the workspace (billing FKs do not cascade). Gap: records retention schedule |
| 5.34 | Privacy and protection of PII | 🟡 | Staff-only fields kept off client-readable responses; contacts and compensation never sent to the model (`CandidateDossier`, `ContactRedaction`); public API needs separate scopes for contacts and compensation; privacy-policy version recorded at signup (`PrivacyPolicy.java`). Gaps: no privacy notice text, no data-subject request procedure, no user or person erasure flow, no ROPA, no DPIA — see §4 |
| 5.35 | Independent review | ❌ | External penetration test and internal audit |
| 5.36 | Compliance with policies | ❌ | |
| 5.37 | Documented operating procedures | 🟡 | `README.md`, `ops/` scripts, `docs/integrations/registration.md`, release/rollback through `deploy.yml`. Gap: backup/restore, on-call and key-rotation runbooks |

### 6. People controls

All are organisational records outside the repository.

| # | Control | Status | Gap |
|---|---|---|---|
| 6.1 | Screening | ❌ | Background checks proportionate to access (GCC customers often require them for anyone with production access) |
| 6.2 | Terms and conditions of employment | ❌ | Security clauses in contracts |
| 6.3 | Awareness and training | ❌ | Annual training with records |
| 6.4 | Disciplinary process | ❌ | |
| 6.5 | Responsibilities after termination | ❌ | Offboarding checklist: GitHub, GCP, Secret Manager, DB grants, vendor dashboards |
| 6.6 | Confidentiality agreements | ❌ | NDAs, including contractors |
| 6.7 | Remote working | ❌ | Remote-working policy |
| 6.8 | Event reporting | ❌ | Internal reporting channel; also a public `SECURITY.md` for external researchers |

### 7. Physical controls

The data centres are Google's (☁️, cite GCP's certificates). Offices and staff laptops remain in scope unless
justified out.

| # | Control | Status |
|---|---|---|
| 7.1–7.6 | Perimeters, entry, offices, monitoring, environmental, secure areas | ☁️ for hosting; ➖ or ❌ for offices — decide scope |
| 7.7 | Clear desk and clear screen | ❌ |
| 7.8 | Equipment siting | ☁️ |
| 7.9 | Assets off-premises | ❌ — laptop policy |
| 7.10 | Storage media | ❌ — no customer data on removable media; local dev uses its own Docker database (`npm run dev`) |
| 7.11–7.13 | Utilities, cabling, maintenance | ☁️ |
| 7.14 | Secure disposal | ☁️ for hosting; ❌ for laptops |

### 8. Technological controls

| # | Control | Status | Evidence / gap |
|---|---|---|---|
| 8.1 | User endpoint devices | ❌ | Laptop baseline: disk encryption, screen lock, OS updates, MDM |
| 8.2 | Privileged access rights | 🟡 | `lm_app` stripped of `cloudsqlsuperuser` (`harden.sql`); platform `SUPER_ADMIN` granted only by `ops/cloudsql/grant-platform-role.sh`, audited, never in the JWT. Gap: a list of who holds GCP Owner/Editor and DB `postgres`, and how often it is reviewed |
| 8.3 | Information access restriction | ✅ | Tenant isolation per query, seat-gated project content, client seats read-only and scoped to their mandates, staff-only fields off client responses |
| 8.4 | Access to source code | 🟡 | Private GitHub repository. Gap: CODEOWNERS, branch protection recorded, mandatory 2FA on the GitHub org |
| 8.5 | Secure authentication | 🟡 | Lockout after 5 failures for 15 minutes (`LockoutSettings.java`), per-endpoint rate limits (`auth.rate-limit.*`), 15-minute access tokens held in memory, rotating refresh tokens with replay detection, `HttpOnly; Secure; SameSite=Strict` cookie, OAuth 2.1 with PKCE for MCP. **Gap: no MFA** — `TwoFactorCard.tsx` is a placeholder. Most GCC enterprise questionnaires ask for MFA; this is a top-priority fix. Rate limits are per instance (Bucket4j in memory) |
| 8.6 | Capacity management | 🟡 | `--max-instances 3`; fair-use ceilings and LLM budgets per seat. Gap: no capacity monitoring (`db-f1-micro` per `docs/production-observability.md`) |
| 8.7 | Protection against malware | 🟡 | Uploaded files accepted only where name and bytes agree (`DocumentFormat`); documents streamed, never served as links. Gap: no antivirus scan of uploads; endpoint protection on laptops |
| 8.8 | Technical vulnerabilities | 🟡 | OSV-Scanner (npm, Maven) and Trivy (image) on every PR and weekly, CRITICAL blocks (`security.yml`); Dependabot security updates; `apk upgrade` in the image. Gaps: scans block only once set as required checks; no SLA for HIGH; no pen test |
| 8.9 | Configuration management | 🟡 | Config in `application.yml` under review; deploy is scripted (`deploy.yml`, `ops/gcp/`). Gap: no infrastructure-as-code, so GCP settings are not under change control |
| 8.10 | Information deletion | 🟡 | Deleted notes purged after 30 days (`PersonNotePurge`, `CompanyNotePurge`); vendor caches aged out at 30 days and purged on every billed call; geocodes per Mapbox terms; OAuth grants purged; workspace delete gated by typing its name. Gaps: call transcripts kept forever by default (`lightmove.recall.transcript-retention` blank — set it); no user-account deletion; no procedure for erasing one person across a workspace; frozen V91 columns still hold copies of every executive until #606 drops them |
| 8.11 | Data masking | ✅ | Contacts and compensation never reach the model; `ContactRedaction` strips emails and phones from notes, documents and transcripts before any AI call; bodies and attachment bytes never logged (`EmailAttachment.toString`) |
| 8.12 | Data leakage prevention | 🟡 | Exports audited and capped; public API and MCP narrow to public DTOs, never notes or assessments; MCP refuses token passthrough. Gap: no DLP policy or alert on bulk export |
| 8.13 | Information backup | ❌ | Cloud SQL automated backups and point-in-time recovery are not evidenced (open item in `docs/production-observability.md`); no GCS object versioning recorded; no restore test |
| 8.14 | Redundancy | 🟡 | Cloud Run spans zones within a region (☁️). Gap: single region, single Cloud SQL instance with no HA recorded |
| 8.15 | Logging | 🟡 | JSON logs (`logback-spring.xml`); append-only security audit trail; per-person activity log (`app_lm_person_activity`); every public API read, MCP tool call, export and download audited. Gaps: no access log, no log retention set |
| 8.16 | Monitoring activities | ❌ | No alerting or uptime checks (`docs/production-observability.md` lists the work) |
| 8.17 | Clock synchronisation | ☁️ | Google-managed NTP; all stored times UTC |
| 8.18 | Privileged utility programs | 🟡 | DB access through `ops/cloudsql/psql.sh` with IAM. Gap: record who may use it and when |
| 8.19 | Software on operational systems | ✅ | Immutable container images built in CI; Flyway runs as a separate step, off at boot |
| 8.20 | Networks security | 🟡 | ☁️ for the platform; Cloud SQL through the connector; CSP header (`SecurityConfig.java`). Gaps: no explicit HSTS; no Cloud Armor / WAF |
| 8.21 | Security of network services | 🟡 | Webhooks authenticated by signature (Stripe, Svix for Recall, HMAC for Nylas), blank secret refuses every delivery; outbound metadata fetches restricted to public addresses (`HttpClientMetadataFetcher`) |
| 8.22 | Segregation of networks | ☁️ / ❌ | No VPC or private-IP Cloud SQL recorded |
| 8.23 | Web filtering | ➖ | Justify for staff browsing, or cover by endpoint policy |
| 8.24 | Use of cryptography | 🟡 | Tink envelope encryption (AES-256-GCM) for OAuth secrets and refresh tokens, bound to workspace and purpose (`core/crypto`); RSA-2048 JWT keys in Secret Manager, a separate key for MCP; SHA-256 token hashes; TLS by Cloud Run; GCP default encryption at rest (☁️). Gaps: crypto/key-management policy and rotation schedule; CMEK if a customer requires it |
| 8.25 | Secure development life cycle | 🟡 | PR review, CI tests on every PR, migration-version check, security invariants documented (`CLAUDE.md`, skills). Gap: written SDLC policy |
| 8.26 | Application security requirements | ✅ | RFC 9457 errors, deliberately vague auth errors, server-side validation (`docs/validation-matrix.md`), prompt-injection guard on AI inputs (`docs/llm-guardrails.md`) |
| 8.27 | Secure architecture | ✅ | One origin, `/api/v1` only; separate security chains for session, API key and MCP; audience-bound MCP tokens; LLM guardrails |
| 8.28 | Secure coding | 🟡 | Conventions in skills; parameterised queries via JPA. Gap: no SAST (CodeQL is only used to upload results) and no secret scanning (gitleaks) |
| 8.29 | Security testing | 🟡 | Integration tests on real Postgres (Testcontainers), e2e matrix, contract tests for public API and MCP. Gap: annual third-party pen test; DAST |
| 8.30 | Outsourced development | ➖ / ❌ | Record whether contractors or AI coding agents contribute and how their changes are reviewed |
| 8.31 | Separation of environments | 🟡 | Local dev database per developer (`npm run dev`); a shared dev Cloud SQL database (`npm run dev:cloud`). Gap: confirm production is a separate instance and project, and record it |
| 8.32 | Change management | 🟡 | Every change via PR, CI, tagged release, manual deploy, rollback by redeploying the previous tag; Flyway migrations never edited once applied. Gap: required reviews not enforced in repo config |
| 8.33 | Test information | 🟡 | Seeds and e2e use synthetic data; the nationality golden set stays out of the repo (`ops/eval/export-golden.sh`). Gap: policy that production personal data is never copied to dev — the golden-set export takes real profiles and needs a recorded approval |
| 8.34 | Protection during audit testing | ❌ | Rules for pen-test and audit access |

## 4. GCC regulatory overlay

Uncava processes personal data of executives across the GCC who have not signed up to the product — profiles
bought from data vendors, contacts, compensation, nationality and gender, call recordings. This is the
heaviest exposure under every GCC data-protection law and should drive the risk register.

| Jurisdiction | Law / framework | What it means for Uncava |
|---|---|---|
| UAE (federal) | Federal Decree-Law No. 45 of 2021 (PDPL) | Lawful basis for processing executives' data, data-subject rights, breach notification to the UAE Data Office, cross-border transfer rules |
| UAE — DIFC | DIFC Data Protection Law No. 5 of 2020 | Applies to DIFC-based customers; DPO and breach notification to the Commissioner |
| UAE — ADGM | ADGM Data Protection Regulations 2021 | As above for ADGM customers |
| UAE — Dubai / government | Dubai ISR, UAE IA Standard | Only if government entities become customers |
| Saudi Arabia | Personal Data Protection Law (PDPL), enforced by SDAIA since Sept 2024, with its Transfer Regulations | Consent is the main basis; transfers outside the Kingdom need an adequacy finding or safeguards; 72-hour breach notification; a registered representative may be required |
| Saudi Arabia | NCA Essential Cybersecurity Controls (ECC) and Cloud Cybersecurity Controls (CCC) | Required for government and critical-national-infrastructure customers, and often asked for by large private ones; CCC expects hosting in the Kingdom for those customers |
| Qatar | Law No. 13 of 2016 (PDPPL), NCSA guidance; QFC Data Protection Regulations | Data-subject rights, breach notification, special-nature data (ethnicity, health) needs a permit |
| Bahrain | Law No. 30 of 2018 (PDPL) | Registration and notification duties to the Personal Data Protection Authority |
| Oman | Royal Decree 6/2022 (PDPL) and its 2024 executive regulations | Permits for sensitive data; transfer conditions |
| Kuwait | CITRA Data Privacy Protection Regulation (2024) | Applies to service providers handling Kuwaiti residents' data |

### Gaps against the overlay

1. **Data residency.** Everything runs in `us-central1`, and Vertex AI is called from there. For Saudi and
   government customers this alone blocks the sale. Plan: a GCC deployment on Google Cloud's Dammam
   (`me-central2`) or Doha (`me-central1`) region. Check before committing that every service used is available
   there (Cloud Run, Cloud SQL, GCS, Secret Manager, Vertex AI Gemini). Decide between one GCC region and
   per-country deployments.
2. **Cross-border transfers to subprocessors** (§5). Most vendors process in the US or EU. Each needs a transfer
   mechanism under PDPL (KSA) and the UAE PDPL, and each must be listed in the customer DPA.
3. **Lawful basis and notice for executives.** Record the basis per jurisdiction (legitimate interest is narrower
   in KSA than under GDPR), give people a way to object, and honour it. Do not contact already blocks outreach;
   it does not erase or stop processing.
4. **Sensitive inferences.** Nationality and gender are inferred by AI (`CandidateNationalityClassifier`,
   `CandidateAiEnricher`). Ethnic origin and nationality can be special-category data in several GCC laws. Do a
   DPIA, and decide whether inference stays on by default for GCC workspaces.
5. **Call recordings.** The Notetaker announces itself in the meeting chat and records only when asked, but
   needs a consent record and a retention period — `transcript-retention` is unset.
6. **Data-subject rights.** No access, correction or erasure procedure for a person across a workspace, and no
   user-account deletion.
7. **Breach notification.** No incident plan, and the clocks differ (KSA 72 hours to SDAIA; others "without
   delay"). The plan needs a per-country contact table.
8. **Arabic.** Privacy notices and DPAs for KSA-facing use are expected in Arabic.

## 5. Subprocessors to register

Taken from `application.yml` and `deploy.yml`. For each one record the purpose, the data sent, the processing
region, the certifications (ISO 27001 / SOC 2), the DPA, and the transfer mechanism.

| Supplier | Purpose | Personal data sent |
|---|---|---|
| Google Cloud (Cloud Run, Cloud SQL, GCS, Secret Manager) | Hosting | All |
| Google Vertex AI (Gemini) | AI enrichment, assessment, extraction, assistant, notetaker summaries | Profiles, notes, documents, transcripts (contacts redacted) |
| Google, Microsoft, LinkedIn | Sign-in; Gmail, Outlook and calendars for outreach | Account identity, email and calendar metadata |
| Nylas | Mailbox gateway (until the in-house gateways roll out) | Emails, calendar events |
| Recall.ai | Meeting bot, transcripts, calendar sync | Call audio, video and transcripts; calendar tokens |
| Zoom | Meeting links | Meeting metadata |
| ContactOut | Email and phone lookup, people search | Names and LinkedIn URLs sent; contacts returned |
| Bright Data, HarvestAPI | Profile and people data | LinkedIn URLs and company keys |
| Apollo (out-of-band ETL) | Company universe | None sent (company data only) |
| Mapbox | Geocoding, maps | City and country only |
| Stripe | Payments | Billing contact, tax ID |
| Resend | Transactional email | Recipient email, message content |

## 6. Remediation roadmap

Ordered by what blocks a GCC enterprise sale or a certification audit first.

| # | Item | Control(s) | Type |
|---|---|---|---|
| 1 | Turn on and test Cloud SQL automated backups and PITR, with a recorded restore | 8.13, 5.30 | Ops |
| 2 | MFA (TOTP) for every user, and enforceable per workspace | 8.5 | Product |
| 3 | Write the ISMS core: policy, scope, risk methodology and register, SoA from §3 | Clauses 4–6, 5.1 | Docs |
| 4 | GCC region decision and migration plan | §4.1, 5.23, 5.31 | Architecture |
| 5 | Subprocessor register, DPAs, transfer mechanisms; customer DPA template | 5.19–5.22, 5.34 | Legal |
| 6 | Incident response plan with GCC breach clocks; `SECURITY.md` | 5.24–5.27, 6.8 | Docs |
| 7 | Alerting, uptime checks and log retention | 8.15, 8.16 | Ops |
| 8 | Set `transcript-retention`; write the retention schedule; person and account erasure flow | 8.10, 5.33, 5.34 | Product |
| 9 | Make the security scans required checks; add CodeQL and gitleaks; pin Actions to SHAs; CODEOWNERS | 8.8, 8.28, 5.21, 8.4 | CI |
| 10 | DPIA for nationality and gender inference and for call recording | 5.34 | Privacy |
| 11 | HSTS header; consider Cloud Armor; record GCP org policies | 8.20, 8.9 | Ops |
| 12 | Stronger password rule (12 characters, breached-password check) | 5.17 | Product |
| 13 | HR controls: screening, NDAs, training, on/offboarding | 6.1–6.6 | People |
| 14 | Third-party penetration test | 8.29, 5.35 | External |
| 15 | Internal audit and management review | 9.2, 9.3 | ISMS |
| 16 | Infrastructure-as-code for GCP | 8.9, 8.32 | Ops |

## 7. Evidence index

Places an auditor can be pointed to:

- Access control and tenant isolation: `CLAUDE.md` (security invariants), `.claude/skills/lightmove-domain`
- Database hardening: `ops/cloudsql/harden.sql`, `ops/cloudsql/create-migrate-role.sh`, `ops/cloudsql/grant-platform-role.sh`
- Audit trail: `apps/api/src/main/resources/db/migration/V1__auth_core.sql`, `apps/api/src/main/java/app/lightmove/api/core/audit`
- Cryptography: `apps/api/src/main/java/app/lightmove/api/core/crypto`, `core/security/token`, `core/security/apikey`
- Authentication hardening: `core/config/LockoutSettings.java`, `auth.rate-limit.*` in `application.yml`
- Vulnerability management: `.github/workflows/security.yml`, `.github/dependabot.yml`, `Dockerfile`
- Deployment and change control: `.github/workflows/ci.yml`, `release.yml`, `deploy.yml`, `ops/gcp/`
- AI data handling: `docs/llm-guardrails.md`, `docs/ai-production-readiness.md`
- Observability gaps: `docs/production-observability.md`
