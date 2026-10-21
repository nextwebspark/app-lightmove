# Glossary — the words on screen

The code, routes, API and tables keep their own names (`project`, `client`, `triage`, `lightmove`).
This page is about what a user reads.

**The rule: a mockup's wording wins.** `claude-design/*.dc.html` is the source of truth for every
screen. Where a mockup still says a word this page would avoid, the screen says it too until the mockup
changes. Copy no mockup draws — toasts, errors, banners, empty states — follows this page.

## Words we use

| Say | For | Not |
|---|---|---|
| **Uncava** | the product | LightMove |
| **Position** | a project: one open role, end to end | project (mandate where a mockup says it) |
| **Business unit** / **Client** | who a position is for — in-house / agency | a literal either way: `useWorkspaceVocabulary().unit` |
| **Hiring manager** / **Client contact** | the people there who read the work | a literal either way: `useWorkspaceVocabulary().contact` |
| **Executive** | a person mapped at a company on a position | — |
| **Candidate** | the same person on the workspace's Candidates page | — |
| **Universe** | the companies a position has taken from the market (In universe / Shortlisted / Declined) | triage |
| **Archive** / **Restore** | putting a position or a business unit away, hidden from every list and picker, and bringing it back; nothing is deleted | delete, close |
| **Uncava support** | who switches on a feature a workspace cannot | "this deployment" |
| **12d** | a trial's days left, where a phone has room for nothing longer (the topbar chip; its spoken name is "Trial · 12 days left") | — |

## Never on screen

- A vendor or infrastructure name the user did not choose: Nylas, Bright Data, ContactOut, Apollo,
  Vertex, Cloud Run. Name the provider the user connects (Google, Microsoft, Zoom, Recall) and nothing
  behind it.
- "This deployment", "configured", "enabled on the server". Say who can turn it on.
- Internal words: triage, enrichment, seat, grant.

`apps/web/src/lib/copyGuard.test.ts` fails when "Nylas", "this deployment" or "triage" reaches on-screen
copy (the list is `copy/neverOnScreen.ts`, which the catalogue test and the server's screen text check read too); `lib/errorCodes.coverage.test.ts` holds the error messages to the same list. A mockup-backed
exception is listed in the guard by its full sentence.

## How we write messages

What Settings → Platform → Screen text shows a super admin beside every sentence, and what any new copy follows:

- Say what happened, then what the person can do next.
- Say why only when we know it. Never guess a cause.
- Offer "Try again" only when trying again can work.
- Never blame the person, and never name a vendor or an internal word (the list above).
- Keep every `{placeholder}`: the screen fills it in. The server refuses an edit that drops or adds one.

A super admin's edit reaches every workspace; an edit nobody publishes changes nothing. The defaults live in
`apps/web/src/copy/catalog/`, one file per screen group, and `catalogFile.test.ts` keeps the server's copy
(`apps/api/src/main/resources/screen-text/catalog.json`) in step.

## Known, left as the mockups draw them

- "mandate" in Team & access ("A mandate always keeps at least one lead"), the Strategy screens and some
  success toasts — `Position.dc.html`, `Strategy.dc.html`.
- "triage" in the Positions empty state — `Workspace.dc.html`.
- "Project type" in the New position modal, "scoped universe", "cos · cand".
