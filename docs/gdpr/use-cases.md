# GDPR use cases: who does what, and what happens

This page covers how a firm on Uncava handles the personal data of the executives it maps: the suppression list, a
request to delete someone, a request to restrict processing, and how long records are kept. It describes what is
built on `main` after #1178, #1179, #1180, #1185, #1186 and #919's export (epic #917). For the engineering detail, see
[people-controls-918-920.md](people-controls-918-920.md) and [retention-schedule.md](retention-schedule.md).

**Who is the controller.** The firm that holds the workspace decides why it keeps each candidate, so it is the
controller and answers the candidate's requests. Uncava is its processor and supplies the tools below. Each workspace
acts alone. Erasing or suppressing someone in one workspace does nothing to another workspace that holds the same
person.

---

## 1. Who does what

| Operation | Who can do it | Where |
|---|---|---|
| Restrict processing of a person | Anyone on the team (Admin or Member) | Candidates → person → ⋯ → **Restrict processing…** |
| Lift a restriction | The person who set it, or an Admin | The same menu, or the note in the drawer |
| See, add to or remove from the suppression list | Admin only | Settings → **Data & retention** → Suppression list |
| Export everything held on a person | Admin only | Candidates → person → ⋯ → **Export personal data…** |
| Erase a person permanently | Admin only | Candidates → person → ⋯ → **Delete person permanently…** |
| Choose the candidate retention period | Admin only | Settings → Data & retention |
| Review who is past the period, and keep them | Admin only | Settings → Data & retention → Due for deletion |
| Delete people past the period | Admin only | The same card → **Delete selected** |
| Set system retention periods, and switch the sweep on | Uncava ops, through deployment settings | `lightmove.retention.*`, `RETENTION_ENABLED` |

The permissions behind these are: restriction is `CANDIDATE_POOL_MANAGE` (Admin and Member); erasure and retention
deletes are `PERSON_ERASE` (Admin); the list and the period are `WORKSPACE_MANAGE` (Admin). A Client representative
can do none of them.

---

## 2. The suppression list

### What it is

A workspace's list of people who must never be added again. Once someone is on it, no route can put them back:

- **Add executive**, the **browser plugin** and a **spreadsheet import** refuse them, with "This person is on your
  workspace's suppression list".
- **Find executives**, **People Search** and **Add to position** leave them out and count how many were skipped.
- Mapping a person the workspace already holds is refused when any of their stored keys is listed.

A person is matched by their **LinkedIn profile** or an **email address**, never by name or phone. A name is not
unique, and a phone is often a switchboard.

The list does not hold the profile link or the address. It holds a one-way keyed hash of each, so a copy of the table
cannot be checked against a guessed email. Each entry records its reason, an optional note, who added it and when.

### How it is filled

| Reason | How an entry gets there | Who |
|---|---|---|
| **Erased** | An erasure adds the person's profile and every email held for them. The box is ticked by default, and the Admin can untick it. | Admin, as part of an erasure |
| **Objected** | The person objected to being processed (Art. 21). It is added by hand, or by ticking "also add to the suppression list" when restricting processing. | Admin by hand; anyone on the team through the restriction dialog |
| **Manual** | Any other reason, for example a person who asked by phone never to be approached. This is the default reason. | Admin |

Deleting someone under the retention period never adds them to the list. Passing the period is not an objection, so
the firm may meet them again.

### Taking someone off

An Admin removes an entry from the list, and that person can then be added again. Lifting a restriction leaves any
objection entries in place; they have to be removed on their own. Every addition and removal is in the audit log.

---

## 3. When a request to delete a candidate's data arrives

The request goes to the firm, because the firm is the controller. If it reaches Uncava instead, pass it to the firm.

1. **Find the person.** Go to **Candidates** and search by name, employer or email.
2. **Open the erasure.** As an Admin, open the person → ⋯ → **Delete person permanently…**. The dialog counts what
   will go: positions, notes, documents and files, emails, outreach runs, meetings, recordings and tasks.
3. **Confirm.** Type the person's full name. Leave **Add to the suppression list** ticked unless the firm has a reason
   to meet them again. With it ticked, no route can add them back later. Untick it, and they could come back through
   the plugin or a vendor search.
4. **Uncava deletes, in one step:**
   - the person and their profile, contacts, photo, tags, notes, documents, timeline, scorecards, interview plans,
     tasks, meetings, recordings and transcripts, emails and outreach runs, on every position in the workspace;
   - the shared vendor cache's copy of their profile;
   - their name from the bell's notifications and from the record of Find executives runs.
5. **Follow-up work runs on its own:**
   - their files are removed from storage within minutes, retried until the storage service agrees;
   - their Notetaker bots are called back and their recordings deleted at Recall, retried until Recall agrees (for up
     to Recall's own 7-day retention).
6. **The record of it.** The audit log records that the person was erased, by whom and how much went, never their
   name.

**It is not done while an email to the person is being sent.** The dialog says so; try again a minute later.

**What an erasure does not reach.** Tell the requester about these where they apply:

- **Emails already sent** remain in the consultant's own mailbox (Gmail or Outlook). Uncava deletes its own record of
  them, not the mailbox's.
- **Backups** keep the data until they age out. Nothing rewrites a backup.
- **Audit lines** name nobody: since #927 they hold ids, and the names earlier ones stored were scrubbed once
  (`ops/cloudsql/scrub-audit-pii.sql`).
- **Other workspaces** that hold the same person are separate controllers and are not affected.
- **Data providers** (ContactOut, Bright Data) hold their own records, as controllers in their own right.

Erasure cannot be undone. Only Admins have the button, the name must be typed back, and the preview is shown first.
After that, there is no other copy.

---

## 4. When someone asks to restrict processing, or objects

A **restriction** (Art. 18) pauses all use of a person while a question is settled, for example while the person
contests their data. Anyone on the team sets it from Candidates → ⋯ → **Restrict processing…**, with a reason. The
record stays readable and editable, but while it is restricted:

- no AI runs on them (Suggest with AI, Assess with AI, openers, the interview plan, the live copilot);
- no contact lookup, email, booking or Notetaker; a sequence skips them, and a live run stops at its next send;
- their scheduled or recording Notetaker bots are called off at once;
- nobody can add them to another position.

Client-facing reads (the Companies grid, talent map, report and export) still show them. Whether they should is
waiting on counsel.

An **objection** to processing is a restriction with "also add to the suppression list" ticked, or an Admin's entry
on the list with the reason Objected.

---

## 5. When someone asks for a copy of their data

A **subject access request** (Art. 15) asks what the firm holds about a person; **portability** (Art. 20) asks for it
in a form they can take elsewhere. An Admin answers both from Candidates → person → ⋯ → **Export personal data…** →
**Download**. The zip holds:

- `person.html`, everything held on them, to read in a browser;
- `person.json`, the same data, machine-readable;
- the latest file of each of their documents, and their photo.

It covers their profile and contacts (each with where it came from), every position they are on with its status,
custom columns, scorecard and tasks, the team's notes (deleted ones too, until they are purged), documents, emails
both ways, meetings, call recordings' transcripts and suggested changes, and the timeline. It also gives the fixed
answers the request is owed: why the data is held, who reads it, how long it is kept, the person's rights, and how
each AI feature that touched their record works.

**Check it before sending.** Colleagues stay named where they wrote or did something. Notes, emails and call
transcripts go as written and may mention other people: remove their details where the request does not need them.
Send the file only to the person, through a channel you trust.

The export changes nothing, and works for a restricted person too. It is recorded in the audit log as *Exported
everything held on a candidate*, by id, never by name. What is in it, table by table, is
[subject-access-export.md](subject-access-export.md).

---

## 6. How retention works

There are two kinds of retention, and they work differently.

### 6a. Candidates: reviewed by an Admin, never deleted automatically

Each workspace chooses how long to keep a candidate nobody is working with: **12, 24, 36 or 60 months, or never**.
Until it chooses, the deployment's default applies (24 months, `RETENTION_CANDIDATE_MONTHS`, awaiting counsel).

A person becomes **due for deletion** when all of these are true:

- nothing has happened on their record for the whole period: no timeline line (an edit, a note, an email, a status
  change, a mapping and so on), no Admin keep, and they were added longer ago than that;
- they are on no live position (an archived position does not count);
- their processing is not restricted.

Nothing is deleted on its own, and nobody is notified. An Admin opens **Settings → Data & retention**, where **Due for
deletion** lists these people, longest untouched first, and for each one either:

- **Keep**: the person stays for another full period from today, and the keep is audited; or
- **Delete selected**: each person is erased exactly as in section 3, but **never added to the suppression list**,
  and audited with the reason "retention".

Just before deleting, each person is checked again. Anyone touched or mapped since the list was read is kept, and
anyone being emailed at that moment waits for the next review. The result says how many were deleted, how many were
no longer due, and how many were mid-send.

### 6b. System records: deleted automatically by an hourly sweep

`RetentionSweeper` deletes housekeeping rows once they pass their period. Every period is a default awaiting
counsel's sign-off, and each can be changed per deployment without a release.

| Record | Deleted after |
|---|---|
| Expired sign-in sessions (refresh tokens) | 30 days past the session's last expiry |
| Email verification and password reset links | 30 days past expiry |
| Two-factor sign-in challenges | 1 day past expiry |
| Invitations | 90 days after acceptance or expiry |
| Read or archived notifications (unread ones are kept) | 90 days |
| Daily notification digests | 90 days after sending |
| Assistant chats | 180 days after the last message |
| Fair-use and billing usage events | 400 days |
| Vendor caches of people and companies | 30 days after fetching |

Some deletions run independently of this sweep: a deleted note is purged for good after 30 days, files queued by an
erasure are removed, and Notetaker transcripts are cleared after `lightmove.recall.transcript-retention` where that is
set.

### 6c. Kept on purpose

| Record | Why |
|---|---|
| The audit trail | A security and legal record; the floor is for counsel to set, and deleting rows needs a maintenance role (#256). |
| The contact-credit ledger | A financial record; it cannot be changed. |
| Suppression list entries | Until an Admin removes them. Removing one lets the person back in. |
| Accounts, workspaces, members, mailbox and Zoom connections | While they are live; removed by offboarding (#926, not built yet). |
| Pictures used in sent email signatures | A sent email still shows them. |

The full table-by-table schedule is [retention-schedule.md](retention-schedule.md). A build test fails if a new
table holding personal data has no row there.

---

## 7. Turning it on

Three deployment switches decide what a deployment offers. All three are off by default.

| Switch (GitHub variable) | What it turns on | Needs |
|---|---|---|
| `DATA_PROTECTION_ENABLED` | Sections 2 to 5a: the suppression list, erasure, restriction and the candidate retention review. Off, their routes answer 404 and no screen offers them. | `SUPPRESSION_LIST_ENABLED` alongside it |
| `SUPPRESSION_LIST_ENABLED` | Mounts the suppression list's keyset. Without it, nothing can be added to the list, and an erasure that suppresses is refused. | The `lightmove-suppression-keyset` secret |
| `RETENTION_ENABLED` | The hourly sweep of system records (5b) | Counsel's sign-off on the periods (#930) |

**Turning `DATA_PROTECTION_ENABLED` off again does not lift anything.** Suppression entries and processing
restrictions made while it was on are still enforced at every route that files a person. But no screen can remove an
entry or lift a restriction until the switch is back on.

## 8. Not built yet

- Hiding a restricted person from client seats (waits on counsel).
- Cross-workspace suppression, for an objection sent to Uncava about vendor-sourced data (#920).
- Workspace offboarding and account deletion (#926).
- Deleting audit rows past a retention floor (#256).
- Counsel's sign-off on every period (#930).
