# UAE alignment register

Draft for counsel, 2026-11-19. The register that joins two tracks of work:

- **The UAE track** is Aleksandra Mizińska's (Alac Partners) emails of 29 Oct to 19 Nov 2026, kept in `docs/GDPR-discusstion/Uncava_Data_Protection_Raw_Emails.md`. It is written for the federal PDPL, DIFC (with Regulation 10 on AI) and ADGM, and it asks us to build to the highest common standard.
- **The engineering track** is epic #917 and the files in this folder. They are written for the EU GDPR and most of the product is built but switched off in production (`DATA_PROTECTION_ENABLED`, `RETENTION_ENABLED`, `SUPPRESSION_LIST_ENABLED` default to false).

It supersedes the "Alok to confirm" cells in `docs/GDPR-discusstion/Uncava_Data_Protection_Gap_Analysis.md`. Evidence is from reading the code and docs on 2026-11-19. Nothing here is legal advice. Paths in the evidence column are under `apps/api/src/main/java/app/lightmove/api/` or `apps/web/src/`.

**Status key.** BUILT works to the lawyer's spec. PARTIAL exists but differs. MISSING not built. OFF means built but not enabled in production.

## 1. Where the two regimes differ

| Point | EU GDPR design today | UAE standard asked for |
|---|---|---|
| Lawful basis for sourcing and outreach | Legitimate interests (`lia.md`); no jurisdiction branch exists in code | PDPL has no legitimate interest. Consent, or a listed exception such as data the person made public |
| Missing consent | Warns only (B7); nothing blocks | Counsel to say whether a block is required |
| Retention | 24 months untouched, a review list, sweeper off | Grey 6 months, light green mandate + 12 months, dark green 24 months, orange 3 months |
| Notice | Firm sends a privacy notice by hand; footer on every email (OFF) | Public Uncava notice plus footer naming the data source |
| AI | Per-workspace switches for inference | No AI inference of nationality, origin or gender; assessment and recording controls |
| Transfers | TIA covers EU to US only | PDPL, DIFC, ADGM transfer rules; nothing hosted in the UAE |
| Regulators | Placeholder rows only | PDPL authority, DIFC Commissioner, ADGM Office of Data Protection |

## 2. Product items (lawyer's P1 to P16)

| # | Requirement | Status | Evidence and current behaviour | Next step |
|---|---|---|---|---|
| P1 | Remove AI inference of nationality and gender; manual optional "Local national" (UAE / KSA / Other GCC / Not a local national / Not confirmed default) and "Nationality" fields; gender manual, aggregated reports only | MISSING | `resources/prompts/candidate-ai-enrich-system.st` infers both. `NationalityGroup` is an ethnic-origin proxy. Gated by four switches (V216: old workspaces on, new off). `Person.nationality` is free text. `DiversityReporter` counts unconfirmed AI values | Backlog B1 |
| P2 | Consent statuses grey / light green / dark green / orange / red with manual and automatic fields | PARTIAL | `candidate/constant/ConsentStatus` is REQUESTED / GIVEN / DECLINED / WITHDRAWN / EXPIRED per purpose. No colours and no unasked state. `valid_until` only on keep-on-file (V223) | Backlog B5 |
| P3 | Export of the consent register | BUILT, OFF | `GET /candidates/export/consent-register`, CSV, audited (`dataexport/ConsentRegisterExportController`). Needs consent mode | Switch on at go-live |
| P4 | Consents in the access-request report | BUILT, OFF | `consents` and `consentHistory` sections (`PersonSubjectAccessRecords`) | Switch on at go-live |
| P5 | Privacy-notice version management; version recorded per person | MISSING | Only `wording_version` of the consent sentence (`ConsentWording.VERSION`). `app_lm_person` keeps `privacy_notified_at` and channel only. Wording is hard-coded | Backlog B5 |
| P6 | Retention engine 6 / 12 / 24 / 3 months, client-editable | PARTIAL | Workspace chooses 12, 24, 36, 60 months or never (V200). "Untouched since", review list only. Sweeper off. Not tied to consent status | Backlog B5, needs E1 |
| P7 | Do-not-contact minimal list (name + profile link) | PARTIAL | `WORKSPACE_SUPPRESSION` (V195) keeps keyed hashes of slug and email, no name or link. Withdrawal makes the consent WITHDRAWN but does not add a suppression row; objection and opt-out do | Counsel question B9 |
| P8 | Notetaker: consent in register before bot joins; bot introduces itself; 90-day recording retention | MISSING / PARTIAL | `CallAssistantPanel.tsx:105` says "Ask {firstName} first". No check in `NotetakerService.schedule`. Intro is a chat line only (`RecallBotClient`). Recall copy kept 7 days; transcript kept forever unless `NOTETAKER_TRANSCRIPT_RETENTION` is set | Backlog B2 |
| P9 | Live copilot off by default, separate switch, disclosed | MISSING | `CallAssistantPanel.tsx:62` hardcodes `liveCopilot: true`. Only disclosure is the bot's chat line | Backlog B2 |
| P10 | Assessment controls | MISSING / PARTIAL | `CandidateScorecard` `markReviewed` adopts every AI value in one click. Prompt scores behavioural traits from career. No "insufficient evidence". Sources only in free text. No draft-versus-final log. Overall rounded to 0.1. No AI-assisted label | Backlog B3 |
| P11 | Non-removable email footer: data source and notice link | BUILT, OFF | #924. Appended at send, not editable, with a stop link (`privacy-notices-923.md`). Microsoft mailboxes lack List-Unsubscribe | Switch on at go-live |
| P12 | Message templates (first contact, consent, renewal, access reply) | PARTIAL | Wording is in code (`PrivacyNoticeWording`, `ConsentWording`, `EmailTemplates`). No editable templates. No access-request reply | New draft in `client-message-templates.md`; Backlog B6 |
| P13 | Warning when an action needs consent and none is valid | PARTIAL | Share-with-client only (Pipeline card, Outreach "Client visible", Show anyway). No CV-upload or talent-pool warning | Backlog B5 |
| P14 | 30-day renewal reminder with template | PARTIAL | `consent_renewal_days` default 30, `ConsentExpiryScanner`. Keep-on-file only, fixed template | Backlog B5 |
| P15 | "Talent pool with valid consent" filter | PARTIAL | `PoolView` has consent_asked / consent_ending / consent_refused, no positive filter | Backlog B5 |
| P16 | Access control: roles, MFA, workspace separation | PARTIAL | Roles and RLS are built. MFA behind `MFA_ENABLED`, no per-workspace enforcement. Humans hold standing DB access to all tenants (gap G1) | `access-control-policy.md`; decision G5 |

## 3. Data sources and vendors (V1 to V6)

| # | Requirement | Status | Detail | Next step |
|---|---|---|---|---|
| V1 | Lawful basis for ContactOut private email and phone | OPEN | Decision: keep with the lawyer's four mitigations. Lookup stays off for UAE-regulated clients until storage approval and a lawful-collection warranty are in writing | Switch per workspace (B4) |
| V2 | ContactOut contract | PARTIAL | API agreement and DPA exist (reply of 2026-11-19). Not yet received or signed. No warranty. Storage "not stored by default"; needs their compliance review. Request drafted in `GDPR-discusstion/email-to-contactout.md` | Send; collect signed copies |
| V3 | Single profile provider | OPEN | `ENRICHMENT_PROVIDER` is off by default and runs Bright Data, HarvestAPI and ContactOut. No decision recorded | Decision for Alok |
| V4 | Hosting and transfers outside the UAE | PARTIAL | Cloud Run, Cloud SQL, GCS in us-central1; Vertex AI global; Recall us-east-1; ContactOut AWS us-west-1; others `[Region]`. No UAE region | UAE addendum to `tia.md`; F1, F2 |
| V5 | Recall.ai as subprocessor | PARTIAL | Listed in `subprocessors.md`. DPA and transfer columns are `[y/n]`. EU region available via `RECALL_BASE_URL` | F3, F12 |
| V6 | Other subprocessors | PARTIAL | `subprocessors.md` complete in names, incomplete in regions | Fill regions, then publish |

ContactOut roles confirmed in writing: for names and profile links we send, ContactOut is our processor. For profiles, emails and phones returned, ContactOut and Uncava are independent controllers (DPA section 3, controller-to-controller SCCs). Sources are public directories, crawling, user-contributed data and licensed datasets; phones partly from a third party; no per-record provenance. Their basis is legitimate interests. The PDPL "made public" exception therefore cannot be claimed for an individual email or phone.

## 4. Documents needing counsel (L1 to L7)

| # | Document | State |
|---|---|---|
| L1 | DPA with clients | Acceptance mechanism built (`terms-acceptance-928.md`: `dpa_version`, `dpa_accepted_by`). Text lives in the `uncava-website` repo, written for GDPR Art. 28 with SCCs. Older workspaces never signed it (B18) |
| L2 | Terms of use | Tick and versioning built. Text in the website repo; version bump waits on counsel |
| L3 | Public candidate privacy notice | The per-firm Art. 14 notice is built. Uncava's own notice as controller is not written |
| L4 | User privacy policy | Placeholders only |
| L5 | DPIA for AI | `dpia.md` draft 1, EU basis. UAE addendum added 2026-11-19; counsel review pending |
| L6 | Client privacy-notice template | Wording is in code; document drafted in `client-message-templates.md` |
| L7 | DPO, Reg 10, retention periods, ContactOut pass-through | Open for counsel (A3, B13, E1 below) |

## 5. Own-side documents (D1 to D10)

| # | Document | State | File |
|---|---|---|---|
| D1 | Record of processing | Draft, EU basis; UAE addendum added | `ropa.md` |
| D2 | Subprocessor list | Draft; regions and DPA columns incomplete | `subprocessors.md` |
| D3 | Data flow map | New draft | `data-flow-map.md` |
| D4 | Information security policy | New draft | `information-security-policy.md` |
| D5 | Access control policy | New draft | `access-control-policy.md` |
| D6 | Incident and breach procedure | Draft; roles blank; UAE section added | `breach-runbook.md` |
| D7 | Retention and deletion policy | Draft; defaults differ from the lawyer's (24 months untouched against 6 / 12 / 24 / 3) | `retention-schedule.md` |
| D8 | Candidate request procedure | Draft | `subject-requests-procedure.md`, `subject-access-export.md` |
| D9 | Backup and recovery | New draft. Automated backups and point-in-time recovery are OFF; one on-demand backup from 2026-10-01 never expires; restore drill table empty | `backup-and-recovery.md`, `security-of-processing.md` |
| D10 | Client templates | Drafted; access-request reply is new | `client-message-templates.md` |

## 6. Engineering decisions A to G against the UAE track

| Engineering decision | Impact of the UAE track |
|---|---|
| A1, A2 legal entity and inboxes | Block every legal page and the email footer. Also decides which law the terms follow |
| A3 DPO | Same question the lawyer raised |
| A4 to A6 EU / UK representative, EU regulator | Unchanged; add UAE representative question |
| B1 legitimate interests | Holds for EU only. PDPL needs consent or the made-public exception |
| B2 notify people never contacted | Lawyer's answer is a public notice plus footer; counsel to confirm for PDPL |
| B6 vendor data as controller | Settled for ContactOut: independent controller. Cache shared across customers still open |
| B7 warn or stop | Lawyer asks for a warning; counsel to say if a block is needed |
| B8 silence never consent | Compatible; request expires after 30 days |
| B9 keep proof of consent after deletion | Conflicts with the red do-not-contact list that keeps name and link |
| B13 LinkedIn as made public in the UAE | Core question for the PDPL; blocks UAE sourcing basis |
| C1, C2 inference switches | Replaced by the agreed removal of nationality and gender inference |
| C3 to C5 AI decisions | Add PDPL Art. 18 and DIFC Reg 10 |
| E1 retention periods | Lawyer's table is the target; automatic deletion stays off until approved |
| E3 backups | Backups are off, so "removed from backups" is untrue today |
| F1 to F12 suppliers, regions | Add UAE transfer analysis; ContactOut us-west-1 |
| F5 breach timing | Add UAE deadlines |

## 7. Build backlog

**B1. Remove AI inference of nationality, origin and gender.** Delete the prompts and the eleven-group classifier. Add manual optional fields: "Local national" dropdown with Not confirmed as default, optional "Nationality" (citizenship), gender. Diversity report counts confirmed values only and appears only in aggregate. Keep the four-switch model only for what remains.

**B2. Notetaker and copilot.** Add a `RECORDING` consent purpose. Block `NotetakerService.schedule` until it is recorded with channel and evidence, and cover the AI analysis disclosure when the copilot is on. Copilot is a separate switch, default off. Bot introduces itself at the start of the call. Default retention of recording and transcript 90 days.

**B3. Assessment controls.** Per-score confirmation before submit. Behavioural, personality and motivation scores only from notes or transcripts, otherwise "insufficient evidence". Structured source per score. Log of differences between AI draft and final. Rounded overall score. Client report states AI assistance.

**B4. ContactOut.** Per-workspace switch for private email and phone lookup, default off for UAE-regulated workspaces. Keep the cache and stored record only within what ContactOut approves. Build an opt-out intake if ContactOut offers a feed.

**B5. Consent model.** Add channel and evidence, a time of day on the answer, mandate scope, and the privacy-notice version recorded per person with a version table and management screen. Add the grey (6 months) and orange (3 months, then delete) timers and the mandate-close + 12 months rule. Settle the red list with counsel. Add consent warnings for CV upload, share and talent-pool add, a "valid consent" filter, and a jurisdiction default that puts UAE-regulated workspaces in consent mode.

**B6. Templates.** Make consent, renewal and first-contact messages editable per firm from Uncava defaults; add the access-request reply.

**B7. Inventory.** Add `/api/v1/llm/shortlist`, `/llm/embed`, assistant chat, position extraction, import mapping and search words to `AiUse` and the DPIA, or remove `/llm/shortlist`.

**B8. Go-live of what is built.** In this order: set up the key for the "never add again" list and switch on suppression; turn on database backups and remove the old manual one; counsel approves E1, then enable automatic deleting; tell customers who connect other apps that gender and nationality need a separate permission; publish the website's legal pages once reviewed.

## 8. Decision pack

| Who | Decisions |
|---|---|
| Leadership | A1 legal entity, A2 real inboxes, A3 DPO, A7 incident ownership, F2 supplier regions, F3 scheduling and recording region, F9 backup location, G1 to G5 |
| Counsel | B2, B7, B9, B13, C2 to C5, D1 to D7, E1 to E4, F1, F5 to F8, UAE deadlines and authorities, transfer mechanism, cross-customer vendor cache |
| Alok | Single provider (V3), which optional consent features to take, KSA priority, delivery dates |
| ContactOut | Storage approval, lawful-collection warranty, UAE position, opt-out feed, LIA, phone-data provider, source provenance |
