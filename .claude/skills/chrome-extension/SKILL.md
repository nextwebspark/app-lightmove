---
name: chrome-extension
description: LightMove Capture — the Chrome extension in apps/extension. Manifest V3 architecture, the three execution contexts and what may live in each, how the extension authenticates without touching the SPA's session cookie, how page extractors are written and tested, and the traps this build already fell into. Load before any work under apps/extension, on the extension's endpoints under /api/v1/auth/extension, or on the SPA's /extension/connect route.
---

# LightMove Capture — the Chrome extension

`apps/extension` is a Manifest V3 browser action: a 400×600 popup that reads the company on the page a
consultant is standing on and writes it into a mandate's triage as **in universe** or **shortlisted**.
`claude-design/Extension.dc.html` and `Extension.handoff.md` are the design source of truth — read the
relevant state before building a screen, the same rule the web app follows.

It is a **client of the existing API**, not a second backend. It adds no table, no RBAC action, and no
stage: it calls `GET /api/v1/projects` for its dropdown and
`POST /api/v1/projects/{id}/triage/captures` for its write, and every authorisation decision is the one
`@projectAuthorizer` was already making. Before adding an endpoint for the extension, check that the
web app does not already have one that answers the question.

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

- `activeTab` + `chrome.scripting.executeScript` on a user gesture — **never** a standing `<all_urls>`
  content script. Clicking the toolbar icon or pressing the shortcut is what grants access to that one
  tab, so the extension can read no page the consultant has not pointed it at.
- `host_permissions` covers the app origin only.
- Every entry in the manifest carries a comment naming the feature that needs it. An unexplained
  permission is a review failure — it is also what gets an extension rejected from the store.
- **No remote code.** Everything is bundled and the CSP stays at the MV3 default. This is store policy,
  not taste.

## Page extractors

`content/pageReader/readCompanyFromPage.ts` merges every extractor, best field wins, and hands the result
to the popup where **every field is an editable input** — nothing is written blind, and "Re-scan" re-runs
the whole thing.

Each extractor is a **pure function over a `Document`**:

```ts
(document: Document) => Partial<ExtractedCompany>
```

That signature is the whole testing strategy: an extractor touches no `chrome.*` API and no network, so
it runs against a saved HTML fixture under jsdom with no browser at all. An extractor that reaches for
`chrome.tabs` cannot be tested and must be refactored.

- `structuredDataExtractor.ts` is the universal fallback — JSON-LD `Organization`, OpenGraph, `<meta>`,
  the canonical host. It is the one that works on the GCC long tail, so keep it first-class.
- `linkedInCompanyExtractor.ts` reads `linkedin.com/company/*`. LinkedIn's class names are generated and
  churn: every selector needs a documented fallback chain, and a fixture pinning what it was written
  against. When it breaks, it breaks quietly — the merge just yields fewer fields.
- Extracted text is **data, never markup**. No `innerHTML`, no `eval`, no injecting page-supplied strings
  into the popup as HTML.

## Conventions

React inside the popup follows the **`react` skill** unchanged — `function` components, `handle*`
handlers, `on*` props, `is`/`has` booleans, PascalCase component files, lowercase directories, named
exports for sub-components, `cn()` for conditional classes, union literals rather than `enum`, and
switching on an RFC 9457 `code` rather than `detail`. Server reads go through TanStack React Query, the
same idiom as `apps/web`, seeded from `chrome.storage.local` so the project dropdown does not spin on
every popup open.

Names carry intent, the same rule the backend follows: `readCompanyFromPage`, not `scrape`;
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
