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
| **Out of process** (the board's band) | the band under the Pipeline board's columns: everyone at a status out of process, under the stage their way out sits in | Inactive, archived, declined (for people) |
| **Conversation** | everything with one executive on one position on the Outreach page: emails both ways, run events, notes, meetings | thread, inbox |
| **To do** | the Outreach page's filter of the people waiting on someone; its groups are Needs reply, Follow-ups, Before first email and Delivery problems | Action queue, Needs action, inbox |
| **Working hours** | the days and hours a consultant works, which a booking link offers times inside (Outlook's own, theirs, or the workspace's) | availability window, office hours |
| **Time zone** / **Date format** / **Time format** | Settings → General's Date & time (the workspace's) and Settings → Profile's (a person's own) | timezone setting, locale, region |
| **Automatic** | a person's time zone following their device, the first option of Profile's Timezone picker | auto-detect, device default |
| **Default time zone** | the workspace's zone on General: a member's until their device is known, and workspace emails' | workspace time zone, company zone |
| **Workspace default** | a person's date or time format left to the workspace, the first option of those Profile pickers | inherit, follow, auto |
| **Reset to standard** | putting the country's zone and the standard formats (04 Dec 2026, 24-hour) back on General | defaults, restore |
| **Email signature** | what closes an email a consultant sends from Uncava, one of their own kept in Settings → Email signature, the default ticked in the composer | footer, sign-off |
| **Email template** | a saved email or reply a consultant applies in the composer, kept in Settings → Email templates, personal or shared with the workspace — never Settings → Templates, which is the position brief library | snippet, canned reply, saved reply, role template |
| **Variable** | a `{{firstName}}` in a sequence step or an email template, filled in for each executive when the email is written, inserted from the same Insert bar in both | token, merge field, placeholder |
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
| **Export personal data** | an admin's one zip of everything held on a person, to answer their request for a copy of it (subject access), from the Candidates drawer's ⋯ | DSAR, SAR, subject access request export |
| **Two-factor authentication** | a code from an authenticator app asked for after the password, turned on in Settings → Security | MFA, 2FA, TOTP, OTP |
| **Authenticator app** | the phone app that shows the six-digit code | token, device |
| **Setup key** | the secret typed into an authenticator app instead of scanning the QR code | seed, secret |
| **Recovery code** | one of ten single-use codes that sign in when the phone is lost | backup code, scratch code |
| **12d** | a trial's days left, where a phone has room for nothing longer (the topbar chip; its spoken name is "Trial · 12 days left") | — |

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
