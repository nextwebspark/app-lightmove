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
| **Uncava support** | who switches on a feature a workspace cannot | "this deployment" |
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
