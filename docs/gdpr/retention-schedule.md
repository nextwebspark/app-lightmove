# Retention schedule

How long Uncava keeps each table that holds personal data, why, and what deletes it (#921, epic #917). One row per
`app_lm_*` table. `RetentionScheduleCoverageTest` reads this file: every table with a `person_id`, `user_id`,
`actor_user_id`, `recipient_user_id`, `owner_user_id`, `full_name`, `linkedin_url`, `to_address`, `from_address` or email column must
have a row, and every row must name a table that exists. A new table of that kind fails the build until it is
written down here. What a person's subject access export hands over from these tables, and what it leaves out and
why, is [subject-access-export.md](subject-access-export.md) (#919), held to the schema by `SubjectAccessCoverageTest`.

⚖️ marks a period that is a default awaiting counsel's sign-off (#930). Every period the sweep applies is an
environment override under `lightmove.retention.*` (`RetentionSettings`), so a deployment can change it without a
release. Backups and point-in-time recovery keep a deleted row until they age out (21 days and 7 days once turned on;
`security-of-processing.md`); nothing rewrites them. Logs are kept 30 days.

**Jobs.** `RetentionSweeper` (hourly, `lightmove.retention.sweep-interval`) deletes the system rows below. It is off
until counsel signs the periods off: only a deployment with `RETENTION_ENABLED=true` (`lightmove.retention.enabled`,
the `RETENTION_ENABLED` variable in `deploy.yml`) makes it at all. A tenant
table is swept only in the workspaces `app_lm_resolve_workspaces_with_retention_due` (V199) names, each inside its own
`WorkspaceScope`. `PersonErasureService` (#918) is how a person goes: by an admin from Candidates, or by an admin
reviewing candidate retention (#921 part 2) — a workspace's period (Settings → Data & retention, V200
`app_lm_workspace_retention`, `lightmove.retention.candidate-months` where it has not chosen, 24 ⚖️) lists whoever is
untouched that long and on no live position. One rule deletes without a review (#1490, decisions B2 and E1):
`NeverContactedErasure` (daily, `lightmove.retention.never-contacted-scan`) erases anyone grey and never contacted —
never told, never emailed either way, never met, no call that reached them, no consent and no answer — six months
(`lightmove.retention.never-contacted-months`) after they were added or after the last position they were on was
archived, unless they are on a live position, restricted, do not contact or ever kept by an admin (a Keep stands for
good against a rule that asks nobody); at most `never-contacted-per-run` (500) a pass, with no actor, no suppression
and an audit line naming the rule. It needs `DATA_PROTECTION_ENABLED` as well as `RETENTION_ENABLED`, and Settings
states its period (`neverContactedMonths` on the retention read) only while it runs. `WorkspacePurge` (#926,
`lightmove.retention.workspace-purge-interval`) is how a workspace goes: a deleted workspace's members lose access at
once, and `lightmove.retention.deleted-workspaces-after` (30 days ⚖️) later every row of every table carrying its
`workspace_id` is deleted — but the audit trail and the billing records, kept by id — with every file under its
`ws/{id}/` prefix, leaving the workspace row as a tombstone and a deletion certificate. Uncava can restore a workspace
until its purge starts. `AccountDeletionService` (#926) is how an account goes, at its owner's own request from Settings → Profile: every
membership ends through the roster's removal (keys and AI apps with it), its sessions, sign-ins, links and second
factor are deleted, its own rows in each workspace — the bell, its settings, working hours, assistant chats — go, its
mailboxes and Zoom accounts are let go at the provider, and the `app_lm_user` row is kept with no name or address so
the notes, tasks and audit lines that name its id still resolve. What it did for a workspace stays the workspace's;
a name it typed into someone's note, or an @mention, stays in that note. Audit-trail retention waits for a delete-capable maintenance role (#256).

**Audit details hold ids, never a person's name or contact (#927).** The trail is append-only, so a name written there
would outlive the person's erasure. An event records the person's, user's or invitation's id and a reader takes the
name from the live row — the position panel's Recent activity through `ActivityPersonNames`, the customer audit log
through its target labels — so an erased person reads as "an executive". `AuditService.Builder` refuses the keys that
would name someone (`email`, `fullName`, `phone`, `linkedinUrl`, …) and strips an address under any other key;
`AuditDetailKeysTest` holds every key that sounds personal to a reviewed list. Rows written before #927 were scrubbed
once by `ops/cloudsql/scrub-audit-pii.sql`.

**Consent periods (E1, counsel 23 Nov 2026; Never contacted B2, email 25 Nov 2026).** How long a candidate is kept,
by the consent status of epic #1467. Agreed with counsel; the timers are built by #1475, the never-contacted rule by
#1490. Every other period below still needs counsel's approval before `RETENTION_ENABLED` is turned on
([governance-decisions.md](governance-decisions.md) E1).

How they meet the workspace's review period: until #1475 ships, the review period above (24 months ⚖️ by default) is
the only clock, and it still deletes no one without an admin's review. #1475 replaces that single period with the
status's own; whether a status timer lists the person for review or deletes them is #1475's to settle with counsel.
The one rule that already erases without review is Never contacted (#1490), and only where both `RETENTION_ENABLED`
and `DATA_PROTECTION_ENABLED` are on.

| Status | Meaning | Kept for | Built by |
|---|---|---|---|
| Grey | no consent: held on legitimate interests | 6 months | #1475 |
| Never contacted | grey, and never told or reached (B2) | 6 months from being added, then erased | #1490 |
| Light green | consent for one mandate | until the mandate closes, + 12 months | #1475 |
| Dark green | consent to stay on file | 24 months | #1475 |
| Orange | consent requested, no answer yet. The request lapses unanswered after 30 days and the person is grey again (B8); the 3 months count from the request, so a lapsed request still holds the person until then | 3 months from the request | #1475 |
| Red | do not contact: an objection or a stop request | no period of its own; never contacted again, and the suppression entry stays until an admin removes it | #924, #1489 |

**Outside the application.** Records Uncava keeps by hand, not in any `app_lm_*` table.

| Record | Kept for | Basis |
|---|---|---|
| Request log ([subject-requests-procedure.md](subject-requests-procedure.md) §5) | 3 years after each request closes | D6, recommended |
| Breach register ([breach-runbook.md](breach-runbook.md)) | 5 years | D6, recommended |
| Website demo requests | 12 months after the last contact if no deal | G2, recommended |

**Inside the application, by table.**

| Table | Personal data | Purpose | Retention | Trigger | Job |
|---|---|---|---|---|---|
| `app_lm_user` | name, work email, sign-in facts | the account | while the account lives; once deleted, a row of no name or address ("Former member"), kept so what it did still resolves | account deletion (#926) | `AccountDeletionService` |
| `app_lm_user_avatar` | profile photo | the person's picture beside their name | while the account lives | a new picture, removal, account deletion (#926) | — |
| `app_lm_email_image` | a copy of the person's profile photo | the picture a sent email's signature shows | kept: an email already sent names it | account deletion (#926) | — |
| `app_lm_email_image` (`SIGNATURE`) | a picture the person uploaded into a signature — a logo, a badge, possibly their face | what a sent email's Custom signature shows | kept: an email already sent names it | account deletion, its file queued for the bucket (`AccountDeletionOutreach`) | `StorageDeletionSweeper` |
| `app_lm_email_signature` | user, the profile details a signature shows | a consultant's email signatures | while a member, until removed | removed, workspace purge, account deletion | — |
| `app_lm_email_template` | owner and last editor, the words they saved | a consultant's saved emails, personal or shared | while the workspace lasts: a removed member's stay (shared ones still used, personal ones back on a rejoin) | deleted by its owner or an admin, workspace purge, account deletion (shared ones too) | — |
| `app_lm_user_identity` | provider email and subject | Google / LinkedIn sign-in | while the account lives | account deletion, unlink | — |
| `app_lm_user_mfa_factor` | sealed authenticator secret | two-factor | while two-factor is on | turned off, account deletion | — |
| `app_lm_user_mfa_recovery_code` | hashed codes | two-factor recovery | while two-factor is on | regenerated, turned off, account deletion | — |
| `app_lm_platform_step_up` | user, when a two-factor code was last entered | a platform admin's fresh code for a high-impact action | while the account exists; overwritten by each code | account deletion | — |
| `app_lm_user_platform_role` | who is Uncava staff | platform access | while granted | revoked by ops script | — |
| `app_lm_pending_registration` | provider subject, work email, name, picture link | a first provider sign-in waiting on the terms tick (#928), before any account exists | 10 minutes; the row 1 day past expiry ⚖️ | the tick, a later sign-in by the same identity, expiry | `PendingRegistrationService`, `RetentionSweeper` |
| `app_lm_mfa_challenge` | user, challenge hash | a sign-in waiting on its code | 1 day past expiry ⚖️ | expiry, account deletion | `RetentionSweeper` |
| `app_lm_verification_token` | user, token hash | email verification, password reset | 30 days past expiry ⚖️ | expiry, account deletion | `RetentionSweeper` |
| `app_lm_refresh_token` | user, IP address, browser line | sessions, stolen-token detection | 30 days past the family's last expiry ⚖️ | expiry, account deletion | `RetentionSweeper` |
| `app_lm_workspace` | the firm's email domain, company snapshot, the deleting admin's email | the tenant | while the workspace lives; once deleted, 30 days ⚖️, then a tombstone of ids — the admin's email and the firm's name kept on it only until the certificate's email is accepted, a week at most | deletion, then the purge | `WorkspacePurge` |
| `app_lm_workspace_export` | who asked; the zip is a copy of everything | an admin's export of the whole workspace | 7 days after it is built ⚖️ | expiry, workspace purge | `WorkspaceExportExpiry`, `StorageDeletionSweeper` |
| `app_lm_workspace_purge_certificate` | the deleting admin's id, counts only | proof a workspace's data was deleted | kept with the tombstone ⚖️ | — | — |
| `app_lm_workspace_member` | user | membership | while a member; the removed row is kept for a rejoin | workspace purge, account deletion | — |
| `app_lm_invitation` | invitee email | invitation | 90 days past acceptance or expiry ⚖️ | accepted, revoked, expired | `RetentionSweeper` |
| `app_lm_client_representative` | name, email, user | a client's hiring manager | while the client record lives | removed by staff, workspace purge | — |
| `app_lm_member_availability` | user, working hours | booking pages, send windows | while a member | workspace purge, account deletion | — |
| `app_lm_api_key` | owner user | public API access | until revoked or expired | revoked, member removed, account deletion | — |
| `app_lm_oauth_authorization` | user, token hashes | AI-app connections (MCP) | while the grant lives | disconnected, refresh replayed, password change, account deletion | — |
| `app_lm_mailbox_authorization` | user | a mailbox connect in flight | until the connect completes | completion, account deletion | — |
| `app_lm_mailbox_connection` | user, address, sealed refresh token | sending from the consultant's own mailbox | while connected | disconnected, workspace purge, account deletion | — |
| `app_lm_zoom_connection` | user, sealed refresh token | Zoom links | while connected | disconnected, workspace purge, account deletion | — |
| `app_lm_person` | the executive: name, profile, background, package; which version of Uncava's notice to executives they were last sent, and when (V247) | the candidate record | while held; reviewed for deletion once untouched for the workspace's period (default 24 months ⚖️) and on no live position; the consent periods above set each status's own (#1475); erased without review once grey, never contacted and never kept, after 6 months (E1) | erasure, retention review, the never-contacted rule | `PersonErasureService`, `NeverContactedErasure` |
| `app_lm_person_contact` | emails, phones | reaching the executive | with the person | erasure, edit | cascade |
| `app_lm_person_photo` | photo | the drawer | with the person | erasure | cascade |
| `app_lm_person_vendor_record` | each research vendor's answer for the executive, as sent; may name other people (people also viewed, recommenders) | the profile's further facts, without buying them again | with the person; replaced by the next answer from the same vendor | erasure | cascade |
| `app_lm_person_tag` | person | the workspace's tags | with the person | erasure, untag | cascade |
| `app_lm_person_note` | person, staff's notes | the team's record | with the person; a deleted note 30 days ⚖️ | erasure, delete | `PersonNotePurge` |
| `app_lm_person_document` | person, CV and references | the team's record | with the person | erasure, removal | cascade |
| `app_lm_person_document_version` | person, file metadata | each file of a document | with the person | erasure, removal | cascade, `StorageDeletionSweeper` |
| `app_lm_person_activity` | person, who did what | the timeline | with the person | erasure | cascade |
| `app_lm_person_privacy_link` | person, who sent a notice or an outreach email, when, when a stop link was used; the link's token as a hash | the person's privacy notices (#923), each outreach email's stop link (#924), consent requests and the link to change an answer (#1267), and the page they open | with the person | erasure | cascade |
| `app_lm_person_consent` | person, each purpose asked about, who asked, the answer, when, until when, the sentence answered | the person's consents per purpose (#1267); a keep-on-file consent in force holds them out of the retention review | with the person | erasure | cascade |
| `app_lm_person_consent_event` | person, each request, answer, record by hand and expiry, who acted, the sentence shown | the append-only proof of what was asked and answered (#1267) | with the person | erasure | cascade |
| `app_lm_person_meeting` | person, consultant, call times | calls with the executive | with the person | erasure, calendar change | cascade |
| `app_lm_project_candidate` | person on a position, frozen V91 copy | the mapping | with the person or the position | erasure, unmapping | cascade |
| `app_lm_candidate_contact` | frozen V54 copy of contacts | none: frozen for #606 | with the mapping | erasure, #606 cleanup | cascade |
| `app_lm_candidate_photo` | frozen photo copy | none: frozen for #606 | with the mapping | erasure, #606 cleanup | cascade |
| `app_lm_candidate_scorecard` | assessment of the executive | the consultant's scorecard | with the mapping | erasure, unmapping | cascade |
| `app_lm_ranking_scenario` | who saved it, the team's rationale | the position's ranking scenario (no person) | with the position | position or workspace deleted | cascade |
| `app_lm_dashboard` | who made it, its name | a member's custom dashboard (the charts chosen; no figures, no person) | with its owner and its position or workspace | owner's account, position or workspace deleted | cascade |
| `app_lm_home_layout` | whose Home it is | a member's old Home layout per workspace, no longer read and dropped by a later migration (sections and charts chosen, Needs you groups; no figures, no other person) | with its member | account or workspace deleted, or reset to the default | cascade, or deleted on reset |
| `app_lm_start_page` | whose start page it is | where a member's Uncava opens per workspace (a list, a dashboard or a position's tab, by id; no other person) | with its member | account, workspace, dashboard or position deleted, or set back to Home | cascade, or deleted on Home |
| `app_lm_interview_plan` | person, questions for a call | the interview copilot | with the person | erasure | cascade |
| `app_lm_meeting_recording` | person, transcript | the Notetaker | with the person; transcript per `lightmove.recall.transcript-retention` | erasure, retention | `NotetakerTranscriptPurge` |
| `app_lm_meeting_live_line` | spoken lines | the live copilot | until the call ends | call end | `NotetakerService` |
| `app_lm_meeting_copilot_viewer` | user | who watches the copilot | with the recording | erasure | cascade |
| `app_lm_task` | person, staff | next actions | with the person | erasure | cascade |
| `app_lm_note_mention` | user | @mentions | with the note | note purge | cascade |
| `app_lm_notification` | recipient, actor, person, names | the bell | 90 days past read or archived ⚖️; unread kept | read, archived, the recipient's account deletion | `RetentionSweeper` |
| `app_lm_notification_digest` | user | one digest a day | 90 days past sending ⚖️ | sent, account deletion | `RetentionSweeper` |
| `app_lm_notification_preference` | user, email setting | the bell's settings | while a member | workspace purge, account deletion | — |
| `app_lm_outreach_enrollment` | person, address | a sequence run | with the person | erasure | cascade |
| `app_lm_outreach_message` | person, addresses, email text | the conversation ledger | with the person | erasure | cascade |
| `app_lm_outreach_message_attachment` | file metadata | an email's files | with the message | erasure, cancel | cascade, `StorageDeletionSweeper` |
| `app_lm_executive_sourcing_run` | counts only (V213 took the names off) | Find executives' record | with the position | — | — |
| `app_lm_executive_sourcing_suggestion` | profile slug, our judgement | who a Find executives search found | the people cache's life (30 days) | the next run, erasure | `ExecutiveSourcingWorker`, `PersonErasureService` |
| `app_lm_import_enrichment_item` | profile or company slug, our outcome | which imported people and companies an import asked to have researched (V225) | `lightmove.spreadsheet-import.enrichment-keep-for` (30 days) after finishing | age, erasure | `ImportEnrichmentPurge` (daily), `ImportEnrichmentErasure` |
| `app_lm_company_note` | staff's notes about a company | the team's record | with the company; a deleted note 30 days ⚖️ | delete | `CompanyNotePurge` |
| `app_lm_assistant_thread` | user, questions asked | the assistant's chats | 180 days past the last turn ⚖️ | inactivity, account deletion | `RetentionSweeper` |
| `app_lm_assistant_turn` | user, questions and answers | one exchange | with the thread | thread swept | cascade |
| `app_lm_usage_event` | user | fair use and billing usage | 400 days ⚖️ | age | `RetentionSweeper` |
| `app_lm_credit_hold` | user, person | a paid find in flight | with the ledger | — | — |
| `app_lm_credit_entry` | user, person | the contact-credit ledger, append-only | statutory ⚖️; financial record | — | none: append-only (#256) |
| `app_lm_audit_event` | actor, ids, IP address, browser line; never a name or contact in its details (#927) | the security audit trail | 12 months, then deleted (E2, recommended) | age | none until #256; then #921 |
| `app_lm_workspace_candidate_privacy` | the firm's privacy contact address, the admin who last changed it | the firm as controller in every privacy notice (#923), and its consent mode and purposes (#1267) | while the workspace exists | workspace purge, an admin clearing it | `WorkspacePurge` |
| `app_lm_workspace_suppression` | one-way hashes only | who no door may file again | until an admin removes the entry | removal | — |
| `app_lm_vendor_person` | an executive's vendor record | the people cache, shared by every workspace | `lightmove.enrichment.people-cache-ttl` (30 days) | age, erasure | `RetentionSweeper` |
| `app_lm_vendor_people_search` | profile slugs a search returned | the people cache | `lightmove.enrichment.people-cache-ttl` (30 days) | age, erasure | `RetentionSweeper` |
| `app_lm_vendor_company` | a company page's vendor record | the company cache | `lightmove.enrichment.company-cache-ttl` (30 days) | age | `RetentionSweeper` |
| `app_lm_companies` | company pages, no people | none: the retired company copy | until dropped | — | — |
