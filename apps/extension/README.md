# LightMove Capture

A Chrome extension that reads whatever the page you are looking at is about — a company or a person —
and writes it into a mandate. A company lands in its triage as **in universe** or **shortlisted**; a
person lands in its people, mapped to one of its triaged companies where the mandate already holds
their employer under that name.

Its own workspace: it imports nothing from `apps/web` and shares no build with it. What it does share
is the API, and **everything it writes with already existed there**, behind the same `WORK_EXECUTE`
gate the mandate's own screens go through:

| What | Endpoint | The manual surface it mirrors |
|---|---|---|
| The dropdown | `GET /projects` | — |
| A company | `POST /projects/{id}/triage/capture` | the Companies screen's Add-company panel |
| A person | `POST /projects/{id}/candidates` | the Add-executive drawer |
| Undo | `DELETE` on either row | the Companies grid's own remove |

A capture is just `source: "extension"` on paths that also take a row typed in by hand, and the popup
sends a strict *subset* of the fields those forms send. **A field it captures must already be a field
the manual form captures** — adding one is a change to both, and a story of its own.

This extension adds **no triage code and no candidate code**. The only thing it needed that did not
exist is a session of its own, which is the pairing flow below.

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

Pairing is a **one-time click, never a login**: on `/extension/connect` you are already signed into the
web app, so nothing asks for a password — one button mints the token. After it lands the connect tab
closes itself and the panel, which watches `chrome.storage` for the session, flips straight to the
capture form. From then on the panel opens already-signed-in without any step (the stored profile is
read with no network call), until the token is actually revoked or expires. Being asked to connect
*repeatedly* is not the flow — it means the paired token was invalidated, most often by a local database
reset dropping `app_lm_refresh_token`; re-pair once and it sticks.

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

## What a capture reads, and why so little

**A name and a URL — deliberately nothing more.** The signed-in 2025 LinkedIn layout has no `h1`,
hashes class names per deploy, serves no JSON-LD, and lazy-mounts everything else — every attempt to
read richer fields off it (career, title, employer, Voyager JSON) proved flaky and was removed. This
is also how the mature tools work: the extension captures who/where, and enrichment happens
server-side later.

On `linkedin.com/in/<slug>` and `/company/<slug>` the captured URL is **built from the address bar's
slug** — never read off the page — so it is present even when the page yields nothing. The name comes
from an injection of the page reader: the `h1` chain where a layout still has one, falling back to
the tab title (`"(3) Name - Headline - Employer | LinkedIn"`), which every layout carries. No
scrolling, and no requests of our own against LinkedIn, ever.

**The read waits for the page to catch up with its address, and that is the whole staleness story.**
LinkedIn navigates with `pushState`, so `tab.url` and `document.location` flip to the new profile at
the same instant while the DOM and the tab title still describe the previous one — which is why
comparing one address to another can never catch it, and why the panel used to offer the person the
consultant had just left. `pageSettleEvidence.ts` asks the other question instead: does anything about
the *content* say it belongs elsewhere — a `canonical` naming another slug, or the same name that was
read at a different page. While it does, `activePage.ts` re-injects on a 150ms grid for up to ~3s.
Past that the name comes back empty rather than wrong, and the re-read that follows LinkedIn's title
change fills it in.

The last confident read is kept per tab in `chrome.storage.session`, because that is what makes "the
same name at a different page" recognisable at all — the signed-in layout declares no `canonical`, so
on the layout consultants actually use it is the only evidence there is.

**The plugin reads LinkedIn only.** On any other site the panel says so and offers an
"Open LightMove" button to the selected mandate's Companies page (the projects list when none is
selected) — manual adds live in the app. A LinkedIn page that names nobody (the feed, search, jobs)
asks for a profile or company page instead.

## Adding a page extractor

Extractors live in `src/content/pageReader/extractors/` and are pure functions:

```ts
(document: Document) => Partial<ExtractedCompany>   // or Partial<ExtractedPerson>
```

No `chrome.*`, no network — which is what makes them testable against a saved HTML fixture with no
browser. Write the extractor, save a fixture beside it in `__fixtures__/`, add it to the merge in
`readPageSubject.ts` **at the end** (the merge takes the first non-empty value per field, so
appending can only fill gaps and never break a page that already worked), and test it:

```bash
npx vitest
```

An extractor that stops matching should return nothing rather than throw — a field it had nothing to
say about comes back empty, and **an empty field is the one the consultant can type in**.

## What is typed, and what is only shown

The detected name is **locked** whenever the page supplied it: what LinkedIn calls a person or a
company is the record, and retyping it by hand is how one executive ends up in two mandates under two
spellings. It renders as `readOnly` rather than `disabled`, so the value stays focusable and copyable
and the label still names it for a screen reader.

It falls back to a real editable input when the read came back **empty**, and that is load-bearing
rather than a nicety: `canSave` gates on the name, so a page the extractor missed — or a read the
panel gave up on past its settle deadline — would otherwise be a row nobody could file. A name typed
into a blank field stays the consultant's: a read landing late fills a blank, never an edit.

The note is the one field that is always written. And a name locked in wrong (the title parser splits
on dashes, so "Amira Haddad - MBA" can lose its suffix) is still editable afterwards in the web app's
`CandidateDrawer` — the panel is not a one-way door.

## Permissions, and why each one is here

`storage` — the paired session and the last-used mandate. Nothing else is stored.
`scripting` — injects the page reader into the LinkedIn tab the panel is looking at.
`tabs` — the side panel outlives the toolbar gesture and follows the active tab, so it needs to know
*which* page a tab is on (its URL, never its content). It is also what drives `panelAvailability.ts`.

**The panel is hidden and the toolbar icon greyed on every tab that is not LinkedIn** — Chrome's own
documented recipe for a site-specific panel, per-tab `sidePanel.setOptions({ tabId, enabled })` driven
from `tabs.onUpdated`. Chrome *hides* rather than closes, and brings the panel back by itself on
returning to a tab where it was open, so the panel still follows the consultant profile to profile.
**The manifest deliberately has no `side_panel` key**, and that is the part that actually makes the
scoping work. A `default_path` there creates a *global* panel which shows on every tab and is **not**
overridden by per-tab `setOptions({ tabId, enabled: false })` — the icon greys out and the panel sits
there anyway. So the path is named by the worker on every per-tab enable instead
(`SIDE_PANEL_PATH` in `buildTargets.ts`), and the global default is explicitly turned off at startup.
Leaving the path off an enable is the mirror trap: the tab comes back enabled and pointing at nothing,
so the icon lights up on LinkedIn and clicking it opens no panel. Both of those shipped once.

Three more parts are easy to leave out and all are load-bearing: a **sweep of already-open tabs** at
worker startup and on `onInstalled`, because `onUpdated` only fires on a change; **`tabs.onActivated`**,
because a tab the worker has never judged now has no panel path *and* no global default to fall back
on, so its toolbar click would open nothing; and **`chrome.action.disable(tabId)`**,
because with `openPanelOnActionClick` a disabled panel makes the toolbar click do nothing *silently*,
which reads as a broken extension rather than one that is not for this page. A tab with no URL yet is
unavailable, not skipped. This adds no permission and guards nothing — `host_permissions` already made
every other site unreadable; it is what the toolbar says about that.
`sidePanel` — the capture surface is a side panel, not a popup, so it stays open while you read.
`host_permissions` — the workspace origin, plus `*://*.linkedin.com/*`: the one site the plugin
reads, standing so the panel keeps working as you move between profiles without a grant prompt per
tab. Every other site gets the LinkedIn-only message — no `activeTab`, no optional hosts, no
`content_scripts`, and nothing close to an `<all_urls>` licence.

## The pinned key

`manifest.config.ts` pins a public key so the extension's id — and therefore its
`chrome-extension://…` origin — is the same on every build. The API allow-lists that origin for CORS
(`lightmove.web.cors-allowed-origins`), and without the pin Chrome would mint a new id on every
unpacked load and every request would be refused.

The matching **private** key is not in this repo and is not needed: it signs a self-hosted `.crx`, and
neither loading unpacked nor publishing through the Web Store — which does its own signing — requires
it. If self-hosted distribution is ever wanted, a new keypair is generated then and the CORS entry
updated to match.
