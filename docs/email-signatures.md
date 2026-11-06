# Email signatures — design and build plan

Status: **planned** (decisions taken 2026-11-01; revised on 2026-11-05 after review and #1172's avatars). Nothing
below exists yet; each phase is one PR, and until a phase ships the tables, routes and entities it names do not exist.
Phase 0 draws the mockups the rest is built from.

## Context

No email Uncava sends carries a signature. The three gateways send exactly the HTML they are given — Gmail's
`users.messages.send` never appends the account's signature (only Gmail's own composers do; it is readable through
`users.settings.sendAs`), Microsoft Graph has no signature API at all, and Nylas's send is the same — so a
consultant's Gmail or Outlook signature never reaches the executive. `OutreachEmailBody` (a sequence step) and
`OutreachMarkdownEmail` (an own email) render the body alone, and the latter turns every image into its alt text on
purpose.

How comparable tools do it (vendor-stated, from web research):

| Product | What it does |
|---|---|
| HubSpot | Several signatures per user, one "global default", a default per connected inbox; a Signature token fills the default. Per-sequence choice is a long-standing request. |
| Outreach.io | One signature per mailbox, appended to one-off, bulk and sequenced emails; the unsubscribe link rides in it. |
| Gem | Appends the Gmail signature by default; off for all sequences in settings or per sequence. Outlook cannot be read, so Outlook users build one in Gem. |
| Bullhorn | A default signature plus others to switch between; advises building it in the app, since pasted HTML breaks. |
| Emelia / Lemlist | A `{{signature}}` token placed in each step, follow-ups included. |
| Exclaimer / WiseStamp | Prefer hosted images over CID-embedded: embedded ones are dropped on reply/forward by iOS Mail and Outlook for Windows and not shown by Outlook on the web. |

So: we store and append signatures ourselves; images are hosted by us, never embedded; a signature is built from
fields, never pasted HTML; several per person with one default; switchable per email.

## Decisions taken with the product owner (2026-11-01)

1. **Personal only.** Each staff member keeps their own signatures; no admin-defined firm templates in this version.
   Uncava ships a handful of **built-in layouts** to pick from, plus a free-form custom one.
2. **Several signatures, one default** per person per workspace. The composer offers the default, ticked; the
   person may untick it or choose another.
3. **Replies use the same default** as a new email — no separate reply signature.
4. **Sequences carry the signature on every step**, follow-ups included; the sender may choose which at Start, and
   that choice is frozen for the run.
5. **The profile gains a photo upload, a phone number and a LinkedIn URL**, which every signature can show. The
   photo upload is #1172's avatar; this plan adds the phone and LinkedIn.

## A. Model and ownership

Two owners, and the dependencies point one way:

- **`core/storage`** owns the hosted pictures (`app_lm_email_image`, `EmailImageService`, the public route). It knows
  nothing about signatures, so `core` never depends on a feature.
- **`outreach`** owns signatures (`app_lm_email_signature`, `/me/signatures…`, `EmailSignatureRenderer`,
  `SignatureSenders`). It reads the profile, the workspace (name, logo, website, mode), the mailbox and `BookingPages`.
  Nothing depends back on it.

Each phase brings its own migration, numbered at the next free version when it is built (V189 for phase 1 and V190
for phase 3 as of writing; main already holds V188).

**`app_lm_email_signature`** (phase 1): `workspace_id`, `user_id` (a signature is the person's in one workspace —
their title and firm can differ between workspaces), `name` ("Formal", "Short"), `layout`
(`CLASSIC | PHOTO | PHOTO_LOGO | LOGO_BANNER | MINIMAL | CUSTOM`, V34's CHECK idiom), `fields` jsonb (overrides
only), the show switches, `custom_body` (Markdown, ≤ 2,000, `CUSTOM` only), `disclaimer` (≤ 1,000), `is_default`
(one per user per workspace by partial unique index), `version`, timestamps. Tenant data: every query filters on
`AuthPrincipal.requireWorkspaceId()` and the caller's own user id, so nobody reads or edits another's signature.
At most 10 per person, and **exactly one default while any exist**:
- the first is the default;
- unticking the default changes nothing — another is made the default instead;
- deleting the default hands the mark to the oldest left.

A create locks the caller's user row, so a double submit cannot pass either rule.

**Fields, and where each comes from.** A field left unset follows its source, so a profile edit reaches every
signature; a value typed into a signature overrides it there alone.

| Field | Source when unset |
|---|---|
| Name | `app_lm_user.full_name` |
| Title | `app_lm_user.title` |
| Photo | the profile photo (V170's stored avatar: uploaded, or a provider's copy) |
| Company | the workspace's name — labelled through `useWorkspaceVocabulary` |
| Logo | the workspace's company logo (V68 snapshot), copied into our store |
| Website | the workspace's company website (V68) |
| Phone | `app_lm_user.phone` (new); an override is held to the same rule (`core/text`'s `PhoneNumbers`) |
| LinkedIn | `app_lm_user.linkedin_url` (new) |
| Email | the connected mailbox's address |
| Address, disclaimer | the signature's own only |
| Book a call | the consultant's booking link, offered only where `BookingPages.isOfferedFor` their mailbox |

**Profile (phase 1's migration):** `app_lm_user.phone` (≤ 40) and `linkedin_url` (≤ 255, a LinkedIn profile URL by
`LinkedInUrls`), on `PATCH /auth/me`. The **photo** is #1172's avatar: one upload path (`/auth/me/avatar`, its crop
dialog, rate limits and audit), one removal rule. Signatures add no upload of their own.

## B. Images in an email

A recipient's mail client must fetch the photo and logo with no session, and our bucket is private and streamed.

- **Hosted, not embedded** (see the research above).
- **Copied into our store the first time a signature draws one**, keyed per owner:
  - the photo, from the stored avatar, by the avatar's SHA-256 (one copy per person per picture);
  - the logo, from the workspace's published URL, by that URL's SHA-256 (one copy per workspace).

  Two workspaces never share a row, so nothing one does can break another's emails. The logo is never hotlinked from
  Apollo or the company's site. It is fetched through `PictureFetcher` on `core/http`'s `GuardedHttpClient`, the
  guard the client-metadata fetch uses: public addresses only, no redirect, a byte cap, one deadline. It is fetched
  off any transaction, when a signature first shows it. A logo that cannot be fetched or read is left out (never a
  broken `<img>`) and not asked for again for ten minutes.
- **Decoding is bounded.** A picture is read as a JPEG or PNG only. Its header is read first and anything past
  40 megapixels in area is refused before a pixel is decoded, since a few KB of PNG can declare gigabytes of raster.
  The rest is decoded subsampled to about twice its target. Then it is resized (photo 192×192, logo inside 480×160:
  2–3× for sharp screens) and **re-encoded with ImageIO**. That re-encode is what strips EXIF, GPS, ICC and text
  chunks, so no metadata-preserving resize library may replace it.
- Served at **`GET /api/v1/email-images/{token}`** (`permitAll`, like the booking page): a 256-bit random token, not
  the storage key; `Cache-Control: public, max-age=31536000, immutable`; 404 alike for anything that leads nowhere.
- **One token per stored picture**, shared by every email that shows it, and **never minted per send, message or
  recipient**. That is what keeps it from being an open-tracking pixel: a fetch identifies nobody. The token is kept
  raw because the server rebuilds the link to draw a signature; CLAUDE.md's invariants list it as an exception,
  beside the avatar's.
- **Not rate-limited per IP.** Mail clients' image proxies (Gmail's, Apple Mail Privacy Protection, Outlook's) fetch
  for all their users from a few shared addresses, so a per-IP budget would blank signatures for everyone behind one.
  Load is held by the `immutable` caching and a single indexed token lookup.
- **Never logged.** `RequestLogFilter` skips the path, and `ops/gcp/bootstrap.sh` adds a Cloud Logging exclusion on
  `httpRequest.requestUrl:"/api/v1/email-images/"`, since Cloud Run's own request log records URL, IP and user agent
  whatever the app does.
- A picture's row outlives the avatar or logo it was copied from, because a sent email names it.

## C. Rendering

**`EmailSignatureRenderer`** (`outreach/service`, pure, unit-tested per layout) turns a signature plus its resolved
fields into HTML. There is **no plain part**: every gateway sends a single HTML part (`RawEmail`, Graph and Nylas
alike), so there is no text form and no `-- ` separator.

- Email-safe HTML only: nested `<table>`s with inline styles, explicit `width`/`height` on every `<img>`, web-safe
  font stacks, no CSS classes, no background images. Text takes no colour of its own but the muted lines', so a
  client's dark mode can turn it light. To be checked in Gmail (web/iOS), Outlook (Windows, web) and Apple Mail, light
  and dark.
- Every value escaped (`HtmlUtils.htmlEscape`); links http/https/mailto/tel only; phone as `tel:`; the only `<img>`
  sources are our own email-image URLs. `OutreachMarkdownEmail`'s class doc says so: those are the only pictures an
  email Uncava sends carries.
- `CUSTOM` is written in the notes' shared editor (`features/notes`' `NoteEditor`) **with mentions off**. A signature
  belongs to nobody's position, and a mention token there would carry a teammate's id. It renders through
  `OutreachMarkdownEmail`'s rules: raw HTML is text, links are http/https/mailto only (a typed `tel:` link is
  stripped, so a phone there is plain text), and images are refused. A custom layout places the logo with its switch,
  not by typing a URL. Headings, lists and quotes are kept; a checklist is best avoided, since mail clients draw it
  unevenly.
- Placed after the body behind a spacer.

## D. Settings → Email signature

Under Account, for staff only (`@workspaceAuthorizer.staffOnly`, a 404 to a pure client, as Billing's reads are).
The page has:
- a list of the person's signatures with the Default badge, Make default, Duplicate and Delete;
- New signature, which opens the layout gallery (each layout drawn with the person's own details), then the field
  form beside a **live preview** (light/dark toggle).

The preview is drawn by the server's own renderer (`POST /me/signatures/preview`), so what the screen shows is what
is sent. The SPA draws every server-rendered signature (preview, list and composer) in a **sandboxed
`<iframe srcdoc>`** with no `allow-scripts`, never `dangerouslySetInnerHTML`. That is defence in depth should
escaping ever miss a value, and it keeps the app's styles and the signature's from leaking into each other.

A preview and the list **write nothing**. A booking link not yet claimed is drawn as a stand-in, never claimed by a
keystroke.

Routes: `GET|POST /me/signatures`, `PUT|DELETE /me/signatures/{id}`, `POST /me/signatures/{id}/default`,
`POST /me/signatures/preview`. Profile gains Phone and LinkedIn; the photo is #1172's.

## E. Sending

**Book a call needs its page first.** A signature that shows the link is drawn for sending through
`SignatureSenders.forSending`, which first runs `BookingPages.prepare` where the sender's mailbox offers a link: it
claims the slug and, on Nylas, makes the Scheduler page. A body using `{{bookingLink}}` already does the same. This
happens at write for an own email and at Start for a sequence, so no email freezes a link to a page that does not
exist, and a failure is the consultant's to see.

**An own email** (`ReplyComposer`, `EmailComposerDialog`): under the body sits an **Include signature** tick box,
ticked when the person has a default. With more than one signature there is also a picker naming the default first.
The chosen signature is drawn under the editor.

`SendMessageRequest.signatureId` (null: none). `OutreachMessageService.schedule` renders it and **appends its HTML to
the message's stored `body`** at write time, as attachments are copied then. So a scheduled email goes exactly as
previewed, and editing the signature later changes nothing already written. `body_text` stays the consultant's words
alone, so search, the conversation pane's text and the AI never read a signature. With no mailbox or no default, the
box is unticked and says why.

**A sequence:** Start's review shows the sender's signature with the same picker (default first, or none).
`StartSequenceRequest.signatureId` carries the choice. The rendered HTML is **frozen on the enrollment**
(`signature_html`, phase 3's migration) beside `first_body`, and `OutreachSendService.emailOf` appends it to the first
email and every follow-up. Nothing is stored on the sequence itself: a sequence is the position's, a signature its
sender's.

**Gateways are untouched**: the signature is part of `OutgoingEmail.htmlBody`. The test email from Settings carries
the default signature, which doubles as a "see it in your own inbox" check.

## F. What it never touches

No `CandidateResponse`, report, public API, MCP or notification read carries a signature. Saving one is not audited
(it is the person's own presentation, like their title); a profile photo change is #1172's audited avatar event.
On-screen words go to `docs/glossary.md`; each new field gets its `docs/validation-matrix.md` row.

## G. Phases

0. **Mockups** — `Settings.dc.html`: Email signature page (list, gallery, form, preview) and Profile's phone and
   LinkedIn; `Outreach.dc.html`: the composer's Include signature row and picker, and Start's review.
1. **Signatures** — its migration, profile phone and LinkedIn, the email-image store and route (photo from the avatar,
   logo through the guarded fetcher), the renderer with its tests, the CRUD and preview routes, Settings → Email
   signature.
2. **Own emails** — the composer's tick box, picker and preview; `signatureId` on the send; booking prepared and the
   HTML appended at write.
3. **Sequences** — its migration, Start's picker, booking prepared and `signature_html` frozen at Start, every step
   appended.

Later, not planned:
- admin firm templates (locked logo and disclaimer);
- a separate reply signature;
- importing a Gmail signature (`users.settings.sendAs`, which needs the `gmail.settings.basic` scope and so a
  reconnect; Outlook offers no equivalent);
- a `{{signature}}` token for placing it elsewhere than the end;
- a purge of email pictures no email names any more. That needs a reference to count first: a sent body holds the
  HTML, so the image ids a message or enrollment carries would be recorded at write time (a link table, or a
  `last_referenced_at` stamped on the row). The same follow-up deletes the bucket objects of rows an account or
  workspace delete cascades away.
