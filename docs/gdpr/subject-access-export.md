# Subject access export (#919)

What Uncava hands over when a person asks a workspace for a copy of everything it holds about them (GDPR Art. 15) or
for that data in a form they can take elsewhere (Art. 20). Part of epic #917.

## Who, where, how

- An admin opens the person on **Candidates**, then ⋯ → **Export personal data…** → **Download**. The menu item is
  offered only where `DATA_PROTECTION_ENABLED` is on, like the other people controls.
- The route is `GET /api/v1/candidates/{personId}/subject-access-export`, gated `PERSON_SUBJECT_ACCESS` (V209, ADMIN
  alone). With the switch off it answers 404.
- Every export is audited `PERSON_SUBJECT_ACCESS_EXPORTED` (Settings → Security → Audit log, *Exports & downloads*,
  "Started an export of everything held on a candidate"), by person id and counts, never a name. The line is written
  once the data is read and assembled, before the zip streams, so it records that the export was made, **not that
  the admin received it**: a download the browser drops still leaves the line. Delivery is the admin's to confirm.
- A restricted person can still be exported: handing someone their own data is not a use of it.

## What the zip holds

| Entry | Contents |
|---|---|
| `README.txt` | For the admin who sends it: what each file is, and what to check first. |
| `person.json` | Everything, machine-readable (`export.format` = `uncava-subject-access/1`). |
| `person.html` | The same, to read in a browser. One page, style inline, no script, every value escaped. |
| `documents/` | The latest file of each of the person's documents. Earlier versions are listed in `documentVersions`. |
| `documents/NOT-INCLUDED.txt` | Only where a document's file could not be read from the bucket: each one left out, by name and version id. The rest of the export still goes, since a request has a deadline; the failure is logged by version id. |
| `photo.*` | The photo on the record, where there is one. |
| `earlier-copies/` | V91's frozen copy of the photo, held until #606 drops it. |

`person.json` has five parts:
- `export`: who the export is about, who holds the data, who exported it, and when;
- `aboutThisData`: the fixed Art. 15(1) answers (purposes, recipients, retention, sources, rights, deleted notes,
  automated decisions), worded in `SubjectAccessStatements`. A position's hiring side is a recipient only while the
  person is on it; an archived one, which no client seat reads (V137), is named as having read it while open;
- `aiLogic`: one entry per AI use that left something on this person (`AiUse`), each with its fixed Art. 15(1)(h)
  text. Background proposals are found by their `AI_ASSESSED` timeline line (`scope: BACKGROUND`) as well as by
  `ai_inferred_fields`, since confirming a value clears its flag but keeps the line;
- `data`: one key per section, each a list of rows as stored;
- `references`: what the ids in the rows name — colleagues, positions with who they are for, statuses and each
  position's own columns.

Rows are read whole (`RowSnapshotReader`, `SELECT *`), so a column a later migration adds is exported without
anyone listing it. Each contributor names the few columns it **withholds**, all of them machinery rather than
information about the person:
- row versions and `workspace_id`;
- storage keys and lookup keys derived from a value already exported (`value_key`, `name_key`, `title_key`);
- provider ids and thread ids;
- workers' claims and counters (`*_since`, `coach_runs`, …);
- the live copilot URL's hash, and `rule_key`;
- photo bytes, which go in as a file instead.

A withheld name the query does not return fails the export, so a rename cannot quietly drop one.

Notes are exported **deleted ones included**, with `deleted_at`, until `PersonNotePurge` removes them
(`lightmove.notes.purge-after`). Each position row also carries V91's frozen copy of the person, `ai_assessment`
included.

## Other people's data ⚖️

Colleagues stay named where they wrote, changed or did something; that is the record of who processed the data.
Notes, emails and call transcripts go as written and may mention third parties. The dialog and the README tell the
admin to check and redact before sending. Whether Uncava should redact anything itself is for counsel (#930).

## Coverage

`SubjectAccessCoverageTest` reads the schema and finds every person table:
- `app_lm_person` itself;
- every `app_lm_*` base table with a `person_id` or `candidate_id` column;
- every table whose foreign keys reach one of those, followed transitively.

Each one must be exported by a `SubjectAccessContributor` (its `tables()`) and listed **Included** below, or listed
**Excluded** with its reason. A new table holding a person fails the build until it is decided here: #922's, #923's
and #1267's tables will have to join the export.

`AiLogicCatalogTest` does the same for the prompts. Every `src/main/resources/prompts/*.st` is either an `AiUse` with
its logic text, or listed as leaving nothing on a person.

| Table | Export | Section or reason |
|---|---|---|
| `app_lm_person` | Included | `person` |
| `app_lm_person_contact` | Included | `contacts`, each with its `source` |
| `app_lm_person_tag` | Included | `tags`, by label |
| `app_lm_person_photo` | Included | `photo`, and the file |
| `app_lm_person_note` | Included | `notes`, deleted ones until purged |
| `app_lm_person_document` | Included | `documents`, and the latest file of each |
| `app_lm_person_document_version` | Included | `documentVersions` |
| `app_lm_person_activity` | Included | `timeline` |
| `app_lm_project_candidate` | Included | `positions`, with V91's frozen copy |
| `app_lm_candidate_contact` | Included | `earlierContacts` (V91's frozen copy) |
| `app_lm_candidate_photo` | Included | `earlierPhotos`, and the file |
| `app_lm_candidate_scorecard` | Included | `scorecards` |
| `app_lm_person_privacy_link` | Included | `privacyNotices`: each notice (#923) and each outreach email's stop link (#924) — its purpose, when it was sent, by whom, until when, when it was last opened or used, and whether the provider refused it; never the link's hash |
| `app_lm_outreach_enrollment` | Included | `outreachRuns` |
| `app_lm_outreach_message` | Included | `emails`, both ways, with their words |
| `app_lm_outreach_message_attachment` | Included | `emailAttachments`, named; the files are the person's documents or the consultant's own |
| `app_lm_person_meeting` | Included | `meetings` |
| `app_lm_meeting_recording` | Included | `recordings`, with transcript, suggested changes and the live reading |
| `app_lm_interview_plan` | Included | `interviewPlans` |
| `app_lm_task` | Included | `tasks` |
| `app_lm_meeting_live_line` | Excluded | the lines streamed during a call, deleted when it ends; the transcript is in `recordings` |
| `app_lm_meeting_copilot_viewer` | Excluded | which colleagues watched the live copilot: staff ids only |
| `app_lm_note_mention` | Excluded | which colleagues a note mentioned: staff ids only; the note itself is in `notes` |
| `app_lm_notification` | Excluded | colleagues' bell: it repeats the person's name and points at rows already exported |
| `app_lm_credit_hold` | Excluded | the billing ledger's hold for a contact lookup: the person's id and a price, no data about them |
| `app_lm_credit_entry` | Excluded | the billing ledger, append-only: the person's id and a price, no data about them |
| `app_lm_audit_event` | Excluded | the security trail, ids only (#927); answers who accessed the record, which the workspace's audit log export gives |
| `app_lm_workspace_suppression` | Excluded | one-way hashes, unreadable by design |
| `app_lm_recall_bot_erasure` | Excluded | bot ids queued for deletion at Recall, no data about anyone |
| `app_lm_storage_deletion` | Excluded | bucket keys queued for deletion, no data about anyone |
| `app_lm_executive_sourcing_run` | Excluded | Find executives' record of a run; the people it filed are the `positions` rows |
| `app_lm_executive_sourcing_suggestion` | Excluded | Find executives' ranked finds: a profile slug and our judgement, never a name; erasure deletes the person's rows |
| `app_lm_vendor_person` | Excluded | Uncava's own cache of a vendor's record, shared by every workspace: Uncava is its controller, and answers for it through its own request channel (website #28) |
| `app_lm_vendor_people_search` | Excluded | the same cache's index of which profiles a search returned |
