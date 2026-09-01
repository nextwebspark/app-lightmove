---
name: chrome-extension
description: LightMove Capture — the Chrome extension in apps/extension. Manifest V3 architecture, the three execution contexts and what may live in each, how the extension authenticates without touching the SPA's session cookie, how page extractors are written and tested, and the traps this build already fell into. Load before any work under apps/extension, on the extension's endpoints under /api/v1/auth/extension, or on the SPA's /extension/connect route.
---

# LightMove Capture — the Chrome extension

`apps/extension` is a Manifest V3 browser action: a **side panel** (not a popup — it stays open while
the consultant moves between profiles, and follows the active tab) that reads whatever the page a
consultant is standing on is about — a company or a person — and writes it into a mandate. A company
lands in its triage as **in universe** or **shortlisted**; a person lands in its people, mapped to one
of its triaged companies when the mandate already holds their employer under that name.
`claude-design/Extension.dc.html` and `Extension.handoff.md` are the design source of truth — read the
relevant state before building a screen, the same rule the web app follows.

It is a **client of the existing API**, not a second backend. It adds no table, no RBAC action and no
stage — every endpoint it calls already existed, and it sends a strict *subset* of the fields the web
app's own forms send:

| What | Endpoint | The manual surface it mirrors |
|---|---|---|
| The dropdown | `GET /projects` | — |
| A company | `POST /projects/{id}/triage/capture` | `CompanyFactsForm` |
| A person | `POST /projects/{id}/candidates` | `CandidateDrawer` |
| Undo | `DELETE` on either row | the Companies grid's own remove |

**A field the popup captures must already be a field the manual form captures.** Adding one is a change
to both surfaces and a story of its own — never something the extension grows alone, or the two ways
into the same table stop agreeing about what a company is.

Every authorisation decision is the one `@projectAuthorizer` was already making.

**Before adding anything server-side for the extension, check whether it is already there.** It very
likely is — a capture is `source: "extension"` on an endpoint that also serves rows typed in by hand,
and the provenance is the only difference. The one thing the extension genuinely needed and did not
have is its own session; that is the pairing flow below.

## Three contexts, and what may live in each

MV3 splits an extension across three JavaScript worlds that share no memory. `src/` is grouped by that
split rather than by file type, so a file's folder says which world it runs in — the first thing anyone
reading it needs to know.

| Folder | Runs as | May hold | Must never hold |
|---|---|---|---|
| `src/background/` | the service worker | the session token, network calls, cross-context messaging | anything assumed to survive between events |
| `src/content/` | injected into a page, sharing its origin | DOM reading, the pairing bridge | the session token, or trust in anything the page says |
| `src/popup/` | a document that dies on close | React, user state for one capture | the session token, long-lived caches |

**The service worker is not persistent.** Chrome kills it between events and restarts it on the next
one, so in-memory state does not survive and every listener must be registered at the **top level** of
the module. A listener registered inside a callback is registered *after* the event that woke the worker
has already been dispatched, and the message is silently lost. Anything that must outlive an event goes
to `chrome.storage`.

**The token lives in the service worker's `chrome.storage.local`, and nowhere else.** A content script
shares an origin with a page that may be hostile; the popup is destroyed on every close. The popup asks
the worker for data over `chrome.runtime.sendMessage` and never holds the credential itself.

## Authentication: pairing, never the SPA's cookie

The handoff describes the popup "reusing the `app.lightmove.io` session cookie". **Do not implement that.**
The refresh cookie is `httpOnly` + `Secure` + `SameSite=Strict`, host-only, and scoped to `/api/v1/auth`;
`lightmove-domain` explains that the SPA and API are one origin precisely so it can stay that way. An
extension is a different origin, and making the cookie reachable from one means weakening every attribute
that protects it.

Instead the extension is **paired**, which looks identical to the user (the popup's button opens the app
in a tab; there is still no in-popup login):

1. The popup opens `<app>/extension/connect`.
2. That SPA route, already signed in, calls `POST /api/v1/auth/extension/tokens` with its in-memory
   access token. The server mints a refresh token through the **existing** `app_lm_refresh_token` family
   machinery — same rotation, same reuse detection, same revocation — and returns it in the response body.
3. The page hands it to this extension with `chrome.runtime.sendMessage(EXTENSION_ID, …)`, permitted by
   the manifest's `externally_connectable` and accepted by the worker only from the workspace origin.
4. The worker exchanges it at `POST /api/v1/auth/extension/refresh` for access tokens from then on.

**Address the handover, never broadcast it.** `window.postMessage` to the page's own window is
delivered to every listener in that frame, and a content script's isolated world does *not* isolate it
from those events — so any other extension the consultant has installed with a broad content script
reads the refresh token straight off the page. This build shipped that mistake in review and it was
caught there. The extension id is not a secret: the manifest pins it and `application.yml` already
names it in the CORS allow-list.

Consequences worth keeping in mind:

- The extension session is a real row in the refresh-token table, so it appears in Settings → Active
  sessions and the user can revoke it from the web. That is the point.
- `/auth/extension/refresh` carries its credential in the **body**, not a cookie, so CSRF is structurally
  impossible and it is correctly listed in `SecurityConfig`'s CSRF exemptions. Do not "fix" that by
  adding a CSRF token — there is no cookie for a cross-site page to cause the browser to attach.
- Rotation applies. If a rotation response is lost the stored token is stale, the next exchange looks
  like replay, and the family is revoked — the user re-pairs. Same trade the SPA makes; don't disable
  rotation to avoid it.
- A token may only be redeemed by the client its family was opened for — a web session's refresh token
  is refused at `/auth/extension/refresh`, so a cookie-only credential cannot be laundered into a
  body-carried one.

## Permissions: least privilege, checked at review

- `host_permissions` covers the app origin and `*://*.linkedin.com/*` — the one site the plugin
  reads, standing so the panel follows profile-to-profile moves with no grant prompt. **Nothing
  else**: no `activeTab`, no `optional_host_permissions`, no content scripts, never `<all_urls>`.
  A non-LinkedIn page gets the `LINKEDIN_ONLY` message and an "Open LightMove" button, not a read.
- Every entry in the manifest carries a comment naming the feature that needs it. An unexplained
  permission is a review failure — it is also what gets an extension rejected from the store.
- **No remote code.** Everything is bundled and the CSP stays at the MV3 default. This is store policy,
  not taste.

## What a capture reads: a name and a URL, deliberately nothing more

V1 captures **`fullName` + `linkedinUrl`** for a person and **`companyName` + `linkedinUrl`** for a
company — the URLs sent silently with the save, never shown in the panel. The
reason is the whole story: the signed-in 2025 LinkedIn layout renders no `h1`, hashes every class
name per deploy, serves no OpenGraph or JSON-LD, and lazy-mounts Experience on scroll — every
attempt to read richer fields (career, title, employer, and LinkedIn's Voyager JSON API) shipped
flaky and was **removed**. This matches the mature tools (Clockwork, Lusha): capture who/where in
the extension, enrich server-side later. Do not re-grow page-side field extraction; a new captured
field is an enrichment story, not an extractor.

How the two fields are read (`serviceWorker.ts:readActivePage`, one injection, no scrolling):

- On `linkedin.com/in/<slug>` and `/company/<slug>` the URL decides the subject and the captured
  `linkedinUrl` is **built from the address-bar slug** (`content/pageReader/linkedInUrls.ts`) —
  never read off the page, so it survives a DOM that yields nothing.
- The name comes from one pass of the injected page reader: the `h1` fallback chain where a layout
  still renders one, else the tab title — `"(3) Name - Headline - Employer | LinkedIn"` — which
  every layout so far carries. A missing name is not an error: the read returns the URL with an
  empty name and the consultant types it (`canSave` gates on the name anyway).
- The extension makes **no request of its own against LinkedIn**, ever — no Voyager calls, no
  interception, no declared `content_scripts`. That restraint is what keeps a real account safe.
- Off LinkedIn there is no read at all: the worker answers `LINKEDIN_ONLY` and the panel shows the
  message with an "Open LightMove" button (the selected mandate's Companies page, else the projects
  list). A LinkedIn page that names nobody — feed, search, jobs — answers `PAGE_NOT_READABLE`.

## Page extractors

One bundle is injected, and its entry is `readPageSubject.ts` — it runs both LinkedIn extractors and
classifies by URL alone (`/in/` → person, `/company/` → company, else unknown). The popup renders
what it reads as **editable inputs** — nothing is written blind, and "Re-scan" re-runs the whole
thing.

Each extractor is a **pure function over a `Document`**:

```ts
(document: Document) => Partial<ExtractedCompany>   // or Partial<ExtractedPerson>
```

That signature is the whole testing strategy: an extractor touches no `chrome.*` API and no network, so
it runs against a saved HTML fixture under jsdom with no browser at all. An extractor that reaches for
`chrome.tabs` cannot be tested and must be refactored.

- `linkedInCompanyExtractor.ts` and `linkedInProfileExtractor.ts` read `linkedin.com/company/*` and
  `/in/*`, keyed on `document.location` and never on `canonical` — a page-supplied URL would let any
  site declare itself a LinkedIn page. Each reads the name only: an `h1` chain ending at the tab
  title, with a fixture per live layout (the hashed-layout fixture pins the title-tag fallback).
  When it breaks, it breaks quietly — an empty, editable name.
- An extractor keeps its own merge written out field by field, so adding a field fails the build
  until it is merged too.
- Extracted text is **data, never markup**. No `innerHTML`, no `eval`, no injecting page-supplied strings
  into the popup as HTML.

## Conventions

React inside the popup follows the **`react` skill** unchanged — `function` components, `handle*`
handlers, `on*` props, `is`/`has` booleans, PascalCase component files, lowercase directories, named
exports for sub-components, `cn()` for conditional classes, union literals rather than `enum`, and
switching on an RFC 9457 `code` rather than `detail`. Server reads go through TanStack React Query, the
same idiom as `apps/web`, seeded from `chrome.storage.local` so the project dropdown does not spin on
every popup open.

Names carry intent, the same rule the backend follows: `readPageSubject`, not `scrape`;
`extensionSessionStore`, not `store`; `DetectedFieldInput`, not `Field`. Every type name reads standalone.

`apps/extension` imports nothing from `apps/web` and vice versa — two apps, two runtimes, no shared
build. The design tokens in `styles/theme.css` and the `ApiError` shape are the only text that exists in
both; that is deliberate, and a shared package for a few dozen lines would couple the two for less than
it costs.

The manifest is generated from `manifest.config.ts` so its paths and the bundle cannot drift. Never
hand-edit a built `manifest.json`.

## Commands

```bash
cd apps/extension && npm run dev        # watch build against localhost:5173 — the one to use locally
cd apps/extension && npm run build      # production build; needs LM_WORKSPACE_ORIGIN to be useful
cd apps/extension && npm run build:release  # the zip to upload; refuses to run without an origin
cd apps/extension && npx vitest         # extractors against saved fixtures
cd apps/extension && npx tsc --noEmit   # typecheck
```

To try it end to end: `npm run dev` at the repo root, `npm run dev:db:apollo` once so the company
universe exists, `npm run dev` in `apps/extension`, load `apps/extension/dist` unpacked at
`chrome://extensions`, then visit `/extension/connect` to pair.

## The origin and the id are configuration, not literals

**The workspace origin is fixed at build time** by `LM_WORKSPACE_ORIGIN`, because the manifest asks
Chrome for permission on that exact host and a permission cannot be computed at runtime.
`vite.config.ts` resolves it once and writes the same value into both the manifest and the bundle — do
not add a second place that decides it. A production build with no origin falls back to a deliberately
fake placeholder and warns; `build:release` refuses outright.

**The extension id differs between development and the store.** The pinned manifest key fixes it for
unpacked loading only; the Web Store assigns its own when the item is created. Two places take it as
configuration and both default to the development id: `EXTENSION_ID` for the API's CORS allow-list
(deploy) and `VITE_EXTENSION_ID` for the pairing page (`apps/web` build). Get either wrong after
publishing and the extension is refused with nothing useful in the response. Never re-hardcode either.

**The allow-listed origin is a browser convenience, not a gate.** The development key is committed, so
anyone can build an extension carrying it and speak from that origin — and the entry applies to the
whole `/api/v1` surface, not just the extension's own routes, because CORS is configured once for the
API. Nothing may be authorised by the caller having reached us: every route still demands the bearer
token and re-reads the roles. Never move a decision onto the origin.
