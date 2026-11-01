# Email signatures — design and build plan

Status: **planned** (decisions taken 2026-11-01). Nothing below exists yet; each phase is one PR, and until a phase
ships the tables, routes and entities it names do not exist. Phase 0 draws the mockups the rest is built from.

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
5. **The profile gains a photo upload, a phone number and a LinkedIn URL**, which every signature can show.

## A. Model

**`app_lm_email_signature`** (V169): `workspace_id`, `user_id` (a signature is the person's in one workspace — their
title and firm can differ between workspaces), `name` ("Formal", "Short"), `layout`
(`CLASSIC | PHOTO | PHOTO_LOGO | LOGO_BANNER | MINIMAL | CUSTOM`, V34's CHECK idiom), `fields` jsonb (overrides
only), `custom_body` (Markdown, ≤ 2,000, `CUSTOM` only by CHECK), `is_default` (one per user per workspace by
partial unique index), `version`, timestamps. Tenant data: every query filters on
`AuthPrincipal.requireWorkspaceId()` and the caller's own user id — nobody reads or edits another's signature.
At most 10 per person.

**Fields, and where each comes from.** A field left unset follows its source, so a profile edit reaches every
signature; a value typed into a signature overrides it there alone.

| Field | Source when unset |
|---|---|
| Name | `app_lm_user.full_name` |
| Title | `app_lm_user.title` |
| Photo | the profile photo (below) |
| Company | the workspace's name — labelled through `useWorkspaceVocabulary` |
| Logo | the workspace's company logo (V68 snapshot), copied into our store |
| Website | the workspace's company website (V68) |
| Phone | `app_lm_user.phone` (new) |
| LinkedIn | `app_lm_user.linkedin_url` (new) |
| Email | the connected mailbox's address |
| Address, second phone, disclaimer | the signature's own only |
| Book a call | the consultant's booking link, offered only where they have one |

**Profile (V169, same migration):** `app_lm_user.phone` (≤ 40) and `linkedin_url` (≤ 255, a LinkedIn profile URL by
`LinkedInUrls`), on `PATCH /auth/me`. **Photo upload** finishes what V25 reserved: `avatar_source = 'USER'`, the file
in `core/storage`'s `DocumentStore`, `avatar_url` its email-image URL (below). A provider never replaces a `USER`
photo (V25's rule already).

## B. Images in an email

A recipient's mail client must fetch the photo and logo with no session, and our bucket is private and streamed.

- **Hosted, not embedded** (see the research above).
- On upload or signature save each image is **copied into our store** — the workspace logo too, never hotlinked
  from Apollo or the company's site — decoded, resized (photo 160×160, logo ≤ 400 wide: 2× for retina), re-encoded
  as PNG/JPEG with metadata stripped, and keyed by its hash.
- Served at **`GET /api/v1/email-images/{token}`** (`permitAll`, like the booking page): a long random token, not the
  storage key; `Cache-Control: public, max-age=31536000, immutable`; 404 alike for anything that leads nowhere;
  rate-limited per IP. **No hit is logged or counted** — this is not an open-tracking pixel and must never become one.
- A replaced image keeps its old token serving for emails already sent; tokens nothing references are purged after
  `lightmove.signatures.image-retention` (365d), so an old email eventually shows alt text, never a broken trail.

## C. Rendering

**`EmailSignatureRenderer`** (`outreach/model`, pure, unit-tested per layout) turns a signature plus its resolved
fields into `(html, text)`:

- Email-safe HTML only: nested `<table>`s with inline styles, explicit `width`/`height` on every `<img>`, web-safe
  font stacks, no CSS classes, no background images; tested in Gmail (web/iOS), Outlook (Windows, web), Apple Mail,
  light and dark mode — a logo should carry its own background, since a transparent dark logo vanishes in dark mode.
- Every value escaped (`HtmlUtils.htmlEscape`); links http/https/mailto/tel only; phone as `tel:`; the only `<img>`
  sources are our own email-image URLs.
- `CUSTOM` renders its Markdown through `OutreachMarkdownEmail`'s rules, with images still refused — a custom layout
  places the photo and logo by choosing them in the form, not by typing a URL.
- Placed after the body behind a `-- ` text separator in the plain part and a spacer in HTML.

## D. Settings → Email signature

Under Account, every staff member's (a pure client sends no email). A list of the person's signatures with the
Default badge, Make default, Duplicate, Delete; New signature opens the layout gallery (each layout drawn with the
person's own details), then the field form beside a **live preview** (light/dark toggle) rendered by the server's own
renderer (`POST /me/signatures/preview`), so what the screen shows is what is sent. Routes:
`GET|POST /me/signatures`, `PUT|DELETE /me/signatures/{id}`, `POST /me/signatures/{id}/default`,
`POST /me/photo` (multipart). Profile gains Change photo, Phone and LinkedIn.

## E. Sending

**An own email** (`ReplyComposer`, `EmailComposerDialog`): under the body, an **Include signature** tick box — ticked
when the person has a default — and, with more than one, a picker naming the default first; the chosen signature is
drawn greyed under the editor. `SendMessageRequest.signatureId` (null: none). `OutreachMessageService.schedule`
renders it and **appends its HTML to the message's stored `body`** at write time, as attachments are copied then —
so a scheduled email goes exactly as previewed, and editing the signature later changes nothing already written.
`body_text` stays the consultant's words alone, so search, the conversation pane's text and the AI never read a
signature. No mailbox, no default: the box is unticked and says why.

**A sequence:** Start's review shows each sender's signature with the same picker (default first, or none);
`StartSequenceRequest.signatureId`; the rendered HTML is **frozen on the enrollment** (`signature_html`, V169) beside
`first_body`, and `OutreachSendService.emailOf` appends it to the first email and every follow-up. Nothing on the
sequence itself — a sequence is the position's, a signature its sender's.

**Gateways are untouched**: the signature is part of `OutgoingEmail.htmlBody`. The test email from Settings carries
the default signature, which doubles as a "see it in your own inbox" check.

## F. What it never touches

No `CandidateResponse`, report, public API, MCP or notification read carries a signature. Saving one is not audited
(it is the person's own presentation, like their title); a profile photo change is `PROFILE_UPDATED` as today.
On-screen words go to `docs/glossary.md`; each new field gets its `docs/validation-matrix.md` row.

## G. Phases

0. **Mockups** — `Settings.dc.html`: Email signature page (list, gallery, form, preview) and Profile's photo, phone
   and LinkedIn; `Outreach.dc.html`: the composer's Include signature row and picker, and Start's review.
1. **Signatures** — V169, profile fields and photo upload, the email-image store and route, the renderer with its
   tests, the CRUD and preview routes, Settings → Email signature.
2. **Own emails** — the composer's tick box, picker and preview; `signatureId` on the send; the HTML appended at write.
3. **Sequences** — Start's picker, `signature_html` frozen on the enrollment, every step appended.

Later, not planned: admin firm templates (locked logo and disclaimer), a separate reply signature, importing a Gmail
signature (`users.settings.sendAs`, which needs the `gmail.settings.basic` scope and so a reconnect; Outlook offers
no equivalent), a `{{signature}}` token for placing it elsewhere than the end.
