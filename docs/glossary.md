# Glossary — the words on screen

The code, routes, API and tables keep their own names (`project`, `client`, `triage`, `lightmove`).
This page is about what a user reads.

**The rule: a mockup's wording wins.** `uncava-design/*.dc.html` is the source of truth for every
screen. Where a mockup still says a word this page would avoid, the screen says it too until the mockup
changes. Copy no mockup draws — toasts, errors, banners, empty states — follows this page.

## Words we use

| Say | For | Not |
|---|---|---|
| **Uncava** | the product | LightMove |
| **Position** | a project: one open role, end to end | project (mandate where a mockup says it) |
| **Business unit** / **Client** | who a position is for — in-house / agency | a literal either way: `useWorkspaceVocabulary().unit` |
| **Hiring manager** / **Client contact** | the people there who read the work | a literal either way: `useWorkspaceVocabulary().contact` |
| **Company** / **Agency** | the workspace's own firm, as an email signature names it — in-house / agency | a literal either way: `useWorkspaceVocabulary().ownFirm` |
| **Executive** | a person mapped at a company on a position | — |
| **Candidate** | the same person on the workspace's Candidates page | — |
| **Universe** | the companies a position has taken from the market (In universe / Shortlisted / Declined) | triage |
| **Out of process** | a status that takes whoever holds it out of the search — not interested, screened out, declined offer — whatever stage it sits under | terminal, dead |
| **Reason** (for leaving the process) | why someone left a position at an out-of-process status (Not interested, Withdrew, Client rejected…), picked from the workspace's own list; staff-only | rejection reason, disposition, archive reason |
| **Out of process** (the board's band) | the band under the Pipeline board's columns: everyone at a status out of process, under the stage their way out sits in | Inactive, archived, declined (for people) |
| **Way out** | one of a stage's statuses out of process (Not interested, Unresponsive, Withdrew…): the first field of the dialog that asks why someone left — "Choose a way out", "Moving them out of process at Approach." | exit status, closing status |
| **Recently approached** | the amber note before an email, a sequence or an add: who on the team already reached the person (an email, a reply, a sequence, a logged call, LinkedIn, WhatsApp or meeting), when, and for which position, and where else they are in process. A call that got No answer or a Wrong number reached nobody and is left out | conflict, duplicate, already contacted, ownership |
| **Last call** | the newest call anyone on the team logged with the person, on any position: the day, how it went and who | last contact, last touch |
| **Conversation** | everything with one executive on one position on the Outreach page: emails both ways, run events, notes, meetings | thread, inbox |
| **Filter** / **Add filter** / **Clear all** | a grid's toolbar button beside the search, its panel's "add one on any column", and the one place every filter in force is cleared; a column header's own menu says **Filter by** | Advanced filter, a Clear all in a column header |
| **To do** | the Outreach page's filter of the people waiting on someone; its groups are Needs reply, Follow-ups, Before first email and Delivery problems | Action queue, Needs action, inbox |
| **Working hours** | the days and hours a consultant works, which a booking link offers times inside (Outlook's own, theirs, or the workspace's) | availability window, office hours |
| **Time zone** / **Date format** / **Time format** | Settings → General's Date & time (the workspace's) and Settings → Profile's (a person's own) | timezone setting, locale, region |
| **Automatic** | a person's time zone following their device, the first option of Profile's Timezone picker | auto-detect, device default |
| **Default time zone** | the workspace's zone on General: a member's until their device is known, and workspace emails' | workspace time zone, company zone |
| **Workspace default** | a person's date or time format left to the workspace, the first option of those Profile pickers | inherit, follow, auto |
| **Reset to standard** | putting the country's zone and the standard formats (04 Dec 2026, 24-hour) back on General | defaults, restore |
| **Email signature** | what closes an email a consultant sends from Uncava, one of their own kept in Settings → Email signature, the default ticked in the composer | footer, sign-off |
| **Email template** | a saved email or reply a consultant applies in the composer, kept in Settings → Email templates, personal or shared with the workspace — never Settings → Templates, which is the position brief library | snippet, canned reply, saved reply, role template |
| **Variable** | a `{{firstName}}` in a sequence step or an email template, filled in for each executive when the email is written, inserted from the same Insert bar in both; in an email to one person it reads as their value, in a chip | token, merge field, placeholder |
| **Home** | the page after sign-in: Needs you, Today, Your positions and charts, each a tile in the person's own layout; a client's Home is the positions shared with them | dashboard, landing page, My work |
| **Needs you** | Home's one list of what is owed today: executives waiting in outreach and the person's own tasks; Mine or, for a lead, Team | inbox, action queue, feed |
| **Positions** | the one list of positions: every one for an admin, the ones a person is on for anyone else | My positions, All positions, projects |
| **Customize** / **Save layout** / **Reset to the default** | Home's edit mode, as a dashboard's Edit layout: drag and resize tiles, Add chart, × to take a tile off, Options for sections and Needs you; kept on Save layout, and Reset puts back the layout everyone starts from | personalize, configure, pin |
| **Start page** | where Uncava opens in a workspace on a sign-in or when opened without a link: Home, Positions, Candidates, Tasks, one of your dashboards, or a position on a chosen tab; set in Settings → Profile | landing page, default view, homepage |
| **Fallback** | what a variable says for an executive with no value, written after a bar: `{{firstName\|there}}` | default value |
| **Daily email limit** | the most one mailbox sends through Uncava in a day | quota |
| **Synced email** | a reply read back from the consultant's own mailbox, as the history tags it | imported, fetched |
| **Ranking** | the Dashboard's staff-only chapter ordering a position's assessed candidates by their assessment total | leaderboard, score table |
| **Agreed baseline** | the brief's own technical share and competency weights, which the ranking starts from | default weights |
| **Exploration scenario** | the one shared what-if weighting a position keeps beside the baseline; it never changes the brief | simulation, model |
| **Not assessed** | a candidate on the position with no score against a current competency | unranked, zero |
| **Archive** / **Restore** | putting a position or a business unit away, hidden from every list and picker, and bringing it back; nothing is deleted | delete, close |
| **Uncava support** | who switches on a feature a workspace cannot | "this deployment" |
| **Audit log** | the workspace's record of sign-ins, member and access changes, exports and downloads, settings, API keys and AI apps, and outreach, in Settings → Security for an admin | activity log, events |
| **Suppression list** | the people a workspace may not add again — erased at their request, objecting to being processed, or entered by an admin — recognised by LinkedIn profile or email, in Settings for an admin | blocklist, blacklist, do-not-add list |
| **Privacy notice** | the email Uncava sends in the firm's name telling a candidate it holds their details, where they came from and how to object or ask for a copy, from the Candidates drawer or a selection | GDPR email, Art. 14 notice, data notice |
| **Notified** / **Not notified** | whether a candidate was told the firm holds their details — by a privacy notice, their first outreach email, or a colleague marking it by hand; Not notified is also a Candidates Filter → Show choice for people held over a month and never told | informed, aware, Art. 14 status |
| **Lawful basis** | why the firm may hold a candidate's details — Legitimate interest, Consent or Contract — the workspace default unless one is recorded on the person | legal basis, processing ground |
| **Candidate privacy** | Settings → Data & retention's card naming the firm as controller: its legal name, privacy contact, notice link and default lawful basis | controller settings, GDPR settings |
| **Privacy footer** | the fixed lines every outreach email ends with: the firm, where the details came from, its privacy notice and Stop emails from us | unsubscribe footer, legal footer |
| **Opted out** | a candidate used an email's Stop emails from us, or their mail app's Unsubscribe: every sequence stopped, do not contact set, and on the suppression list | unsubscribed |
| **Privacy page** | the public page a privacy notice links to, where a candidate objects or asks for a copy or deletion | data portal, preference centre |
| **Consent request** | the email Uncava sends in the firm's name asking a candidate to agree to each purpose on its own, from the Candidates drawer or a selection; they answer Yes or No on their privacy page, and no answer changes nothing | consent email, opt-in request, GDPR consent |
| **Ask for consent** / **Record an answer** | sending a consent request, and recording an answer a candidate gave another way (a call, their own email) | request consent, log consent |
| **Keep on file** | the purpose of keeping a candidate's details for future roles for 12, 24 or 36 months; while it is given, the candidate stays out of the retention review | retain, retention consent |
| **Career and market updates** | the marketing purpose, recorded only: Uncava sends no updates | newsletter, marketing consent |
| **Given** / **Declined** / **Withdrawn** / **Expired** | where a purpose stands: agreed, said no, took it back, or a request nobody answered in 30 days (or a consent past its date) | accepted, rejected, revoked, lapsed |
| **Consent register** | the CSV of every consent request and answer, from Settings or a position's menu; never a contact detail | consent log, GDPR export |
| **Ask to share with {unit}** / **Name the {unit}** | asking someone on a position whether their profile may go to its business unit or client (`useWorkspaceVocabulary().unitLower` on staff screens; the candidate's email and page always say "client"); the email says "a confidential client" unless Name the client is ticked, which a confidential position never allows | share consent, disclosure request |
| **No consent to share** / **Consent asked** / **Consent given** | a Pipeline card's badge for that consent; turning visibility on without it asks first, and never refuses | unconsented, pending |
| **Withdraw** | taking a consent back on the manage page a confirmation email links to, as easily as it was given | revoke, opt out |
| **Export personal data** | an admin's one zip of everything held on a person, to answer their request for a copy of it (subject access), from the Candidates drawer's ⋯ | DSAR, SAR, subject access request export |
| **Two-factor authentication** | a code from an authenticator app asked for after the password, turned on in Settings → Security | MFA, 2FA, TOTP, OTP |
| **Authenticator app** | the phone app that shows the six-digit code | token, device |
| **Setup key** | the secret typed into an authenticator app instead of scanning the QR code | seed, secret |
| **Recovery code** | one of ten single-use codes that sign in when the phone is lost | backup code, scratch code |
| **Fits the role** / **Doesn't fit the role** | Find executives' verdict on someone found: their current title holds the search's words at the levels searched, or it does not (with why) | qualified, matched, holds the seat |
| **Title not on record** | someone Find executives found at a company with no current title on record, whom no free check reached | no title, unknown |
| **Live profiles** / **Profile index** | Find executives' two sources, in *How we searched*: today's titles bought a profile at a time, and who works where, searched cheaply | the vendors' names |
| **Strong** / **Good** / **Possible** | how well someone who fits the role fits it, from their score | score, rank, rating |
| **Connected** / **Left voicemail** / **No answer** / **Wrong number**; **Sent a message** / **They replied** | how a logged call, or a logged WhatsApp, went: the sequence call step's own words. An outcome that is not the note kind's own is refused with "That outcome doesn't fit this kind of note" | disposition, status, result, call result |
| **Not now** / **Done** / **N more to log** / **Fix number** | the log-it tray's words: a card set aside unlogged, one closed after Wrong number, the cards folded under the newest, and the way to Edit contact details after a wrong number | dismiss, skip, snooze, queue |
| **Call logged** / **WhatsApp logged** | the toast once a log that says how it went is saved (from the tray or the composer); "Call logged · Wrong number" for that outcome | note saved, call saved |
| **12d** | a trial's days left, where a phone has room for nothing longer (the topbar chip; its spoken name is "Trial · 12 days left") | — |
| **Platform** | the area only Uncava's own staff reach, from the avatar menu: what every workspace shares. Its header reads **Uncava platform** · Staff only | admin console, back office, super admin |
| **Confirm it's you** | the dialog asking a super admin for a fresh two-factor code before a high-impact platform act (a variable change, Put live, Roll back) | step-up, sudo, re-authenticate |
| **Back to workspace** | the platform rail's way out, to the workspace the session is in | exit, close console |
| **Put live** / **Roll back** / **Version** / **Shipped** / **Edited** / **Try it** | Platform → Prompts: making a version of a prompt the one every workspace runs on, going back to an earlier one, one saved text with its model and settings, a version a release brought, a version saved in the console, and one run on a fictional input | promote, deploy, production, revert, bundled, playground |
| **Variables** / **Default** / **Changed** / **Reset to default** | Platform → Variables: a value the application runs on that Uncava's staff change without a release. The default is what the release shipped; Changed marks one that differs from it; a reset puts the default back. Never the `{{firstName}}` kind of Variable, which is an email's | settings, config, flags, override |
| **Evals** / **Dataset** / **Case** / **Dev** / **Holdout** / **Synthetic** | Platform → Evals: scoring one version of a prompt against another. A dataset is one prompt's set of cases; a case is one fictional input with what a careful reader expects of the answer; dev cases are for working on a prompt and holdout cases are kept back for the check before a version goes live. Every case is synthetic, and a case is retired, never edited or deleted | test set, golden set, sample, example, real data |
| **Run** / **Candidate** / **Against** / **Check** / **No worse** / **Regressed** | a run has a candidate version and the one it is measured against (the live version unless another is chosen) answer the same cases; a check is one rule every answer is held to, shown as the share of answers that pass; a finished run is No worse or Regressed, and a changed case is a Regression or Better. A run that was cancelled or stopped "gives no result" | job, experiment, baseline (on screen), scorer, pass / fail, gate |
| **Rubric** / **Criterion** / **Judged criteria** / **The judge** / **Counts** / **Shown only** / **Label pairs** / **No result** | a dataset's rubric is the criteria the judge compares two answers on; the judge picks the better answer of a pair on each criterion, or a tie, and a run counts the pairs each version won. Judged criteria count toward a run's result only while the judge agrees with a person's labels; otherwise they are shown only. Label pairs is where a person picks A better, Tie or B better; "the person" is whoever labelled. A finished run nothing counted on has No result | LLM-as-judge, grader, score (for a judged criterion), calibration, ground truth, win rate, blind test |

## Never on screen

- A vendor or infrastructure name the user did not choose: Nylas, Bright Data, ContactOut, Apollo,
  Vertex, Cloud Run. Name the provider the user connects (Google, Microsoft, Zoom, Recall) and nothing
  behind it.
- "This deployment", "configured", "enabled on the server". Say who can turn it on.
- Internal words: triage, enrichment, seat, grant.

`apps/web/src/lib/copyGuard.test.ts` fails when "Nylas", "this deployment" or "triage" reaches on-screen
copy; `lib/errorCodes.coverage.test.ts` holds the error messages to the same list. A mockup-backed
exception is listed in the guard by its full sentence.

## Known, left as the mockups draw them

- "mandate" in Team & access ("A mandate always keeps at least one lead"), the Strategy screens and some
  success toasts — `Position.dc.html`, `Strategy.dc.html`.
- "triage" in the Positions empty state — `Workspace.dc.html`.
- "Project type" in the New position modal, "scoped universe", "cos · cand".
