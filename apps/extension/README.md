# LightMove Capture

A Chrome extension that reads the company on the page you are looking at and writes it into a
mandate's triage — as **in universe** or **shortlisted**.

Its own workspace: it imports nothing from `apps/web` and shares no build with it. What it does share
is the API, and everything it needs already existed there — `GET /projects` for the dropdown,
`POST /projects/{id}/triage/captures` for the write, and the same `WORK_EXECUTE` gate the Strategy
screen goes through.

Design source of truth: `claude-design/Extension.dc.html` and `Extension.handoff.md`.
Coding standard: `.claude/skills/chrome-extension/SKILL.md` — read it before changing anything here.

## Running it

```bash
npm run dev            # at the repo root: database, API and the web app
cd apps/extension && npm run build
```

Then load it: `chrome://extensions` → Developer mode on → **Load unpacked** → pick
`apps/extension/dist`. Open it with the toolbar icon or `⌥⇧L`.

`npm run build` builds against `http://localhost:5173` (the Vite dev server, which proxies `/api` to
the API). A production build against `https://app.lightmove.io` is `npx vite build --mode production`,
which is what CI does — the origin is baked into `host_permissions` and cannot be changed at runtime.

To capture a company that resolves against the Apollo universe you need the universe locally:
`npm run dev:db:apollo` once, from the repo root.

## Pairing

The extension does not use the web app's session cookie, and deliberately: that cookie is
`SameSite=Strict`, host-only and scoped to `/api/v1/auth`, and letting another origin present it would
mean removing every attribute that protects it. Instead:

1. The popup's **Open LightMove** button opens `<workspace>/extension/connect`.
2. That page — where you are already signed in — asks the API for a refresh token of its own.
3. It posts the token to `src/content/pairingBridge.ts`, which hands it to the service worker.

The result is an ordinary refresh-token family with a shorter TTL, listed in **Settings → Active
sessions** as *LightMove Capture* and revocable from there. Signing out of the extension leaves the
browser session alone, and vice versa.

## Layout

Grouped by which of Manifest V3's three execution contexts a file runs in, because that is the first
thing you need to know about any file here.

| Path | Runs as | Holds |
|---|---|---|
| `src/background/` | the service worker | the session, every network call, the message handlers |
| `src/content/` | injected into a page | the pairing bridge and the page reader — never a credential |
| `src/popup/` | a document destroyed on close | React; asks the worker for everything |
| `src/api/` | (imported by the worker) | the only code that knows the API exists |
| `src/domain/` | anywhere | domain normalisation, the two destinations |

## Adding a page extractor

Extractors live in `src/content/pageReader/extractors/` and are pure functions:

```ts
(document: Document) => Partial<ExtractedCompany>
```

No `chrome.*`, no network — which is what makes them testable against a saved HTML fixture with no
browser. Write the extractor, save a fixture beside it in `__fixtures__/`, add it to the list in
`readCompanyFromPage.ts` **at the end** (the merge takes the first non-empty value per field, so
appending can only fill gaps and never break a page that already worked), and test it:

```bash
npx vitest
```

An extractor that stops matching should return nothing rather than throw — the capture still works
with fewer fields, and every field is editable in the popup anyway.

## Permissions, and why each one is here

`storage` — the paired session and the last-used mandate.
`activeTab` + `scripting` — reads the page you invoked the extension on, and only that one. There is
deliberately **no `<all_urls>` content script**: Chrome grants `activeTab` when you click the toolbar
icon and revokes it when you navigate away, so the extension can read no page you have not pointed it
at. `host_permissions` covers the workspace origin alone.

## The pinned key

`manifest.config.ts` pins a public key so the extension's id — and therefore its
`chrome-extension://…` origin — is the same on every build. The API allow-lists that origin for CORS
(`lightmove.web.cors-allowed-origins`), and without the pin Chrome would mint a new id on every
unpacked load and every request would be refused.

The matching **private** key is not in this repo and is not needed: it signs a self-hosted `.crx`, and
neither loading unpacked nor publishing through the Web Store — which does its own signing — requires
it. If self-hosted distribution is ever wanted, a new keypair is generated then and the CORS entry
updated to match.
