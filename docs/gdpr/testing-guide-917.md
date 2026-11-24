# GDPR epic #917: what is done, and how to test it locally

A plain-language guide for developers who are new to the codebase. It lists everything finished so far under
[epic #917](https://github.com/nextwebspark/app-lightmove/issues/917), says what each piece does, where to find it in
the app, and how to try it on your own machine.

State as of 2026-11-24, `main` at #1449.

> **What GDPR means here, in one paragraph.** Uncava holds data about two kinds of people: **users** (consultants who
> sign up) and **candidates** (executives who never signed up, whose details came from data vendors, the browser
> plugin, spreadsheets or the AI). The law gives both groups rights: to see their data, to have it deleted, to stop it
> being used, and to be told it is held. This epic builds the tools a firm needs to honour those rights.

---

## Contents

1. [The finished items at a glance](#1-the-finished-items-at-a-glance)
2. [The two switches you must know about](#2-the-two-switches-you-must-know-about)
3. [Prerequisites (one-time setup)](#3-prerequisites-one-time-setup)
4. [Start the app with the GDPR features on](#4-start-the-app-with-the-gdpr-features-on)
5. [Get some test data](#5-get-some-test-data)
6. [Feature by feature: what, where, how to test](#6-feature-by-feature-what-where-how-to-test)
7. [Run the automated browser scripts](#7-run-the-automated-browser-scripts)
8. [Run the backend and frontend tests](#8-run-the-backend-and-frontend-tests)
9. [Troubleshooting](#9-troubleshooting)
10. [What is not done yet](#10-what-is-not-done-yet)

---

## 1. The finished items at a glance

"Gated" means you only see it when `DATA_PROTECTION_ENABLED=true` (see section 2).

| # | Feature | Issue | PRs | Gated? | Has a screen? |
|---|---|---|---|---|---|
| 6.1 | Suppression list (people who may never be added again) | #920 | #1178, #1186 | Yes | Yes |
| 6.2 | Delete a person permanently (erasure) | #918 | #1179 | Yes | Yes |
| 6.3 | Restrict processing of a person | #920 | #1180 | Yes | Yes |
| 6.4 | Candidate retention review (Due for deletion) | #921 | #1186 | Yes | Yes |
| 6.5 | Automatic clean-up of old system rows | #921 | #1185 | Own switch | No |
| 6.6 | Export everything held on one person | #919 | #1288 | Yes | Yes |
| 6.7 | Workspace deletion and workspace export | #926 | #1188 | Export: yes. Delete: no | Yes |
| 6.8 | Delete my account, Download my data | #926 | #1190 | No | Yes |
| 6.9 | AI on people (four AI switches) and the gender/nationality API scope | #922 | #1300 | No | Yes |
| 6.10 | Candidate privacy notices and lawful basis | #923, #1267 ph.1 | #1328 | Yes | Yes |
| 6.11 | Privacy footer and "stop emails" link on outreach email | #924 | #1330 | Yes | Yes |
| 6.12 | Consent requests per purpose | #1267 ph.2 | #1341 | Yes + consent mode | Yes |
| 6.13 | Share with client (consent per position) | #1267 ph.3 | #1343 | Yes + consent mode | Yes |
| 6.14 | Terms tick on signup, re-acceptance, DPA tick | #928 | #1331 | No | Yes |
| 6.15 | Self-hosted fonts, strict CSP, no third-party trackers | #929 | #1338 | No | Small notes only |
| 6.16 | No personal data in logs or audit details | #927 | #1276, #1279, #1280 | No | No |
| 6.17 | Documents only: RoPA, DPIA, LIA, TIA, residency, breach runbook | #930, #925 | #1327, #1339 | — | No |

Supporting PRs: #1177 (mockups), #1260 (the `DATA_PROTECTION_ENABLED` switch), #1350 (the screen map in
[ui-changes.md](ui-changes.md)), #1367 (local dev starts with the switches off, like production).

---

## 2. The two switches you must know about

Most of this work is **switched off in production** until lawyers sign off. Local dev copies production, so it is
**off on your machine too** unless you turn it on.

| Switch | What it does when `true` | Default |
|---|---|---|
| `DATA_PROTECTION_ENABLED` | Shows the people controls: the drawer's ⋯ menu, most of Settings → Data & retention, the privacy page, the outreach footer. Off, their API routes answer 404. | `false` |
| `SUPPRESSION_LIST_ENABLED` | Loads the key used to hash suppression entries. **Always turn it on together with the one above**, or erasure and opt-out fail with `SUPPRESSION_UNAVAILABLE`. Locally a dev key is loaded for you. | `false` |
| `RETENTION_ENABLED` | Runs the hourly clean-up of old system rows (6.5). | `false` |
| `LEGAL_DPA_VERSION` + `LEGAL_DPA_URL` | Adds the "data processing agreement" tick when founding a workspace (6.14). | blank |

**Consent mode** is not an environment switch. It is a setting inside the app: Settings → Data & retention →
Candidate privacy → Mode = *Notices and consent requests*. Features 6.12 and 6.13 only appear in that mode.

---

## 3. Prerequisites (one-time setup)

You need:

| Tool | Why | Check |
|---|---|---|
| **Docker Desktop**, running | The local Postgres database runs in a container | `docker ps` |
| **Node.js 20+** | The web app, and the browser test scripts | `node -v` |
| **Java 21** | The API (the Maven wrapper `./mvnw` downloads Maven for you) | `java -version` |
| **Git Bash** (on Windows) | Every dev script is a Bash script | open "Git Bash" |
| **Python 3** | Only for `seed-approach` and the export test script | `python --version` |
| **curl** | Only for `seed-approach` | comes with Git Bash |

Then, once:

```bash
git clone https://github.com/nextwebspark/app-lightmove.git
cd app-lightmove
npm install                       # installs the web app, extension and Playwright
npx playwright install chromium   # the browser the test scripts drive
```

**Windows only.** npm runs scripts with `cmd.exe` by default, which breaks the Bash scripts behind `npm run dev`. If
`npm run dev` fails with something like `'.' is not recognized`, point npm at Git Bash (adjust the path to your
install):

```bash
npm config set script-shell "C:\\Program Files\\Git\\bin\\bash.exe"
```

Do **not** commit that to the repo's `.npmrc`; it is a personal setting.

You do **not** need gcloud, Cloud SQL or `application-local.yml` for anything in this guide. The only things that
need real outside services are noted per feature (the AI buttons need Vertex AI credentials; sending a real email
needs a connected Gmail or Outlook).

---

## 4. Start the app with the GDPR features on

Run everything from **Git Bash**, in the repo root.

### Easiest: one terminal

```bash
DATA_PROTECTION_ENABLED=true SUPPRESSION_LIST_ENABLED=true npm run dev 2>&1 | tee dev.log
```

- API: http://localhost:8080 · Web app: http://localhost:5173 · Database: localhost:55433
- `dev.log` keeps a copy of everything the API prints. You need it because **emails are not sent locally; they are
  printed to the API console** (sign-up links, privacy notices, consent requests). Search `dev.log` for `http` to find
  a link.

### Better for the test scripts: two terminals

```bash
# Terminal 1: database + API, with its log saved to api.log
npm run dev:db
DATA_PROTECTION_ENABLED=true SUPPRESSION_LIST_ENABLED=true npm run dev:api 2>&1 | tee api.log

# Terminal 2: web app
npm run dev:web
```

Wait until the API prints `Started ...Application`. The first start runs every database migration and takes a
minute or two.

**Extra switches for some tests** (add them in front of the same command):

| You want to test | Add |
|---|---|
| The DPA tick (6.14) | `LEGAL_DPA_VERSION=2026-11 LEGAL_DPA_URL=https://example.com/dpa` |
| Workspace purge without waiting 15 minutes (6.7) | `RETENTION_WORKSPACE_PURGE_INTERVAL=PT20S` |
| The hourly system clean-up (6.5) | `RETENTION_ENABLED=true` |

To stop: `Ctrl+C`. If ports 8080 or 5173 stay busy afterwards, run `npm run dev:stop`.

---

## 5. Get some test data

### Option A: the seed script (quickest)

With the app running:

```bash
npm run dev:db:seed-approach
```

It creates a workspace called "Approach Demo <time>" with:

- three users who can sign in: Yara (admin), Sara and Omar (members). The script prints their email addresses; the
  password for all three is `Approach-Demo-2026!`;
- three positions and six executives (candidates);
- a stand-in mailbox, so the email composer opens (sending fails at the last step, but the preview works).

Sign in at http://localhost:5173/login as **Yara** for everything that needs an admin.

### Option B: by hand

1. Open http://localhost:5173/signup, fill in a name, a work-style email (anything, e.g. `you@acme.test`) and a
   password, **tick the terms box**, and continue.
2. Find the verification link in `dev.log` / `api.log` (search for `verify`) and open it in the browser.
3. Name your workspace. You are its admin.
4. Create a position, then add an executive to it with a LinkedIn link and an email address. The email address
   matters: it is where privacy and consent emails "go" (printed in the log).

> Shortcut: start the API with `AUTH_AUTO_VERIFY_EMAIL=true` and new sign-ups are verified without the email link.

---

## 6. Feature by feature: what, where, how to test

Every "Where" path starts from the app's left sidebar. "Drawer" means: open **Candidates**, click a person's row, and
a panel slides in on the right. Its **⋯** button (top right, "More for {name}") holds most of the people controls.
The ⋯ menu only exists when `DATA_PROTECTION_ENABLED=true`.

### 6.1 Suppression list (#920, PRs #1178, #1186)

**What it does.** A workspace's "never add again" list. A person on it cannot be added by any route: Add executive,
the browser plugin, spreadsheet import, Find executives or Add to position. People are matched by LinkedIn profile or
email only. The list stores a one-way hash, never the actual link or address.

**Where.** Settings (avatar menu → Settings) → Workspace → **Data & retention** → *Suppression list*. Admins only.

**How to test.**
1. Sign in as an admin, open Data & retention, and add a LinkedIn profile URL (e.g.
   `https://www.linkedin.com/in/test-person/`) with the reason *Added by an admin*.
2. Notice the list shows the kind and reason, but **not** the URL itself.
3. Open a position and try to add an executive with that same LinkedIn URL. It is refused: "This person is on your
   workspace's suppression list".
4. Remove the entry (it asks for confirmation). Now the same person can be added.

**Automated:** `e2e/spa/data-retention.mjs` (cases DR2.x); `PersonSuppressionIntegrationTest`, `SuppressionGateTest`.

---

### 6.2 Delete a person permanently (#918, PR #1179)

**What it does.** Erases one candidate completely from the workspace: profile, contacts, notes, documents, emails,
outreach, meetings, recordings, tasks, on every position. Files in storage are removed in the background. By default
the person is also put on the suppression list so they cannot be re-added. The audit log records that it happened,
never the name.

**Where.** Candidates → click a person → **⋯** → **Delete person permanently…**. Admins only.

**How to test.**
1. As an admin, open a person you can spare.
2. Click ⋯ → Delete person permanently…. The dialog first **counts** what will go (positions, notes, files...).
3. Type the person's full name to unlock the button. Leave "Add to the suppression list" ticked.
4. Confirm. The person disappears from Candidates and from every position.
5. Open Data & retention → Suppression list: a new entry with the reason *Erased* is there.
6. As a member (not admin), check that the menu item is not offered.

**Automated:** `PersonErasureIntegrationTest`, `RecallBotErasureSweeperIntegrationTest`.

---

### 6.3 Restrict processing (#920, PR #1180)

**What it does.** "Pause" a person while a question is sorted out. The record can still be read and edited, but: no
AI runs on them, no contact lookup, no emails or meetings, sequences skip them, and nobody can add them to another
position. Ticking "also add to the suppression list" turns it into an **objection**.

**Where.** Candidates → click a person → **⋯** → **Restrict processing…** (anyone on the team). Lift it from the same
menu, or from the note in the drawer (only the person who set it, or an admin).

**How to test.**
1. Restrict a person with a reason. The drawer header now shows a **Processing restricted** pill and a note with the
   reason, who and when.
2. Open a position that person is on → Companies → click them: a strip says processing is restricted; AI and
   outreach buttons answer with a toast instead of running.
3. On Candidates, tick the person and choose **Add to position**: it is refused with a toast saying why.
4. Lift the restriction: a toast says "Restriction lifted" and everything works again.
5. Sign in as another member: they see "Only {name} or an admin can lift it."

**Automated:** `PersonProcessingRestrictionIntegrationTest`.

---

### 6.4 Candidate retention review (#921, PR #1186)

**What it does.** The admin chooses how long to keep a candidate nobody is working with (12, 24, 36 or 60 months, or
never). People past that period appear in a **Due for deletion** list. **Nothing is deleted automatically**: an admin
picks Keep (another full period from today) or Delete selected (erased like 6.2, but **not** suppressed).

A person is "due" only when: nothing has happened on their record for the whole period, they are on no live position,
and they are not restricted.

**Where.** Settings → Workspace → **Data & retention** → *Candidate retention* and *Due for deletion*. Admins only.

**How to test.** Nobody becomes two years old in a test session, so the easiest way is the script, which ages people
in the database for you:

```bash
API_LOG=api.log node e2e/spa/data-retention.mjs
```

To try it by hand, age a person with SQL (`npm run dev:db:psql`, then as the superuser) by moving their activity
dates back, take them off every live position, then reload Data & retention. Look at how the script does it in
`e2e/spa/data-retention.mjs` before writing your own SQL.

Check: the empty state reads "Nobody is due for deletion"; Keep removes the row; Delete selected asks first and then
erases.

**Automated:** `e2e/spa/data-retention.mjs` (DR1.x); `CandidateRetentionIntegrationTest`.

---

### 6.5 Automatic clean-up of old system rows (#921, PR #1185)

**What it does.** An hourly job (`RetentionSweeper`) deletes housekeeping rows past their age: expired sessions,
old password-reset links, read notifications, old assistant chats, vendor caches and so on. The full list and every
period is in [retention-schedule.md](retention-schedule.md). A build test (`RetentionScheduleCoverageTest`) fails if
someone adds a personal-data table without a row in that file.

**Where.** No screen.

**How to test.** Start the API with `RETENTION_ENABLED=true` and watch the API log for the sweep each hour. In
practice the tests are the check:

```bash
cd apps/api && ./mvnw test -Dtest='RetentionSweeperTest,RetentionScheduleCoverageTest'
```

---

### 6.6 Export everything held on one person (#919, PR #1288)

**What it does.** Answers a "send me a copy of my data" request. Downloads one zip with `person.html` (readable in a
browser), `person.json` (machine-readable), and the latest file of each of their documents plus their photo. It also
contains fixed text explaining why the data is held, who sees it, how long it is kept, and how each AI feature works.
It changes nothing and is audited by id.

**Where.** Candidates → click a person → **⋯** → **Export personal data…** → **Download**. Admins only.

**How to test.**
1. Pick a person with a note and a document on them.
2. Export, unzip, and open `person.html`. Check the profile, positions, notes and documents are all there.
3. As a member, check the menu item is not offered.

**Automated:** `API_LOG=api.log node e2e/spa/subject-access-export.mjs` (needs Python); `PersonSubjectAccessIntegrationTest`,
`SubjectAccessCoverageTest` (fails if a new table holding a `person_id` is neither exported nor listed as excluded in
[subject-access-export.md](subject-access-export.md)).

---

### 6.7 Workspace deletion and workspace export (#926, PR #1188)

**What it does.** An admin can export the whole workspace as a zip, and can delete the workspace. Deletion has two
stages: **now** members lose access; after the **grace period** (30 days) everything is purged and a deletion
certificate is emailed. During the grace period the platform team can restore it.

**Where.**
- Export: Settings → Workspace → **Data & retention** → *Export workspace data* → Start export (gated).
- Delete: Settings → Workspace → **General** → *Delete workspace* (not gated). Admins only.

**How to test.**
1. Start the API with `RETENTION_WORKSPACE_PURGE_INTERVAL=PT20S` so the purge job runs every 20 seconds.
2. In Data & retention, click Start export. "Building your export…" appears; the ready email (with the download link)
   is printed in the API log.
3. In General, click Delete workspace. Type the workspace name to confirm. You are moved out of the workspace.
4. The grace period is 30 days, so the script fast-forwards it with SQL. Run:

```bash
RETENTION_WORKSPACE_PURGE_INTERVAL=PT20S npm run dev     # in one terminal, with the log saved
API_LOG=api.log node e2e/spa/workspace-offboarding.mjs   # in another
```

**Automated:** `e2e/spa/workspace-offboarding.mjs`; `WorkspaceOffboardingIntegrationTest`.

---

### 6.8 Delete my account and Download my data (#926, PR #1190)

**What it does.** Any user can download their own account data as JSON and delete their own account. If they are
the **only admin** of a workspace or the **only lead** of a live position, they are shown what to hand over first and
cannot delete yet. Deletion asks for their password (or two-factor code, or a fresh Google/LinkedIn sign-in).

**Where.** Settings → Your account → **Profile** → *Download my data* and *Delete my account*. Not gated.

**How to test.**
1. Sign in as the only admin of a workspace and click Delete my account: the dialog names the workspace to hand over
   and offers no delete button.
2. Sign in as a member (e.g. Sara from the seed). Click **Download my data**: a file named `uncava-account-….json`
   downloads with your email, workspaces and sign-ins.
3. Click Delete my account, try a wrong password (refused under the field), then the right one. You land on `/login`
   with "Your account was deleted."
4. Try to sign in again: it fails.

**Automated:** `API_LOG=api.log node e2e/spa/account-deletion.mjs`; `AccountDeletionIntegrationTest`.

---

### 6.9 AI on people (#922, PR #1300)

**What it does.** Four per-workspace switches: **Infer gender**, **Infer nationality**, **Draft assessments with AI**,
**AI-enrich on capture**. Turning gender or nationality off asks first and then wipes the AI's unconfirmed guesses
across the workspace. New workspaces start with all four **off**; older ones kept them on. Separately, the public API
and MCP only return gender and nationality to a key that holds the new `candidates.demographics:read` scope.

This one is **live in production** and not gated.

**Where.**
- Settings → Workspace → **Data & retention** → *AI on people* (admins). This card shows even with the flag off.
- Settings → Your account → **API keys** → the new scope when creating a key.

**How to test.**
1. Open Data & retention. A brand-new workspace shows all four switches off.
2. Turn **Draft assessments with AI** off, open a position → Companies → an executive → Assessment: **Assess with AI**
   is hidden. Turn it on again: it is back. (Actually running the AI needs Vertex AI credentials; checking the button
   appears/disappears does not.)
3. Turn Infer gender on, then off: a confirmation asks "Turn off and clear" and says "This can't be undone."
4. Create an API key without the demographics scope and call `GET /api/v1/public/...` for a candidate: no `gender` or
   `nationality` fields. With the scope, they appear (if recorded).

**Automated:** `WorkspaceAiControlsIntegrationTest`. Detail: [ai-controls-922.md](ai-controls-922.md).

---

### 6.10 Candidate privacy notices and lawful basis (#923 / #1267 phase 1, PR #1328)

**What it does.** The law says a person must be told when a firm holds their data. This lets the firm:
- save its details once (legal name, privacy contact email, privacy notice link, default lawful basis);
- send a **privacy notice** email to one or many candidates, or **mark** someone as told another way;
- record a **lawful basis** per person;
- give each candidate a **public privacy page** (no login) where they see what is held and can choose: *Don't contact
  me again*, *Send me a copy of my data*, *Delete my data*. Each choice notifies the admins via the bell;
- list who has never been told: the **Not notified** view.

**Where.**
- Settings → Workspace → Data & retention → *Candidate privacy* (fill this in first; nothing else works until it is
  saved).
- Candidates → person → ⋯ → **Send privacy notice…** / **Mark as notified…**
- Candidates → person → **Contact** tab → *Privacy* block (lawful basis, Notified / Not notified).
- Candidates → tick people → selection bar → **Privacy notice** (bulk).
- Candidates → Filter → Show → **Not notified**.
- Public page: `/privacy/<token>` (the link in the email).
- Settings → Your account → Notifications → "A candidate objects or asks for their data" (admins).

**How to test.**
1. As admin, fill in Candidate privacy (any values, e.g. "Acme Search LLC", `privacy@acme.test`,
   `https://acme.test/privacy`) and save.
2. Open a person who has an email. The Contact tab's Privacy block says **Not notified**.
3. ⋯ → Send privacy notice…, check the preview, send. The block now says Notified with today's date.
4. Find the email in `api.log` and copy the `/privacy/...` link. Open it in a **private / incognito window** (no
   session). The page names the firm and the kinds of data, but **not** the person's name or email.
5. Click **Don't contact me again** and confirm. Back in the app: the person shows **Do not contact**, and the admin's
   bell (top bar) has "{person} objected to being contacted".
6. Break the token in the URL: the page says "This link isn't valid".

**Automated:** `API_LOG=api.log node e2e/spa/privacy-notices.mjs` (PN1–PN4); `PersonPrivacyNoticeIntegrationTest`.
Detail: [privacy-notices-923.md](privacy-notices-923.md).

---

### 6.11 Privacy footer and "stop emails" on outreach (#924, PR #1330)

**What it does.** Every outreach email gets a short footer: who holds the data, where it came from, a link to the
privacy notice, and a **Stop emails from us** link. Clicking stop marks the person do-not-contact, stops their
sequences, and tells the sender. Gmail mailboxes also get the one-click `List-Unsubscribe` header (Outlook can't carry
it, so Outlook gets the footer link only).

**Where.**
- Position → **Outreach** → *Add to sequence* dialog and the reply composer: a read-only footer preview under the email.
- Candidate drawer header: **Opted out by email.** banner after an opt-out.
- Public page `/privacy/<token>?action=opt-out`.

**How to test.**
1. Use the seed workspace (option A in section 5), which has a stand-in mailbox. Open a position → Outreach → pick a
   candidate → Add to sequence. Under the email body you see "{firm} holds your professional details, which came from
   … Our privacy notice · Stop emails from us".
2. Sending for real needs a connected Gmail/Outlook, so test the stop link through a privacy page instead: take a
   `/privacy/<token>` link from step 6.10, add `?action=opt-out`, and open it in a private window.
3. The page asks **"Stop emails from {firm}?"** first. (Just opening the link does nothing; that is on purpose, so a
   mail scanner fetching links can't opt anyone out.) Confirm.
4. In the app the drawer shows **Opted out by email.**, and the bell says "{person} opted out of emails".

**Automated:** `e2e/spa/privacy-notices.mjs` (PN5.x); `OutreachPrivacyServiceTest`.

---

### 6.12 Consent requests (#1267 phase 2, PR #1341)

**What it does.** For firms that want **consent** rather than just a notice. The firm offers up to three purposes:
*keep on file for future roles*, *share with a client for a specific role*, *career and market updates*. Staff ask a
candidate by email; the candidate answers Yes/No per purpose on their privacy page (nothing pre-selected), and can
withdraw later from a "manage" link. Consents expire after a set time; the app can ask again before they end.

**Where.**
- Settings → Data & retention → Candidate privacy → Mode = **Notices and consent requests** (only selectable after
  the firm's details are saved). Choose purposes, *Valid for*, *Ask again N days before*. A **consent register** CSV
  download is here too.
- Candidates → person → ⋯ → **Ask for consent…** / **Record an answer…**
- Contact tab → Privacy block: one row per purpose with a status pill (Not asked, Asked, Given, Declined, Withdrawn,
  Expired...).
- Candidates → tick people → **Ask for consent** (bulk).
- Candidates → Filter → Show → **Asked, no answer** · **Consent ending in 30 days** · **Declined or withdrawn**.

**How to test.**
1. Save the firm's details (6.10 step 1), then switch Mode to *Notices and consent requests* and tick *Career and
   market updates*. Save.
2. Open a person → ⋯ → Ask for consent…. The email is previewed word for word. Send. The Privacy block rows say
   **Asked**.
3. Copy the link from `api.log`, open it in a private window. Nothing is pre-selected. Say **Yes** to "keep on file"
   and **No** to "career and market updates".
4. Back in the app: keep-on-file says **Given** "Until <date>"; updates says **Declined**.
5. The confirmation email (in the log) has a manage link. Open it, withdraw keep-on-file, confirm.
6. Candidates → Filter → Show → **Declined or withdrawn** lists the person.

**Automated:** `API_LOG=api.log node e2e/spa/consent-requests.mjs` (CR1–CR4); `PersonConsentIntegrationTest`.
Detail: [consent-requests-1267.md](consent-requests-1267.md).

---

### 6.13 Share with client (#1267 phase 3, PR #1343)

**What it does.** Before a candidate is shown to the hiring client on a position, staff can ask the candidate for
permission. Making someone client-visible without that consent shows a warning first (it warns, it never blocks).
The email says "a confidential client" unless the client is named.

**Where.** Needs consent mode with the *share with a client* purpose offered.
- Position → Companies → any stage → **Pipeline** view: each card has a badge (**No consent to share** / **Consent
  asked** / **Consent given**) and ⋯ → **Ask to share with client…**
- Turning a person client-visible (Pipeline, or Outreach → candidate pane → **Client visible** switch) without consent
  opens "No consent to share with {client} yet": *Ask first* · *Show anyway* · *Cancel*. Hiding never asks.
- Position → Companies → Table or Map → **Advanced ▾** → **Consent register** (CSV for that position).

**How to test.**
1. In Candidate privacy, tick the *share with a client* purpose too. Save.
2. Open a position → Companies → a stage → Pipeline. A card says **No consent to share**.
3. Make that person client-visible: the prompt appears. Click **Ask first**. The person stays hidden from the client;
   the card says **Consent asked**.
4. Open the share link from `api.log` in a private window and say Yes. The card now says **Consent given**, and turning
   visibility on no longer asks.
5. Advanced ▾ → Consent register downloads a CSV with that share and **no email address**.

**Automated:** `e2e/spa/consent-requests.mjs` (CR5.x); `ShareConsentIntegrationTest`.

---

### 6.14 Terms tick, re-acceptance and the DPA tick (#928, PR #1331)

**What it does.** Users must actively tick the terms box on every way in: password signup, accepting an invite, and a
first Google/LinkedIn sign-in (which now shows a "One more step" page before the account is created). When the terms
version changes, a "We've updated our terms" sheet covers the app until accepted. If the deployment names a DPA, the
founder of a workspace must also tick it. Not gated.

**Where.** `/signup`, `/auth/accept-invite`, `/signup/terms`, `/signup/workspace`, Settings → Your account →
Workspaces → New workspace.

**How to test.**
1. Open `/signup`, fill everything in **without** ticking terms: you cannot continue. Tick it: you can.
2. Invite a colleague (the **Team** page, `/team`). Open the invite link from the log: the same tick is required.
3. Restart the API with `LEGAL_DPA_VERSION=2026-11 LEGAL_DPA_URL=https://example.com/dpa`. Sign up a new user: the
   workspace step now has a DPA tick. Without those variables there is no DPA tick.
4. The re-acceptance sheet only shows after a terms version bump, which hasn't shipped, so nobody sees it today. The
   script fakes it.
5. The Google/LinkedIn "One more step" page needs real OAuth credentials; it is covered by tests instead.

**Automated:** `API_LOG=api.log PG_CONTAINER=lm-dev-pg node e2e/spa/terms-acceptance.mjs`;
`TermsAcceptanceIntegrationTest`, `OAuthLoginIntegrationTest`. Detail: [terms-acceptance-928.md](terms-acceptance-928.md).

---

### 6.15 Third-party loads: fonts, CSP, trackers (#929, PR #1338)

**What it does.** The app no longer contacts Google Fonts (fonts are served from our own server), Mapbox telemetry and
the booking widget's error reporter are off, and the server sends a strict Content-Security-Policy that blocks any
script or style from a source we didn't approve. Small privacy notes were added to the booking page and the Chrome
extension's settings.

**Where.**
- No visible change on most screens (fonts look the same).
- `/book/<slug>`: "Our scheduling provider handles the times shown and the details you enter. Privacy".
- Chrome extension → Settings screen → **Privacy**.

**How to test.**
1. Open the app, press F12 → **Network** tab, reload. Filter for `fonts.googleapis` or `fonts.gstatic`: nothing.
2. The CSP header is sent by Spring, not by the Vite dev server, so to see it you must serve the built app from the
   API:

```bash
cd apps/web && npm run build && cp -r dist/. ../api/target/classes/static/
# restart the API, then open http://localhost:8080 (not :5173)
cd ../../e2e && node spa/third-party-loads.mjs
```

3. In DevTools → **Console**, there should be no "Content Security Policy" errors on the maps, the editor, the booking
   page or the OAuth consent page.

**Automated:** `e2e/spa/third-party-loads.mjs`; `SpaSecurityTest`.

---

### 6.16 No personal data in logs or audit details (#927, PRs #1276, #1279, #1280)

**What it does.**
- **Audit details hold ids only**, never names or emails. The position's *Recent activity* panel looks up the names
  from live rows instead, so it looks the same, but an erased person's name vanishes from it.
- **Logs are masked** in deployed environments: emails, LinkedIn links and phone numbers are replaced before a log
  line leaves the server (`PersonalDataLogMasker`).
- A scorecard draft kept in the browser is cleared on sign-out.
- Backups, recovery and encryption are documented in [security-of-processing.md](security-of-processing.md).

**Where.** No screen.

**How to test.** Local dev uses plain console logs on purpose (so you can read emailed links), so masking is **not**
visible locally. The tests are the check:

```bash
cd apps/api && ./mvnw test -Dtest='PersonalDataLogMaskerTest,AuditDetailKeysTest'
```

`AuditDetailKeysTest` fails if any new audit event puts a name-like key in its details. You can also check by hand:
`npm run dev:db:psql`, then `select event_type, target_id, metadata from app_lm_audit_event order by occurred_at desc limit 10;`
and see only ids and counts.

---

### 6.17 Documents only (#930, #925; PRs #1327, #1339)

No code. These are drafts for the lawyers, in `docs/gdpr/`: [ropa.md](ropa.md), [lia.md](lia.md), [dpia.md](dpia.md),
[tia.md](tia.md), [subprocessors.md](subprocessors.md), [residency-adr.md](residency-adr.md),
[breach-runbook.md](breach-runbook.md), [subject-requests-procedure.md](subject-requests-procedure.md),
[governance-decisions.md](governance-decisions.md). Nothing to test; read them to understand the reasons behind the
features.

---

## 7. Run the automated browser scripts

These scripts open a real (headless) Chrome against your running app and click through each feature. They create
their own users and data, so they don't touch yours.

1. Start the app as in section 4 (two-terminal way), with the switches on and the API log in `api.log`.
2. From the repo root:

```bash
API_LOG=api.log node e2e/spa/privacy-notices.mjs        # 6.10, 6.11
API_LOG=api.log node e2e/spa/consent-requests.mjs       # 6.12, 6.13
API_LOG=api.log node e2e/spa/subject-access-export.mjs  # 6.6 (needs python)
API_LOG=api.log node e2e/spa/data-retention.mjs         # 6.1, 6.4
API_LOG=api.log node e2e/spa/account-deletion.mjs       # 6.8
API_LOG=api.log PG_CONTAINER=lm-dev-pg node e2e/spa/terms-acceptance.mjs   # 6.14
```

Two need a special API start:

| Script | Start the API with |
|---|---|
| `workspace-offboarding.mjs` (6.7) | `RETENTION_WORKSPACE_PURGE_INTERVAL=PT20S` added |
| `third-party-loads.mjs` (6.15) | the built bundle copied into the API (see 6.15), run from `e2e/` |

Each step prints a green PASS or red FAIL line with its case number (PN1.1, CR3.2...). Screenshots land in
`e2e/spa/screenshots/` (or the `RUN_DIR` you set).

---

## 8. Run the backend and frontend tests

```bash
# All GDPR backend integration tests (needs Docker running; uses Testcontainers)
cd apps/api && ./mvnw test -Dtest='Person*IntegrationTest,ShareConsentIntegrationTest,AccountDeletionIntegrationTest,WorkspaceOffboardingIntegrationTest,CandidateRetentionIntegrationTest,TermsAcceptanceIntegrationTest,WorkspaceAiControlsIntegrationTest,Suppression*Test,SubjectAccess*Test,Retention*Test,AuditDetailKeysTest,PersonalDataLogMaskerTest,SpaSecurityTest'

# The whole backend suite (slow: around 20 minutes)
cd apps/api && ./mvnw test

# Frontend unit tests, and the real typecheck
cd apps/web && npx vitest run
cd apps/web && npm run build
```

---

## 9. Troubleshooting

| Problem | Fix |
|---|---|
| No ⋯ menu in the drawer; Data & retention shows only *AI on people* | You started without `DATA_PROTECTION_ENABLED=true`. Restart with it. |
| Erasure or opt-out says `SUPPRESSION_UNAVAILABLE` | Add `SUPPRESSION_LIST_ENABLED=true` next to `DATA_PROTECTION_ENABLED=true`. |
| Mode *Notices and consent requests* is greyed out | Save the firm's legal name, privacy contact and notice link first. |
| "Ask for consent" / share badges missing | You are in *Notices* mode, or the purpose isn't ticked. |
| No email arrives | Emails are never sent locally. Look in `api.log` / `dev.log`. |
| A test script can't find a link | It reads `API_LOG`. Make sure the API's output is going to that file (`| tee api.log`). |
| `npm run dev` fails on Windows with a `cmd` error | Set npm's script shell to Git Bash (section 3). |
| Ports 8080 / 5173 already in use | `npm run dev:stop`. |
| The DPA tick doesn't show | Both `LEGAL_DPA_VERSION` and `LEGAL_DPA_URL` must be set. |
| Vitest: every mock fails on Windows | Run from `C:\` with a capital drive letter, and on Node 26 set `NODE_OPTIONS=--no-experimental-webstorage`. |
| Want a clean database | `npm run dev:db:reset` (wipes your local data; the next start re-runs every migration). |

---

## 10. What is not done yet

Not in this guide because it isn't finished:

- **#921**: deleting old audit-log rows (waits on #256), and the lawyers' sign-off on every period.
- **#922**: the report's diversity chapter still counts unconfirmed AI guesses for gender and nationality; legal
  questions (DPIA, Art. 22, nationality for EU/UK people).
- **#925**: Vertex AI still runs at `global` (decided: stays, because the model is global-only).
- **#927**: production ops steps (turn on backups and point-in-time recovery, run the audit scrub, restore drill).
- **Public request page** `/privacy/requests` for people a firm doesn't hold (website #28).
- **Hiding a restricted person from client seats**, and **cross-workspace suppression** (waiting on counsel).
- **Going live**: turning `DATA_PROTECTION_ENABLED`, `SUPPRESSION_LIST_ENABLED` and `RETENTION_ENABLED` on in
  production.

The full picture, kept up to date, is the epic itself and [ui-changes.md](ui-changes.md).
