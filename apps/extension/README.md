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
npm run dev                              # at the repo root: database, API and the web app
cd apps/extension && npm run dev         # watch build against http://localhost:5173
```

Then load it: `chrome://extensions` → Developer mode on → **Load unpacked** → pick
`apps/extension/dist`. Open it with the toolbar icon or `⌥⇧L`.

**`npm run dev` is the one to use locally.** It targets the Vite dev server, which proxies `/api` to
the API exactly as the web app does. `npm run build` is a production build and targets whatever
`LM_WORKSPACE_ORIGIN` names — nothing, by default, so it falls back to a placeholder host and says so
loudly. That build typechecks and bundles (which is all CI wants of it) but cannot talk to anything.

Both build **every** part — popup, service worker, and the separately-bundled page reader. Running
`vite build` by hand does not, and leaves a manifest pointing at a file that is not there.

### Where a build points

The workspace origin is fixed at **build time** and cannot be changed afterwards: the manifest asks
Chrome for permission on that exact host, and a permission cannot be computed from something a user
types later. One environment variable decides it, and `vite.config.ts` writes the same value into both
the manifest and the bundle so they cannot disagree.

| `LM_WORKSPACE_ORIGIN` | Mode | Result |
|---|---|---|
| unset | `dev` | `http://localhost:5173` |
| unset | production | a placeholder, with a warning — **not shippable** |
| set | either | exactly what you set |

There is no deployed LightMove domain yet, and the placeholder is deliberately not a real one. Until
there is a domain, the Cloud Run URL the deploy prints is a perfectly good origin to build against:

```bash
LM_WORKSPACE_ORIGIN=https://lightmove-api-xxxx.run.app npm run build
```

To capture a company that resolves against the Apollo universe you need the universe locally:
`npm run dev:db:apollo` once, from the repo root.

## Pairing

The extension does not use the web app's session cookie, and deliberately: that cookie is
`SameSite=Strict`, host-only and scoped to `/api/v1/auth`, and letting another origin present it would
mean removing every attribute that protects it. Instead:

1. The popup's **Open LightMove** button opens `<workspace>/extension/connect`.
2. That page — where you are already signed in — asks the API for a refresh token of its own.
3. It hands the token to this extension with `chrome.runtime.sendMessage(EXTENSION_ID, …)`, which the
   manifest permits through `externally_connectable` and the service worker accepts only from the
   workspace origin.

Step 3 is addressed, not broadcast, and that matters: `window.postMessage` would deliver the token to
*every* listener in the frame — including content scripts belonging to any other extension the
consultant has installed.

The result is an ordinary refresh-token family with a shorter TTL, listed in **Settings → Active
sessions** as *LightMove Capture* and revocable from there. Signing out of the extension leaves the
browser session alone, and vice versa.

## Publishing to the Chrome Web Store

```bash
LM_WORKSPACE_ORIGIN=https://your-workspace-origin npm run build:release
```

That produces `release/lightmove-capture-<version>.zip`, ready to upload. It refuses to run without an
origin, strips `key` from the packaged manifest, drops the source maps, and zips the *contents* of
`dist/` rather than the folder — three things that are easy to get wrong by hand and each of which
fails quietly.

**The published extension has a different id from the one you develop with.** The pinned key in
`manifest.config.ts` fixes the id for unpacked loading; the Web Store assigns its own when the item is
first created, and you cannot choose it. So the id is configuration in two places, and both are wrong
until you have published once:

| Where | How to set it | What breaks if it is wrong |
|---|---|---|
| API CORS allow-list | `EXTENSION_ID` — a repository variable for the deploy workflow, or an env var for `ops/gcp/deploy.sh` | Every request from the extension is refused, with nothing in the response saying why |
| The pairing page | `VITE_EXTENSION_ID` at `apps/web` build time | Pairing reports "extension not detected" forever |

So the order is: publish once → note the assigned id → set both → redeploy. Until then, both default to
the development id, which is right for a locally-loaded extension and right for nothing else.

### The listing

1. A developer account (one-off **$5**) at
   [the Web Store dashboard](https://chrome.google.com/webstore/devconsole).
2. Upload the zip, fill in a description, at least one 1280×800 screenshot, and a privacy policy URL.
3. **Justify the permissions.** `storage`, `activeTab` and `scripting`, and it is worth saying plainly
   that there is no `<all_urls>` content script — the extension reads a page only when the consultant
   invokes it on that tab. Reviews go faster when the narrow scope is stated rather than inferred.
4. Expect the **data-use disclosure** section, since the extension reads page content and handles
   account data.
5. **Consider Private or Unlisted visibility.** If this is for your own consultants rather than the
   public, private distribution to a Google Workspace domain skips public review entirely, which is
   almost certainly what you want first.

One thing worth verifying rather than trusting: whether the store rejects a package that still contains
`key` or merely ignores it. `build:release` strips it either way, because it does nothing there.

## Layout

Grouped by which of Manifest V3's three execution contexts a file runs in, because that is the first
thing you need to know about any file here.

| Path | Runs as | Holds |
|---|---|---|
| `src/background/` | the service worker | the session, every network call, the message handlers |
| `src/content/` | injected into a page | the page reader — never a credential |
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
