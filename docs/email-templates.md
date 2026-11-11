# Email templates: design and build plan

Status: **Phases 0–3 built** (epic #1247, decisions taken with the product owner on 2026-11-06, decision 16 on
2026-11-10 and extended to the composer the same day): the design, the mockups, the table, the token engine, Settings →
Email templates, the composer and sequence steps.
Each phase is one PR into `feature/email-templates`, and each is stacked on the one before. The branch merges to
`main` once all four are in. Until a phase ships, the tables, routes and entities it names do not exist. Phase 0 is
this document and the mockups that Phases 1–3 are built from: `claude-design/Outreach.dc.html` (the composer and the
sequence editor) and `claude-design/Settings.dc.html` (Settings → Email templates).

## Context

Asked for in #1200:

> When replying to an email we regularly send standard replies (to save time). Can we have an ability to create and
> apply templates? It should allow for auto inserts like Sequences: `{{firstName}}` `{{currentTitle}}`
> `{{currentCompany}}` `{{positionTitle}}` `{{location}}` `{{senderFirstName}}`

What the code does today:

- **No template exists.** A consultant retypes a standard reply, or pastes it from somewhere else.
- **Tokens are typed blind.** The composer (`ReplyComposer`, which also draws Write email and New email) has no token
  menu. A token is filled when the email is written (`OutreachMessageService.saveScheduled` →
  `OutreachPersonalisation.tokensOf(…).renderMarkdown(body)`). The composer never shows its value.
- **A blank value goes out blank.** `SequenceTokens.renderWith` turns a known token whose value is null into `""`, so
  "Hi {{firstName}}," reaches someone with no name on file as "Hi ,".
- **An unknown token goes out literally.** Nothing checks the vocabulary anywhere: neither a sequence step's save
  (`OutreachSequenceService.stepsOf`) nor an own email. A misspelled `{{curentTitle}}` is sent as typed.
- **A new email's subject is never rendered.** `saveScheduled` takes the subject through `requireSubject` alone, so a
  subject of `{{firstName}}, a quick question` arrives with the braces in it. A sequence's first subject *is* rendered
  (`OutreachEnrollmentService.enroll` → `SequenceTokens.render`).
- **Two renderers.** `render` serves sequences: the first email is frozen at Start, and each follow-up is rendered from
  the sequence as it stands when it is sent (`OutreachSendService.emailOf`). Both go out through the plain-text
  `OutreachEmailBody`. `renderMarkdown` serves only an own email. It escapes whatever in a value could open markup,
  and makes the booking link the one autolink.

## Market practice (research, Nov 2026)

| | Shared templates | Fallback | Missing value | Signature | In sequences |
|---|---|---|---|---|---|
| HubSpot | private, or shared with teams/users | account default (marketing) | red at enrolment, filled first | added separately | template as a step |
| Outreach | private / others see / others modify | `{{#if}}…{{else}}` | red in preview, not delivered | separate | linked or cloned |
| Salesloft | personal / team (admins) | — | "will not send" | — | cadence steps |
| Apollo | owner + role rights, folders | `{{#if}}` | red, moved to Not sent | appended at send | linked or cloned |
| Greenhouse | mine / organisation-wide (permission) | — | undocumented | `{{MY_SIGNATURE}}` typed by hand | — |
| Lever | personal / team | — | values filled on insert | — | — |
| Ashby | personal / company-wide (admin) | — | highlighted, send blocked | per-template toggle | — |
| Front | private / shared per inbox | `{{x \| 'there'}}` | fallback, else blank | separate | — |
| lemlist, Gem | — | `{{firstName\|there}}` | review filter / blank | — | — |
| **Uncava** | personal / shared; owner or admin edits | `{{firstName\|there}}` | red pill, Send blocked, server refuses | Include signature, never in the template | copied into a step |

Every product shares the same baseline: personal and shared templates, a picker with search in the composer, a token
menu, and a preview. The pitfalls they document are each answered below:

- **Raw tokens reaching the recipient** (Bullhorn, HubSpot): we block the send (§E).
- **A shared edit landing on everyone** (Greenhouse warns about it): only the owner or an admin can edit, and the edit
  is audited (§B).
- **A template overwriting a reply's subject** (Front): a reply keeps its subject (§D).
- **A booking link in a shared template** (Lever limits it to personal templates): ours is always the sender's own
  (§C).

Sources (vendor help centres, read November 2026):

- HubSpot: [create and send templates](https://knowledge.hubspot.com/templates/create-and-send-templates),
  [enrol contacts in a sequence](https://knowledge.hubspot.com/sequences/enroll-contacts-in-a-sequence) (missing
  tokens in red, fixed before enrolling)
- Outreach: [variables overview](https://support.outreach.io/hc/en-us/articles/226680368) (sender variables; content
  with a broken variable fails),
  [content governance](https://support.outreach.io/hc/en-us/articles/13441885273115)
- Salesloft: [team templates](https://salesloft.com/resources/news/salesloft-product-release-team-templates-2/)
- Apollo: [create and use email templates](https://knowledge.apollo.io/hc/en-us/articles/4409238165389-Create-and-Edit-Email-Templates),
  [advanced dynamic variables](https://knowledge.apollo.io/hc/en-us/articles/4409735256845-Use-Advanced-Dynamic-Variables)
  (red in preview, unresolved blocks the send, Not sent)
- Greenhouse: [add and edit email templates](https://support.greenhouse.io/hc/en-us/articles/115004020946-Add-and-edit-email-templates)
  (My Templates and Organization-Wide; an org-wide edit reaches everyone)
- Lever: [creating email templates](https://help.lever.co/hc/en-us/articles/20087316264221) (personal or team;
  personal Easy Book links only in personal templates),
  [placeholders](https://help.lever.co/hc/en-us/articles/20087359674269)
- Ashby: [email template creation and management](https://docs.ashbyhq.com/email-template-creation-and-management)
  (a placeholder holds the send)
- Front: [template variables and fallbacks](https://help.front.com/t/80244g)
- lemlist: [custom variable format](https://help.lemlist.com/en/articles/4452688-custom-variable-format)
  (`{{variable|fallback}}`)
- Also read for the epic: Reply.io, Gem, Loxo, Bullhorn, LinkedIn Recruiter, Gmail and Outlook templates,
  Superhuman snippets, Help Scout saved replies and Zendesk macros

## A. Decisions taken with the product owner (2026-11-06)

1. **Personal or shared.** A template belongs to its owner. **Share with workspace** lets every staff member apply
   it. *Why:* every product offers both, and a firm's standard replies are only worth writing once.
2. **The owner or an admin changes a shared template.** Only its owner or a `WORKSPACE_MANAGE` holder can edit,
   unshare or delete a shared template. Everyone else applies it, or duplicates it into a template of their own.
   *Why:* a shared edit reaches everyone who applies it next (Greenhouse's warning), so it is held to the people
   accountable for it and audited.
3. **Staff only.** A pure client sees none of it. Nothing rides `CandidateResponse`, the report, the public API, MCP
   or a notification. *Why:* a template is how the firm writes to executives, which is not the client's to read.
4. **What a template holds:** a name, an optional subject, and a body. The body is formatted in the notes editor's
   Markdown, up to 10,000 characters (an own email's limit), and may carry tokens. There are no attachments and no
   signature. *Why:* the body is exactly what the composer edits, so applying one is lossless. Attachments are
   "later".
5. **Tokens.** A template can use the six sequence tokens, plus `{{bookingLink}}` where booking pages are offered.
   Sender tokens are always the *sender's*, so a shared template works for whoever sends it. `{{opener}}` (a
   sequence's AI line), `{{signature}}` and any token we do not know are refused at save. *Why:* `opener` only
   exists inside a sequence run, and a token we cannot fill would otherwise reach the recipient.
6. **Fallbacks.** `{{firstName|there}}`: the text after the pipe is used when the executive has no value. Fallbacks
   work everywhere tokens render, in own emails and in sequences. *Why:* this is the shortest form any product
   offers (lemlist, Gem, Front), and it covers the common case without a conditional language.
7. **A missing value blocks the send.** In the composer, a token with no value and no fallback is a red pill, and
   Send waits until it is filled, given a fallback or removed. The server refuses such an email too
   (`OUTREACH_TOKENS_UNFILLED`, naming the tokens), and refuses an unknown token the same way. *Why:* "Hi ," and a
   literal `{{currentTitle}}` must never go out. The pill is only a preview, so the server must decide again.
8. **Sequences honour fallbacks; blanks are only marked.** The review marks a blank token red for each person, but
   blocking a Start on blanks is later. *Why:* a run is many people at once, so a block needs its own per-person
   "fill in or skip" design.
9. **Tokens show as pills.** In the composer each token is a pill showing this executive's value ("Duren"), red when
   blank. It is still a token underneath, so Save as template keeps `{{firstName}}`. *Why:* the consultant sees the
   email as it will arrive, without losing the token. The server fills the tokens when the email is written, and the
   pill previews the same values but is never their source.
10. **Applying a template:** into an empty editor it replaces the body, and into text it goes in at the cursor. A new
    email takes the template's subject only when its own subject is empty. A reply takes the body only and keeps the
    thread's `Re:` subject. *Why:* nothing typed is lost, and threading never breaks (Front's pitfall).
11. **Own-email subjects get tokens.** A new email's subject is rendered on one line, so a profile value can never
    break the header.
12. **Surfaces in v1:**
    - the composer (Write email, the Outreach reply, New email): a **Templates** picker, an **Insert** token menu and
      **Save as template**
    - the sequence editor: **From template** and **Save as template** on a step. A template is copied in, never
      linked, so editing a template changes no step.
    - **Settings → Email templates**
13. **Named apart from role templates.** Settings → *Templates* is the position brief library. This feature is *Email
    templates*: `/settings/email-templates`, `features/emailtemplates`, the `EmailTemplate*` classes and the
    `EMAIL_TEMPLATE_*` codes. `core/email/service/EmailTemplates` (the system's own emails) and the position
    templates' `TEMPLATE_*` codes already exist and are untouched.
14. **Each email records the template it came from** (`template_id` on the message), so usage and reply-rate figures
    can come later without a migration.
15. **Audit.** Sharing, unsharing, and any edit or delete of a shared template are events in the customer audit log
    (Outreach). A personal template is unaudited, like a signature. *Why:* a shared template is firm-wide content
    that any member applies; a personal one is a person's own drafts.
16. **Variables look and work exactly as in a sequence** (product owner, 2026-11-10). The template editor carries the
    sequence editor's own Insert bar (`TokenInsertBar`, shared by both screens): the same chips, in the same order
    (`templateTokenOptions` is `tokenOptions` less the opener), each inserted as typed — `{{firstName}}` — into
    whichever of the subject or the email the cursor was last in. The email shows a variable as that text, as a
    sequence step does, never as a pill with a label. On screen it is a **variable** (`docs/glossary.md`), the word
    the refusals use too. *Why:* a consultant writes both, and two ways of writing the same `{{firstName}}` would
    have them wondering which one is right. This replaces §F's pill in Settings, so Phase 1 adds no token node to the
    editor: the notes editor already keeps `{{firstName|there}}` as text (it writes the pipe as `\|`, which the
    grammar reads). **The composer follows it too** (product owner, 2026-11-10): it carries the same Insert bar and
    shows `{{firstName}}` as typed. This replaces decision 9's pills and §E's red pill and its popover. A variable with
    nothing on file and no fallback is named instead in one line under the editor ("Fill in or remove 1 variable to
    send: {{location}} has nothing on file"), and Send waits; the Templates picker's preview reads a template with the
    executive's values.

## B. Model and ownership

Templates are owned by **`outreach`**, beside signatures: `app_lm_email_template`, `/me/email-templates…`,
`EmailTemplateService`. It reads the user (name and face) and `WorkspaceAccess`. Nothing depends back on it.

**`app_lm_email_template`** (Phase 1, V205):

| Column | |
|---|---|
| `id` | uuid PK |
| `workspace_id` | `REFERENCES app_lm_workspace ON DELETE CASCADE` |
| `owner_user_id` | `REFERENCES app_lm_user ON DELETE CASCADE`, as signatures do: deleting an account deletes that person's shared templates too |
| `name` | `varchar(80)`, not blank (CHECK) |
| `subject` | `varchar(200)`, nullable, one line (CHECK: no `\n` or `\r`) |
| `body` | `varchar(10000)`, 1–10,000, Markdown (`body_format` is not stored: always Markdown) |
| `shared_at` | nullable: null is personal |
| `updated_by` | the last editor, who for a shared one may be an admin |
| `created_at`, `updated_at`, `version` | `BaseEntity` |

- **Indexes:** `(workspace_id, owner_user_id)`, and `(workspace_id) WHERE shared_at IS NOT NULL`. The touch trigger is
  `app_lm_touch_updated_at`.
- **Forced row-level security** with V193's policy: `app_lm_tenant` using `app_lm_tenant_visible(workspace_id)`.
  `TenantTableGuard` finds the table from the schema, and `TenantTableGuardIntegrationTest` fails the build without
  the policy.
- **Privacy is the service's job.** RLS keeps a template in its workspace, but keeping a personal template private is
  `EmailTemplateService`'s: every personal read is keyed on the caller, so another person's personal template reads as
  missing (`EMAIL_TEMPLATE_NOT_FOUND`).
- **A member's removal deletes nothing.** Their shared templates stay usable and an admin manages them; their personal
  ones come back if they rejoin, since V81 rejoins the same membership row.
- **At most 100 per owner per workspace** (`EMAIL_TEMPLATE_LIMIT_REACHED`), counted under the owner's row lock.
  `UserRepository.lockForSignatureChange` is generalised to `lockForOwnContentChange` and used by both features.
- **Edits are versioned.** `PUT` and `DELETE` carry the `version` they read, and a stale one is
  `EMAIL_TEMPLATE_STALE`, following `CandidateScorecardService`'s explicit check. Two people may now edit one shared
  row, which a signature never faced.

**`app_lm_outreach_message.template_id`** (V205): `REFERENCES app_lm_email_template ON DELETE SET NULL`, nullable,
with a partial index `WHERE template_id IS NOT NULL`. It is written from Phase 2. It is resolved by id, workspace
**and** whether the sender may see the template; anything else is stored as null, never refused. Under RLS a foreign
key alone does not check the workspace, since the reference check runs as the table owner.

### Who may do what

| | Owner | Another staff member | `WORKSPACE_MANAGE` holder | Pure client |
|---|---|---|---|---|
| Personal template | read, edit, share, delete, duplicate | missing (404) | missing (404) | 404 on every route |
| Shared template | read, edit, unshare, delete, duplicate | read, apply, duplicate; any change `EMAIL_TEMPLATE_NOT_YOURS` (403) | read, edit, unshare, delete, duplicate (audited) | 404 |

An admin cannot read anyone's *personal* template, as with signatures and notes.

## C. Tokens

**Grammar.** `{{name}}` or `{{name|fallback}}`:

```
\{\{\s*([A-Za-z]+)\s*(?:\\?\|([^{}|\n]{1,60}))?\s*}}
```

- A name is letters only, as today.
- A fallback is 1–60 characters on one line, with no braces and no pipe. It is trimmed and its inner whitespace
  collapsed.
- **An escaped pipe (`\|`) is accepted**, because the notes editor's Markdown serializer escapes every `|` in text.
  The token node (§F) writes its token raw, but a `{{x|y}}` pasted as plain text into an older editor, or typed
  before the pill rule fired, arrives as `{{x\|y}}`.
- **A fallback on `bookingLink` is refused.** A link with a fallback would be text pretending to be a link.

**Where each value comes from** (`OutreachPersonalisation.tokensOf` / `senderContextOf`, unchanged):

| Token | Value |
|---|---|
| `firstName` | the first word of the executive's full name (`SequenceTokens.firstNameOf`) |
| `currentTitle` | the executive's title (`OutreachRecipient.title`) |
| `currentCompany` | the company they are mapped at (`OutreachRecipient.companyName`) |
| `positionTitle` | the brief's role title (`PositionService.briefOf(…).details().roleTitle()`) |
| `location` | the executive's city, else their country |
| `senderFirstName` | the **sender's** first name, whoever owns the template |
| `bookingLink` | the **sender's** booking link (`BookingPages.linkOf`). Claimed by `prepare` only when an email that uses it is written, never by a read |
| `opener` | a sequence run's AI line (`OutreachOpenerDrafter`); sequences only |

**Vocabulary per surface:**

| Surface | Takes | Refused |
|---|---|---|
| Email template (save) | the six, `bookingLink` where `BookingPages.isOffered` | `opener`, `signature` (its message says Include signature adds it), anything else: `VALIDATION_FAILED` on `subject` or `body` |
| Own email (send) | the six, `bookingLink` | `opener` and anything else: `OUTREACH_TOKENS_UNFILLED` (409, `tokens`) |
| Sequence step | the six, `bookingLink`, `opener` | unchanged in v1: an unknown token is still left as typed and shows in the review |

**The engine.** `outreach/model/SequenceTokens` and its web twin `features/outreach/lib/sequenceTokens.ts` gain:

- the fallback in `render` and `renderMarkdown`. `renderMarkdown` escapes a fallback and collapses its whitespace
  exactly as it does a value, since a template's fallback is author-typed text in a Markdown body.
- `renderLine(template)`: a subject, rendered with every value's whitespace collapsed and the result on one line.
- `unknownIn(text, vocabulary)`: the names not in the vocabulary, in order of first use.
- `unfilledIn(text)`: the names with no value and no fallback.

**Widening the pattern changes live sequences. Read this before deploying Phase 1.** A follow-up is rendered when it
is sent (`OutreachSendService.emailOf`), not frozen at Start. A step whose text already holds `{{x|y}}` (unlikely,
but nothing forbade it) is sent literally today and **as a token from Phase 1 on**, in runs already under way
included. A first email is not affected: it was rendered and frozen at Start (`first_body`) and is sent as stored.
Before the deploy, count them:

```sql
SELECT count(*) FROM app_lm_outreach_sequence_step
 WHERE coalesce(subject, '') || body ~ '\{\{\s*[A-Za-z]+\s*\\?\|';
```

If the count is not zero, read the steps with their owners before the deploy.

## D. Applying a template

*Phase 2 as built:* `TemplatePicker` (`features/emailtemplates`) beside Attach, `SaveAsTemplateDialog` at its foot, and
`NoteEditorHandle.insertDoc` placing a template's Markdown (`parseNoteMarkdown`) or Insert follow-up's plain step
(`plainNoteDoc`) at the cursor. The email records the template applied last (`templateId`).

In `ReplyComposer`, which draws Write email (`EmailComposerDialog`), the Outreach reply and New email:

- **Into an empty editor** the template replaces the body. **Into text** it goes in at the cursor, or at the end where
  the editor has never been focused.
- **A new email** takes the template's subject only when its own subject is empty.
- **A reply** takes the body only and keeps the thread's `Re:` subject (`composer.replySubject`), so threading never
  breaks.
- **The Include signature choice and the picked signature are left alone.**
- **Insert follow-up** (a live run's next step) goes through the same insert. A step is plain text, so it is inserted
  as `plainNoteDoc` rather than re-read as Markdown.

**The picker** (Templates, beside Attach):
- a search box
- **Recently used**: the caller's five, read from `template_id` on their own messages, newest first
- **Mine**, and **Shared**: colleagues' shared templates, with the owner's face
- a preview of the highlighted template with this executive's values
- **Save as template** and **Manage templates** (Settings) at its foot

**Save as template** asks for a name and a Share with workspace switch. It saves the editor's Markdown with its
tokens (the pills serialised back to `{{…}}`), plus the subject for a new email, and never the signature. The
signature lives only in the stored HTML `body` and never in the editor.

## E. The send guard

*Phase 2 as built (decision 16):* no pills. `composer.tokens` (the server's own values, `SequenceTokensResponse`) feeds
the line under the editor and the picker's preview; the line names each variable no email fills and each with nothing
on file and no fallback, and Send now and Schedule wait. The server's `OUTREACH_TOKENS_UNFILLED` makes the composer read
the conversation again, so the line names what went blank meanwhile. The bullets below are the Phase 0 plan.

- **Pills.** The conversation read's `composer` carries this executive's values (`tokens`, from
  `OutreachPersonalisation.tokensOf` in `ConversationService.composerOf`; the booking link read only through `linkOf`,
  which never claims a slug). A pill shows its value. Until a link exists, `bookingLink` shows *(your booking link)*.
  A pill with no value and no fallback is **red**. Its popover offers **Add a fallback**, **Replace with text** and
  **Remove**.
- **Send waits** while any pill is red, with a line under it naming which: "Fill in or remove 1 token to send:
  Location".
- **The server decides again.** `OutreachMessageService.saveScheduled` renders a new email's subject with `renderLine`
  and checks again that it is one line. After `bookingPages.prepare` (now also when only the subject uses
  `bookingLink`), it refuses with **`OUTREACH_TOKENS_UNFILLED`** (409, a `tokens` property listing the names) when a
  token has no value and no fallback, or is outside the composer's vocabulary (`{{opener}}` included). The composer
  then refetches the conversation and marks the pills, as it refetches the signatures on `EMAIL_SIGNATURE_NOT_FOUND`
  today. A value can disappear between the read and the send, for example when the executive's title is cleared in
  another tab.

## F. The editor

*Phase 1 as built (decision 16):* the template editor is the notes editor with `editorRef` (`NoteEditorHandle
.insertText`, the text at the cursor, or at the end where it was never focused) under the shared `TokenInsertBar`.
No token node and no `schema="email"` were built: a variable is plain text, as in a sequence step. The bullets below
are the Phase 0 plan, kept for Phase 2's decision.

- **A token node** in `features/notes/lib/noteMarkdown.ts` is an inline atom with its own Markdown tokenizer and raw
  serializer, as `NoteMentionNode` has. So `{{firstName|there}}` round-trips untouched, though the text serializer
  escapes every `|`. Input and paste rules turn a typed `{{…}}` into a pill.
- **It lives in a separate `emailExtensions()` schema**, never in `noteExtensions()`, so notes never grow pills.
- **`NoteEditor` and `RichNoteEditor` gain `schema="email"`** and an `editorRef` that inserts at the cursor. Settings
  (Phase 1) and the composer (Phase 2) share both.
- **A pill's face depends on where it is drawn.** In Settings it shows the token's label ("First name"), since there
  is no executive. In the composer it shows the executive's value.
- **The Insert menu** lists `tokenOptions(bookingLinkOffered)` without `opener`, each with its tip.

## G. With email signatures (#1157)

*Since #1277 (on `main`, merged into this branch):* a **sequence step** may carry `{{signature}}`, which places the run's
signature there (`SequenceTokens.SIGNATURE`). A template and an own email still refuse it, as below: their signature is
Include signature's, placed after the words. So the template and composer Insert bar is the sequence bar less the opener
and the signature (`templateTokenOptions`), and Phase 3's From template never brings one into a step.

- **A template never holds a signature.** The signature stays the composer's Include signature choice, appended after
  the body when the email is written, exactly as today (`SendMessageRequest.signatureId` →
  `signatures.requireRenderedForSending`). Applying a template never changes that choice or the picked signature.
- **`{{signature}}` is refused at save**, with a field error saying Include signature adds it. #1157 kept that token
  "later, not planned".
- **A shared template closes with the signature of whoever sends it.** No template names a signature.
- **The template editor draws the author's default signature greyed under the body** (`SignatureUnderEmail`), so the
  author sees the email as it will go out and leaves the sign-off to the signature.
- **Sequences:** a step copied from a template still closes with the signature chosen at Start (`signature_html`).

## H. Settings → Email templates

`/settings/email-templates`, under *Your account*, staff only (`RequireStaff`, `staffOnly` in `settingsSections.ts`):

- **The list:** All · Mine · Shared, a search box, and rows showing the name, subject, owner (face and name), a Shared
  chip and when it was last updated. A row's ⋯ offers Edit, Duplicate and Delete. A colleague's shared row offers
  Duplicate alone.
- **New template** is off at 100. An empty list explains what a template is and offers New template.
- **The editor:** name, subject, the body with the toolbar and Insert, Share with workspace, and the author's default
  signature greyed beneath. Editing a shared template says the change reaches everyone who applies it next.
- **Duplicate** makes a personal copy, owned by the caller, named "Copy of …" (cut to 80).
- **Deleting a shared template asks first** and says everyone in the workspace loses it. A personal one asks too,
  without that line.

## I. Routes, codes and audit

**Routes** (all `@workspaceAuthorizer.staffOnly`, so a pure client gets a 404):

| Route | |
|---|---|
| `GET /me/email-templates` | the caller's own and the workspace's shared ones. Each carries `canEdit`, `owner` (`{userId, fullName, avatarUrl}`), `shared`, `version` and `plainBody` (`NoteText.plainEmailTextOf`, paragraphs apart, for sequence steps). From Phase 2 it also carries `recentlyUsed` (ids) |
| `POST /me/email-templates` | `{name, subject?, body, shared}` |
| `PUT /me/email-templates/{id}` | `{name, subject?, body, version}` |
| `DELETE /me/email-templates/{id}?version=` | |
| `POST /me/email-templates/{id}/duplicate` | a personal copy owned by the caller |
| `PUT /me/email-templates/{id}/sharing` | `{shared, version}` |

**Codes** (new; `ErrorCode` before `INTERNAL_ERROR`):

| Code | Status | When |
|---|---|---|
| `EMAIL_TEMPLATE_NOT_FOUND` | 404 | no such template, or another person's personal one |
| `EMAIL_TEMPLATE_NOT_YOURS` | 403 | a shared template changed by someone who is neither its owner nor a `WORKSPACE_MANAGE` holder |
| `EMAIL_TEMPLATE_LIMIT_REACHED` | 409 | a 101st template, by create or by duplicate |
| `EMAIL_TEMPLATE_STALE` | 409 | the `version` sent is not the row's |
| `OUTREACH_TOKENS_UNFILLED` | 409 | an own email with a blank, unknown or `opener` token; `tokens` names them (Phase 2) |

An unknown token, `{{opener}}`, `{{signature}}` or a fallback on `bookingLink` in a template is `VALIDATION_FAILED` on
that field.

**Audit** (`WorkspaceEventType`, the Outreach group of `AuditLogGroup`, phrased in `AuditLogPhrasing.outreach()`; the
target is the template, and the line names the template only):

| Event | Recorded when | Phrase |
|---|---|---|
| `EMAIL_TEMPLATE_SHARED` | a personal template is shared, or created shared | Shared the email template "…" |
| `EMAIL_TEMPLATE_UNSHARED` | a shared template goes personal | Stopped sharing the email template "…" |
| `EMAIL_TEMPLATE_UPDATED` | a shared template is edited (by its owner or an admin) | Edited the shared email template "…" |
| `EMAIL_TEMPLATE_DELETED` | a shared template is deleted | Deleted the shared email template "…" |

A personal template's create, edit and delete are unaudited.

As built, a line names its template only while that template is still shared (`SharedEmailTemplateNames`, answered by
`outreach` for `AuditEntryDescriber`): an admin never reads the name of a template its owner has made personal again,
and a deleted one has no row to name. Those lines read "Stopped sharing an email template" and "Deleted a shared email
template".

## J. Phases

0. **Design (#1248).** This document and the mockups: `Outreach.dc.html` (the composer's Templates picker, Insert,
   pills, the blank pill's popover, the blocked Send line, Save as template, and the sequence editor's From template
   and Save as template) and `Settings.dc.html` (Email templates: list, editor, delete confirmations). The stale
   lines in `docs/outreach-conversations.md` and `docs/email-signatures.md` are corrected on the way.
1. **Templates, the token engine, Settings (#1249).**
   - V205 (both columns of §B; renumbered as main took V195–V204).
   - The engine (§C) on both twins.
   - The CRUD routes, codes and audit.
   - The email schema's token node and `schema="email"` / `editorRef`.
   - Settings → Email templates.
   - `docs/validation-matrix.md` rows, `docs/glossary.md` (Email template as distinct from role template; Fallback), and
     a CLAUDE.md paragraph beside the one on signatures.
   - Tests:
     - `EmailTemplateIntegrationTest`: owner-only reads, a shared one visible to all, a non-owner's edit refused, an
       admin's edit audited, a personal one unaudited, a client's 404, the token refusals, the limit, a stale version,
       Duplicate.
     - `SequenceTokensTest` and `sequenceTokens.test.ts`: fallback, escaped pipe, a fallback escaped in Markdown,
       `renderLine`, unknown and unfilled.
     - `noteMarkdown.test.ts`: the round trip, fallback included; the notes schema makes no pills.
     - `SettingsEmailTemplatesPage.test.tsx`.
2. **The composer (#1250).**
   - `composer.tokens`, `SendMessageRequest.templateId`, the subject rendered, `OUTREACH_TOKENS_UNFILLED`, and
     `recentlyUsed`.
   - The picker, Insert, pills, the blocked Send, Save as template, and Insert follow-up through the insert.
   - Tests:
     - `OutreachOwnEmailIntegrationTest`: the composer read carries tokens; a blank is refused and named; a fallback is
       used; a subject's tokens are rendered; an unknown token and `{{opener}}` are refused; a foreign `templateId`
       is stored as null; deleting a template nulls `template_id`.
     - `ReplyComposer.test.tsx`: an empty body replaced and text inserted at the cursor; the subject only when empty; a
       reply's body only; the signature unchanged; a red pill holds Send; Save as template keeps the tokens.
   - `docs/validation-matrix.md` (tokens in an own email's subject, unfilled tokens) and
     `docs/outreach-conversations.md`.
3. **Sequence steps (#1251).** *As built:* `StepTemplatePicker` inline in the step (the mockup's panel), the replace
   question only where the step holds words, `stepTemplates.ts` (`stepFromTemplate`, `stepRefusalOf`,
   `templateRefusalOf`), and `plainBody` now written paragraphs apart (`NoteText.plainEmailTextOf`, a blank line between
   blocks) so a step reads as the template did. The copy closes in exactly one `{{signature}}` wherever the sequence
   signs any step (`sequenceSigns`), a step cleared before the copy included: since #1277/#1281 a step placing none goes
   unsigned, so a copy without it would unsign the step; a signature in the template's own words is dropped first
   (`withoutToken`, the engine's one grammar). A fallback is read without the Markdown escapes the notes editor's
   serializer adds (`{{currentTitle\|a \*senior\* leader}}` fills "a *senior* leader"), on both twins. Save as template leaves the step's
   `{{signature}}` out and writes the plain text through `plainEmailDoc` (a paragraph per blank line, a hard break per
   line). `renderParts` gained an `isFallback` part; `FilledVariable` draws a blank red and a fallback underlined in the
   template picker, the sequence preview and the review, which also flags each person "N blank" or "Fallback" across
   every step and says which under the email.
   - **From template** on each step: the template's `plainBody` (steps are plain text, so formatting is dropped, and
     the picker says so); the subject into step 1 only; it asks before replacing a step that has text; a body over a
     step's 5,000 characters is refused before it lands; it is a copy.
   - **Save as template** on a step: the step's subject (step 1 only) and body; the plain text becomes Markdown
     (`plainNoteDoc` through the email schema) with its tokens kept; a step using `{{opener}}` is refused with the
     reason.
   - The editor's preview and the Add to sequence review (`renderParts` gains the fallback and a blank part) mark a
     blank token red per person. Start is not blocked.
   - Tests:
     - `OutreachDispatchIntegrationTest`: a follow-up's fallback.
     - `SequenceEditorPage.test`: From template, a subject only on step 1, Save step as template.
     - `EnrolDialog.test`: a blank marked.

Later, not planned:
- usage and reply rate per template (`template_id` is recorded from Phase 2; no open tracking)
- a `/` or `;` shortcut in the body to search templates
- folders
- attachments on a template
- templates linked into sequence steps
- blocking a sequence Start on blank tokens
- admin-locked firm templates
- AI drafting a template
