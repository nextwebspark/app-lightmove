# GDPR: every screen change so far (epic #917)

What the GDPR work has changed on screen, and how to reach each change. Back-end-only work (log masking, audit ids,
the residency ADR, the accountability documents) is listed at the end only to say it has no screen. State as of
2026-11-15, `main` at #1343.

Each row names the click path from the app's sidebar, who sees it, whether `DATA_PROTECTION_ENABLED` gates it, and
the PR that shipped it. The feature docs beside this file hold the rules behind each screen.

## Is it on?

| Gate | Effect on screen |
|---|---|
| `DATA_PROTECTION_ENABLED` (#1260) **off in production** | Off, the Candidates drawer has no ⋯ menu, Data & retention shows only *AI on people*, the privacy Show choices are hidden, the outreach footer is not added and the `/privacy/:token` page has nothing to open. |
| Candidate privacy mode = *Notices and consent requests* | Adds every consent control (#1267 phases 2–3). Can only be chosen once the firm's name, privacy contact and notice link are saved. |
| `LEGAL_DPA_VERSION` + `LEGAL_DPA_URL` | Adds the DPA tick when founding a workspace. Blank, no tick. |
| Not gated, **live** | AI on people (#1300, in `v0.36.0`), the terms ticks (#928), account deletion and Download my data (#926), workspace deletion (#926), the booking page and extension privacy notes (#929). |

`npm run dev` starts with the flag off, as production does. To see the gated screens locally, start it with
`DATA_PROTECTION_ENABLED=true SUPPRESSION_LIST_ENABLED=true npm run dev` (see [use-cases.md](use-cases.md) §7), sign
in as an admin and fill in Settings → Data & retention → Candidate privacy.

## Navigation map

```
Sign-up and sign-in (no session)
├── /signup ........................... terms tick                                   #928
├── /auth/accept-invite ............... terms tick                                   #928
├── /signup/terms ..................... "One more step" after a first Google/LinkedIn sign-in   #928
├── /signup/workspace ................. DPA tick                                     #928
└── /login ............................ "Your account was deleted." notice           #926

Every signed-in screen
└── "We've updated our terms" sheet, when the terms version changes                  #928

Sidebar → Candidates (/candidates)
├── Filter → Show: Not notified · Asked, no answer · Consent ending in 30 days · Declined or withdrawn   #923 #1267
├── Selection bar: Privacy notice · Ask for consent                                  #923 #1267
└── Click a person → drawer
    ├── ⋯ More: Send privacy notice… · Mark as notified… · Ask for consent… · Record an answer…
    │           Restrict processing… / Lift restriction · Export personal data… · Delete person permanently…
    ├── Header: Processing restricted pill and note · Opted out by email / Do not contact    #920 #924
    └── Contact tab → Privacy: lawful basis · Notified / Not notified · consent rows · share rows   #923 #1267

Sidebar → a position (/projects/:projectId)
├── Companies → any stage → Pipeline view → card: share badge · ⋯ Ask to share with client… · visibility prompt   #1267
├── Companies → any stage → Table / Map → Advanced ▾ → Consent register              #1267
├── Companies → executive drawer: restricted strip · AI buttons follow the AI switches   #920 #922
└── Outreach (/projects/:projectId/outreach)
    ├── Candidate pane: Client visible switch → visibility prompt                    #1267
    ├── Add to sequence: privacy footer preview                                      #924
    └── Reply composer: privacy footer preview                                       #924

Settings (avatar menu → Settings)
├── Your account → Profile ............ Download my data · Delete my account         #926
├── Your account → Notifications ...... "A candidate objects or asks for their data" (admins) · stop-email wording   #923 #924
├── Your account → API keys ........... candidates.demographics:read scope           #922
├── Your account → Workspaces → New ... DPA tick                                     #928
├── Workspace → General ............... Delete workspace (grace period, certificate) #926
└── Workspace → Data & retention (/settings/data)
    ├── AI on people                                                                 #922
    ├── Candidate privacy (+ consent register download)                              #923 #1267
    ├── Candidate retention                                                          #921
    ├── Due for deletion                                                             #921
    ├── Suppression list                                                             #920
    └── Export workspace data                                                        #926

Notification bell .................... privacy requests, opt-outs, consent answers   #923 #924 #1267

Public pages (no session)
├── /privacy/:token ................... the candidate's privacy page and consent answers   #923 #924 #1267
├── /oauth/consent .................... demographics scope listed for AI apps       #922
└── /book/:slug ....................... scheduling-provider privacy line            #929

Chrome extension (LightMove Capture)
├── Capture refusal: suppressed / processing restricted                              #920
└── Settings screen → Privacy                                                        #929
```

## 1. Settings → Data & retention

Route `/settings/data`. Sidebar: Settings → **Workspace** group → **Data & retention**. Admins only (the Workspace
group is hidden from members and clients). With the flag off the page shows only the first card.

| Card | What it does | Flag | PR |
|---|---|---|---|
| **AI on people** | Four switches: Infer gender, Infer nationality, Draft assessments with AI, AI-enrich on capture. Turning gender or nationality off asks first ("Turn off and clear", "This can't be undone.") and clears the AI's unconfirmed values across the workspace. New workspaces start with all four off. | No | #1300 |
| **Candidate privacy** | Firm's legal name, privacy contact email, privacy notice link, default lawful basis. Mode: *Notices* or *Notices and consent requests* (locked until the firm's details are saved). In consent mode: the three purposes (keep on file for future roles, share with a client for a specific role, career and market updates), *Valid for*, and *Ask again N days before consent ends* (0–90). A download of the **consent register** CSV for the whole workspace, audited. | Yes | #1328, #1341, #1343 |
| **Candidate retention** | "Keep candidates untouched for" N months. | Yes | #1186 |
| **Due for deletion** | Table of candidates past the period (Candidate, Last activity, Positions, Owner), select some or all, **Keep** (another period from today) or **Delete selected**. Empty state "Nobody is due for deletion". Nothing is deleted on its own. | Yes | #1186 |
| **Suppression list** | Who may never be added again. Add by LinkedIn profile and/or email (hashed on save) with a reason (*Added by an admin* or *Objected*); remove with a confirmation. | Yes | #1186 (list itself #1178) |
| **Export workspace data** | Start export → "Building your export…" → emailed when ready. | Yes | #1188 |

Copy: the page subtitle switches between "What the AI may work out about the people you hold." (flag off) and the
longer data-protection sentence (flag on).

## 2. Candidates page and the person drawer

Route `/candidates`. Sidebar: **Candidates** (mobile: the Candidates tab). Staff only.

### 2.1 Quick views (toolbar, above the grid)

| Quick view | Shows | Shown when | PR |
|---|---|---|---|
| Not notified | Held more than 30 days and never told | Flag on | #1328 |
| Asked, no answer | An open consent request | Consent mode | #1341 |
| Consent ending in 30 days | A consent that ends within 30 days | Consent mode | #1341 |
| Declined or withdrawn | Refused or withdrawn consent | Consent mode | #1341 |

### 2.2 Selection bar (tick one or more people)

| Action | Opens | Shown when | PR |
|---|---|---|---|
| Privacy notice | `SendPrivacyNoticeDialog` for the selection | Flag on | #1328 |
| Ask for consent | `RequestConsentDialog` in bulk (never the share purpose); after sending it says who was skipped and why | Consent mode | #1341 |
| Add to position | Now refuses suppressed and restricted people with a toast naming why | Always | #1178, #1180 |

### 2.3 The drawer's ⋯ menu

Open a person (click their row) → **⋯** at the top right of the drawer ("More for {name}"). The menu exists only with
the flag on.

| Item | Who | Opens | PR |
|---|---|---|---|
| Send privacy notice… | Staff | `SendPrivacyNoticeDialog` (email preview) | #1328 |
| Mark as notified… | Staff | `MarkNotifiedDialog` (the day they were told another way) | #1328 |
| Ask for consent… | Staff, consent mode | `RequestConsentDialog` for one person, the email previewed word for word; already-given or recently refused purposes are locked; a share purpose names one position the viewer works | #1341, #1343 |
| Record an answer… | Staff, consent mode | `RecordConsentDialog` (consent given outside the product) | #1341 |
| Restrict processing… | Anyone on the team | `RestrictProcessingDialog`: reason, optional "also add to the suppression list" | #1180 |
| Lift restriction | Whoever set it, or an admin | Lifts at once, toast "Restriction lifted" | #1180 |
| Export personal data… | Admin | `SubjectAccessExportDialog` → Download (one zip) | #1288 |
| Delete person permanently… | Admin | `ErasePersonDialog`: preview counts, type the name back, optional suppression | #1179 |

### 2.4 The drawer's own content

| Where | What | PR |
|---|---|---|
| Header | **Processing restricted** pill; a note with reason, who, when, and Lift (or "Only {name} or an admin can lift it.") | #1180 |
| Header | **Opted out by email.** banner while do not contact stands after an email opt-out; **Do not contact** banner otherwise | #1330 |
| Contact tab → **Privacy** block | Lawful basis select (with "(workspace default)"), Notified / Not notified with the date, Mark as notified | #1328 |
| Privacy block, consent mode | A row per purpose with a pill (Not asked, Asked, Given, Renewal asked, Declined, Withdrawn, Expired), the "No consent recorded." warning when the basis is Consent, Ask for consent / Record an answer | #1341 |
| Privacy block, share offered | A share row per position the person was asked about | #1343 |
| Activity timeline | New lines: privacy notice sent / marked, restriction set / lifted, opted out, consent asked / answered / recorded / expired, shown to client without share consent | #1180, #1328, #1330, #1341, #1343 |

## 3. A position

### 3.1 Companies → Pipeline view (share with client)

Route `/projects/:projectId/companies/:stage`, view switch **Pipeline**. Consent mode with the share purpose offered.

| Change | PR |
|---|---|
| Each card carries a badge: **No consent to share** · **Consent asked** · **Consent given** | #1343 |
| Card ⋯ → **Ask to share with client…** → `AskShareConsentDialog` (the email says "a confidential client" unless the client is named; a confidential brief never allows naming) | #1343 |
| Turning a person client-visible without given share consent opens **"No consent to share with {client} yet"**: Ask first · Show anyway · Cancel. Hiding never asks. | #1343 |

### 3.2 Companies → Table / Map → Advanced ▾

**Consent register**: that position's share requests and answers as a CSV, audited. Consent mode. #1343

### 3.3 Executive drawer (open a person from a position)

| Change | PR |
|---|---|
| A strip saying processing is restricted; AI, research and outreach actions answer with a toast | #1180 |
| Assessment → **Assess with AI** hidden when *Draft assessments with AI* is off; the automatic background suggestion stops asking for gender or nationality when that switch is off | #1300 |

### 3.4 Outreach

Route `/projects/:projectId/outreach`. Staff only.

| Change | PR |
|---|---|
| Candidate pane → **Client visible** switch opens the same visibility prompt; its Ask first sends at once in the confidential wording | #1343 |
| **Add to sequence** dialog: the privacy footer drawn read-only under the email ("{firm} holds your professional details, which came from … Our privacy notice · Stop emails from us") | #1330 |
| Reply composer: the same read-only footer preview | #1330 |
| Runs stopped by an opt-out read **Opted out**; restricted people are refused at enrolment | #1180, #1330 |

## 4. Settings, outside Data & retention

| Where | Change | Who | PR |
|---|---|---|---|
| Your account → **Profile** (`/settings/profile`) | **Download my data** card; **Delete my account** card → `DeleteAccountModal` (the only admin of a workspace or only lead of a live position is shown what to hand over first; confirms with password, two-factor code or a fresh provider sign-in) | Everyone | #1190 |
| Workspace → **General** (`/settings/general`) | **Delete workspace** row and modal: Now (members lose access) / {N}d (purged, deletion certificate emailed), an "Export first." link to Data & retention, typed name to confirm | Admin | #1188 |
| Your account → **Notifications** (`/settings/notifications`) | New switch "A candidate objects or asks for their data" (admins only, emailed at once); the bounce switch now reads "An email of mine bounces, or someone stops my emails" | Staff | #1328, #1330 |
| Your account → **API keys** (`/settings/api-keys`) | New scope `candidates.demographics:read` ("Their gender and nationality, where your team recorded them.") | Staff | #1300 |
| Your account → **Workspaces** → New workspace | DPA tick when the deployment names a DPA | Staff | #1331 |

## 5. Sign-up, sign-in and the terms

None of it is behind the flag.

| Route | Change | PR |
|---|---|---|
| `/signup` | An unticked terms box (`LegalConsent`), sent as ticked rather than hard-coded | #1331 |
| `/auth/accept-invite` | The same terms tick for an invitee | #1331 |
| `/signup/terms` | **One more step**: a first Google or LinkedIn sign-in now asks for the tick before the account exists; "This sign-up has expired" after ten minutes | #1331 |
| `/signup/workspace` | DPA tick when founding a workspace (configured deployments only) | #1331 |
| Every signed-in screen | **"We've updated our terms"** sheet when the stored version is outdated, until ticked or signed out (booking and privacy pages excepted). No version bump has shipped yet, so nobody sees it today. | #1331 |
| `/login` | "Your account was deleted." notice after account deletion | #1190 |

## 6. Public pages (no session)

### 6.1 The candidate's privacy page — `/privacy/:token`

Reached from a privacy notice email, a consent request email, or the outreach footer. Flag on.

| State | What it shows | PR |
|---|---|---|
| Privacy notice | "What we hold about you, and your choices": What we hold, Where it came from, Why we may hold it, Your choices — **Don't contact me again**, **Send me a copy of my data**, **Delete my data** | #1328 |
| `?action=opt-out` | "Stop emails from {firm}?" asked once (a mail scanner's fetch opts nobody out), then See all your choices | #1330 |
| Consent request | Yes / No per purpose, nothing preselected; a renewal asks once; an answered or closed request says what was said | #1341 |
| Your consents (manage link) | What is in force, each withdrawn after one confirmation | #1341 |
| Share request | Share with a named or "a confidential client" for one role | #1343 |
| Expired / invalid | "This link has expired" · "This link isn't valid" | #1328 |

### 6.2 Other public pages

| Route | Change | PR |
|---|---|---|
| `/oauth/consent` | An AI app asking for `candidates.demographics:read` lists it as personal data | #1300 |
| `/book/:slug` | "Our scheduling provider handles the times shown and the details you enter. Privacy" under the scheduler | #1338 |

## 7. Notification bell

| Line | Sub-line | PR |
|---|---|---|
| {person} asked for a copy of their data | From their privacy page · answer within one month | #1328 |
| {person} asked for their data to be deleted | From their privacy page · answer within one month | #1328 |
| {person} objected to being contacted | From their privacy page · answer within one month | #1328 |
| {person} opted out of emails | — | #1330 |
| {person} answered your consent request | From their privacy page | #1341 |

## 8. Chrome extension (LightMove Capture)

| Where | Change | PR |
|---|---|---|
| Capture result panel | "This person is on your workspace's suppression list, so they can't be added." | #1178 |
| Capture result panel | "Processing is restricted for this person, so they can't be added to a position until it is lifted in Uncava." | #1180 |
| Settings screen → **Privacy** | What Capture reads and sends, with "How we handle it" | #1338 |

## 9. Emails people receive

Not screens in the app, but what candidates and users see from these features: the privacy notice (#1328), the
outreach footer and one-click unsubscribe on every outreach email (#1330), the consent request and confirmation
(#1341), the share request (#1343), the workspace export ready email and the deletion certificate (#1188).

## 10. GDPR work with no visible change

| PR | What |
|---|---|
| #1177 | The mockups for erase, restrict, retention, offboarding and account deletion (in `uncava-design`) |
| #1185 | The retention schedule and hourly system sweep |
| #1276 | Audit details hold ids; Recent activity resolves names from live rows (looks the same); scorecard drafts cleared on sign-out |
| #1279, #1280 | Masked logs; backups and security-of-processing doc |
| #1327, #1339 | Accountability documents; residency ADR and sub-processor list |
| #1338 (part) | Self-hosted fonts (same look), a full SPA CSP, Mapbox telemetry and the booking widget's reporter off |

## Not built yet

- The `/privacy/requests` page for people a firm does not hold (website #28, needs an artboard).
- A "Hide from client" action after a declined or withdrawn share (governance decision B12).
- Hiding a restricted person from client seats (⚖️, #920).
- The terms version bump that would show the re-acceptance sheet (ships with the counsel-reviewed terms).
