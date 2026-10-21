# Outreach conversations — design and build plan

Status: **planned, not built** (approved 2026-10-21). This is the design the Outreach workspace in
`claude-design/Outreach.dc.html` is built from; each phase below is one PR. Until a phase ships, the tables, routes
and entities it names do not exist. The research and the decisions are kept here so the PRs can stay small.


## Context

The Outreach screen today is count tiles, sequence cards and a "People in outreach" table; everything about one
person is spread over the executive drawer (Outreach fold, Meetings section, Notes tab, Activity tab), and a
reply is only ever *noticed* — "Only who wrote and where is ever read — never what" (`OutreachInboxService`).
The new mockup (`claude-design/Outreach.dc.html`) turns the
page into a three-pane **conversation workspace**: an action queue on the left, one executive's communication
history and a reply composer in the middle, that executive's details on the right. The goal is one place where a
consultant sees everything that happened with a person on this position in time order — emails sent, replies,
bounces, sequence events, calls logged, notes, meetings — and acts from it: reply, send a first email, schedule,
start a sequence, book a call, log a call, add a note, mark the next action done. WhatsApp, SMS and phone calls
come later; the model must take them without changing its meaning.

How comparable tools do it (vendor-stated, from web research): Spott's **Unibox** keeps email, SMS, WhatsApp and
calls in one thread per candidate linked to candidate and job, with call transcripts via VoIP integrations; Gem's
**Messages** lists conversations left, thread right, a reply stops the sequence; Loxo, Bullhorn and Vincere get
calls/SMS/WhatsApp through Ringover/CloudCall-style add-ons that log into the record; Front and HubSpot separate
*conversation state* (open, snoozed, done, assigned) from the *contact timeline*; Twilio's Conversations model is
channel-agnostic (participants + messages carrying a channel). This plan takes the same shape: **one message
ledger with channel and direction, keyed on person × position, with the existing task rows as the "needs action"
state** — no new status machine.

## Decisions taken with the product owner (2026-10-21)

1. **Position-scoped**, stays at `/projects/:id/outreach`; every row carries person + position so a workspace-wide
   inbox can be added later without a migration of meaning.
2. **Email bodies are stored for threads Uncava started**: the sent body (already stored as HTML) and each reply's
   text, read from the consultant's mailbox only inside our own threads. Staff-only; never on `CandidateResponse`,
   the report, the public API, MCP, notifications or the AI readers.
3. **One-off sends are in scope**: a reply into an existing thread and a fresh first email, Send now or Schedule,
   through the same gateway and dispatcher.
4. **Future channels modelled, no provider built**: `channel` (EMAIL now; WHATSAPP, SMS, CALL reserved) and
   `direction` on every message; "Log call" is a note of kind `call` today.
5. **Placement**: sequences move to `/projects/:id/outreach/sequences` (reached from a menu on **New outreach**
   and from the right pane's **View sequence**); the mailbox is a compact chip in the tab row whose popover holds
   time zone, test email, Zoom and Disconnect.
6. **Queue groups**: Needs reply = the reply-rule task; Follow-ups = other open tasks; Preparation = an open task
   on someone never messaged on this position; Attention = the bounce-rule task or a run stopped on a send or
   mailbox failure. Mine = assigned to me; Team = anyone.
7. **Composer**: rich text (the notes editor, Tiptap → Markdown, rendered to HTML server-side) with the sequence
   token bar **and attachments** (a person's document or an uploaded file) through all three gateways.
8. **No mailbox connected is a first-class state** (asked mid-session): the page still opens, with a prominent
   banner and a disabled composer both offering **Connect your mailbox**, plus Reconnect variants.

## Existing seams this builds on (read these first)

| Seam | Path (under `apps/api/src/main/java/app/lightmove/api/` or `apps/web/src/`) |
|---|---|
| Message ledger (outbound only today) | `outreach/model/OutreachMessage.java`, `V101__outreach_send.sql` |
| Inbound handling, poll + webhook | `outreach/service/OutreachInboxService.java` (`pollListeningThreads`, `apply`, `answered`) |
| Sending: claim loop, re-checks, record | `outreach/service/OutreachDispatcher.java`, `OutreachSendService.java`, `outreach/repository/OutreachEnrollmentClaims.java` |
| Run outcomes → timeline, audit, events | `outreach/service/OutreachOutcomes.java` → `candidate/service/CandidateOutreachService.java` (`recordEmailSent`, `recordReplied`, status automation) |
| Gateways | `outreach/service/MailboxGateway.java`, `RoutingMailboxGateway`, `GoogleMailboxGateway` (`SCOPES`, `RawEmail`), `MicrosoftMailboxGateway` (draft → `createReply` → `/send`), `NylasMailboxGateway` (`/v3/grants/{g}/messages/send`, `reply_to_message_id`) |
| Email rendering | `outreach/model/OutreachEmailBody.htmlOf`, `OutreachPersonalisation` (tokens); `common/note/NoteText` (commonmark-java, already a dependency) |
| Tasks as next action | `task/service/TaskRuleService.java` (`"reply:" + enrollmentId`), `candidate/service/NextActionLookup` ⇐ `task/service/NextActionLookupAdapter` |
| Timeline read | `candidate/service/PersonTimelineService.java` (`DETAIL_KEYS` bug), `candidate/constant/PersonActivityKind.java` |
| Notes | `candidate/service/PersonNoteService` + `NoteViewpoint`; SPA `features/notes/**`, `features/candidates/components/PersonSections.tsx` (`NotesSection`, `TimelineFeed`) |
| Documents | `candidate/service/PersonDocumentService.open(StoredDocumentFile)`, `core/storage/service/DocumentStore` |
| Live | `core/stream/ProjectStreamKind` + `ProjectStreamPublisher`; SPA `features/triage/lib/useProjectStream.ts`, `features/notifications/lib/useNotificationStream.ts` |
| Pipeline facts | `candidate/controller/PositionPipelineController` (`client-visibility`), SPA `features/pipeline/lib/usePipelineWrites.ts` (`useClientVisibility`) |
| Drawer | `features/candidates/components/CandidateDrawerById.tsx`, `CandidateProfile.tsx`, `features/outreach/components/OutreachSection.tsx`, `MeetingsSection.tsx`, `EnrolDialog.tsx`, `BookCallDialog.tsx` |
| Tasks UI | `features/tasks/components/NextActionBar.tsx` (`useCandidateTasks`, `useTaskCompletion`), `NextActionModal.tsx` |
| Mailbox UI | `features/outreach/pages/OutreachPage.tsx` (connect card, banners, chip, `connectMailboxInPopup`, `MailboxTimeZoneSelect`, `ZoomConnectControl`), `lib/useMailbox.ts` |

Facts confirmed in code: next migration is **V144**; `lightmove.outreach.reply-listen-window` (30d) exists and the
poll already listens to `ACTIVE` and `COMPLETED` runs; the daily cap is `countBy…SentAtGreaterThanEqual` over the
message table; the reply task is keyed `"reply:" + enrollmentId`; `MailboxConnection` stores no granted scopes;
`PersonTimelineService.DETAIL_KEYS` lacks `sequence`, `sequenceId`, `step`, `reason`, `startsAt`, `viaLink`
(the writers put exactly those in, the SPA's `candidateActivity.ts` expects them); there is no ArchUnit test — the
`candidate ← outreach ← task` direction is a convention, so outreach reaches tasks only through `candidate`'s
`NextActionLookup` and tasks hear outreach only through events; person merge (Phase 4b-2) is not built.

## A. Data model

**Widen `app_lm_outreach_message` in place (V144), expand-only.** It already *is* the per-email ledger the daily
cap, the bounce lookup and the drawer's step list read; a second table would turn every "latest message / sent
today / thread of this person" read into a UNION. V91's frozen-copy idiom was for re-keying ownership; here only
nullable or defaulted columns are added, so the previous revision's `OutreachMessage.sent(...)` keeps working during
the deploy window.

`V144__outreach_message_ledger.sql`:
- Relax `NOT NULL` on `enrollment_id`, `step`, `provider_message_id` (a scheduled row has none yet), `subject`,
  `body` (a bounce has none), `sent_at` (**outbound SENT only — it is the cap's column**).
- Add: `direction` (`OUTBOUND|INBOUND`, default OUTBOUND), `channel` (`EMAIL|WHATSAPP|SMS|CALL`, default EMAIL),
  `kind` (`SEQUENCE_STEP|MANUAL|REPLY|BOUNCE`, default SEQUENCE_STEP), `status`
  (`SCHEDULED|SENT|FAILED|CANCELLED|RECEIVED`, default SENT), `project_id` (FK cascade), `person_id` (FK cascade),
  `candidate_id` (FK **SET NULL** — unmapping keeps the record, the enrollment's rule), `from_address`,
  `to_address`, `thread_gateway` (`NYLAS|DIRECT`), `in_reply_to`, `occurred_at` (the ordering instant: sent,
  received or due), `send_at` (a SCHEDULED row's pinned time), `sending_since` (the claim), `fail_reason`
  (`OutreachStopReason` names), `body_format` (`PLAIN|MARKDOWN`, V142's idiom; the composer writes MARKDOWN),
  `body_text` (plain text ≤ 20,000: a reply's stripped text, or the outbound's source).
- Backfill `project_id, person_id, candidate_id, to_address, thread_gateway` from the enrollment, `from_address`
  from the sender's mailbox (null where gone), `occurred_at = sent_at`, `body_text` from `body` with tags
  stripped; then `SET NOT NULL` on `project_id`, `person_id`, `occurred_at`.
- Drop `app_lm_outreach_message_step_uk`; add `UNIQUE (enrollment_id, step) WHERE kind = 'SEQUENCE_STEP'`.
- CHECKs: `status <> 'SENT' OR sent_at IS NOT NULL`; `direction = 'OUTBOUND' OR sent_at IS NULL` (a reply can never
  count against the cap); `char_length(body_text) <= 20000`.
- Indexes: `(workspace_id, project_id, person_id, occurred_at DESC)`; `(send_at) WHERE status = 'SCHEDULED'`;
  `(workspace_id, sender_user_id, thread_id) WHERE thread_id IS NOT NULL`; `UNIQUE (workspace_id, sender_user_id,
  provider_message_id) WHERE provider_message_id IS NOT NULL` (reply/bounce dedupe); `(sender_user_id,
  occurred_at) WHERE direction = 'OUTBOUND' AND status = 'SENT' AND thread_id IS NOT NULL` (the listening scan).
- Table COMMENT rewritten: a reply *is* a row since V144, text only, staff-only.

`V145__mailbox_reads_replies.sql`: `app_lm_mailbox_connection.reads_replies boolean NOT NULL DEFAULT false`,
`granted_scopes text`; backfill `true` for direct Microsoft rows (Mail.ReadWrite already covers it).

`V146__outreach_attachment.sql`: `app_lm_outreach_attachment` — `message_id` (FK cascade), `workspace_id`,
`file_name`, `content_type`, `size_bytes`, `sha256`, `document_version_id` (nullable FK SET NULL to
`app_lm_person_document_version` — a CV sent from the person's documents), `storage_key` (the bucket key; for a
person's document the version's own key). **No bytes in the database** (V105's rule).

Entity `OutreachMessage`: drop `updatable = false` from the columns a scheduled row later fills; factories
`sent(...)` (unchanged signature, sets the new columns), `scheduled(...)` (MANUAL), `received(...)` (REPLY),
`bounced(...)` (BOUNCE). New enums under `outreach/constant/`: `MessageDirection`, `MessageChannel`,
`MessageKind`, `MessageStatus`. New entity `OutreachAttachment`.

**Where a thread is identified**: on the message row, `(workspace_id, sender_user_id, thread_id)` +
`thread_gateway` — the key `OutreachEnrollmentRepository.findByWorkspaceIdAndSenderUserIdAndThreadId` already uses
(a thread id is unique only within one mailbox). `OutreachInboxService` resolves an inbound thread **messages
first** → `(projectId, personId, candidateId, enrollmentId?)`; the enrollment lookup stays as the fallback for the
"sent but not recorded" path (`OutreachEnrollmentClaims.recordDelivered`).

**Which threads listen**: every distinct `(workspace, sender, thread, gateway)` with an `OUTBOUND SENT` row within
`reply-listen-window` (reuse the existing 30d setting; no new key), **plus** every live enrollment's thread whatever
its age. The poll's `since` becomes the newest `occurred_at` we hold in that thread minus one hour (not
`enrolled_at`); the dedupe index absorbs the overlap. A webhook `InboundMessage` is accepted for any thread the
message table knows, window or not.

**Reply storage** — new `MailboxGateway.messagesInThread(grantId, threadId, since) -> List<ThreadMessage>`
(`providerMessageId, fromAddress, receivedAt, textBody, isOwn`); `senderAddressesInThread` stays for mailboxes that
cannot read bodies, and `OutreachInboxService` branches once on `mailbox.readsReplies()`:
- Google: thread metadata as today to find new ids, then `messages.get?format=full` per new id, `text/plain` part
  else flattened `text/html`. `GoogleMailboxGateway.SCOPES`: `gmail.metadata` → `gmail.readonly` (both restricted
  scopes; same verification tier, but **existing direct Google mailboxes must reconnect**).
- Microsoft: the existing `conversationId` read with `$select=…,uniqueBody` and `Prefer:
  outlook.body-content-type="text"` — `uniqueBody` is already the unquoted part. No scope change.
- Nylas: `GET /v3/grants/{g}/messages?thread_id=&received_after=&select=id,from,date,body`. Whether the Nylas
  app's provider scopes include read is not discoverable per grant → `lightmove.outreach.nylas.reads-replies`
  (default false) decides; a 4xx falls back to addresses, logged once per thread, never stopping the run.
- Test double `apps/api/src/test/java/app/lightmove/api/RecordingMailboxGateway.java` gains `writeInto(threadId,
  from, text)` and `messagesInThread`.
- Quoted history is **stripped** (`outreach/model/ReplyText`: cut at the first of `^On .* wrote:`, `-----Original
  Message-----`, `^From: `, a `> ` block, `gmail_quote`; if nothing is left, keep the whole), capped at 20,000.
- Dedupe on the unique index; the insert catches the violation and skips.
- Mailbox capability: `GrantedMailbox` gains `scopes` (Google and Microsoft return `scope` with the token; Nylas
  null); `MailboxConnection.connected/reconnect` set `reads_replies` from them (Google `gmail.readonly`, Microsoft
  `Mail.Read*`, Nylas the setting); a consent where the user unticked the scope is `false`, not an error.
  `ConnectedMailboxResponse` gains `readsReplies`, `reconnectToReadReplies` and `sentToday` (for the chip, counted
  in the mailbox zone with the cap query).

**Bounce rows: yes** — `INBOUND / BOUNCE / RECEIVED`, `from_address` the daemon, `body_text` null (no DSN
parsing), so one source draws "Email bounced" and feeds Attention. `OutreachOutcomes.bounced` is unchanged.

**Channel seam (named, not built)**: `MessageChannelGateway` beside `MailboxGateway` (`send(ChannelAddress,
OutgoingMessage)`, `readWebhook`) and a `RoutingMessageGateway` keyed on `channel`; the dispatcher's claim loop is
already channel-agnostic because it reads the message table. A Twilio Conversations/Voice adapter would implement it;
a call becomes a `CALL` row with duration/outcome in details. Written down in `docs/outreach-conversations.md`.

## B. One-off sends and attachments

**Route** `POST /api/v1/projects/{p}/outreach/candidates/{c}/messages` (new
`outreach/controller/OutreachConversationController`, `@RequireProjectPermission(WORK_EXECUTE)`) → 201
`MessageResponse`. `SendMessageRequest(toAddress, subject [≤200, one line; required for a fresh thread], body
[Markdown, 1–10,000, tokens allowed], sendAt [null = now; else > now and ≤ 60 days], replyToThread,
attachments[] {documentVersionId} | {uploadId})`. Uploads go first to `POST …/messages/uploads` (multipart, the
`DocumentFormat` name/bytes agreement, held under a storage key for an hour until a send claims them).

**Rendering**: new `outreach/model/OutreachMarkdownEmail` — tokens substituted by `OutreachPersonalisation`, then
commonmark-java `HtmlRenderer` with the same link allowlist `common/note`'s reader applies (http, https, mailto),
`++underline++` as the notes do; `escapeHtml(true)` and `sanitizeUrls(true)` (a raw HTML block is text, never markup), mention tokens stripped first
(`@[Name](user:…)` must never reach an executive); `body` = the HTML as sent, `body_text` = `NoteText.plainTextOf`.
Note `NoteText` itself renders Markdown to *plain text* only — nothing in the API renders Markdown to HTML today, so
the `HtmlRenderer` is new. Sequence steps keep
`OutreachEmailBody.htmlOf` (paragraph text) — two renderers, one per authoring model, both staff-reviewed.

**Service** `outreach/service/OutreachMessageService.schedule(...)`: requires the caller's own mailbox `canSend`
(`MAILBOX_NOT_CONNECTED`), the position live (`PROJECT_ARCHIVED`), the recipient through
`CandidateOutreachService.currentRecipient` and the `refusalOf` rules extracted from `OutreachSendService` into
`OutreachEligibility` — do-not-contact (409 `DO_NOT_CONTACT`), the address on the ledger (`ADDRESS_NOT_ON_LEDGER`),
Send now refused when today's cap is reached (`OUTREACH_DAILY_CAP_REACHED`; a scheduled one defers like a
sequence). Threading when `replyToThread`: the newest row of `(workspace, project, person, sender)` with a
`thread_id` and `thread_gateway == mailbox.gateway` → same `thread_id`, `in_reply_to` = the newest message in that
thread **inbound included** (Google reads its `Message-ID`/`References`; Graph `createReply` on it with recipients
forced to the executive; Nylas `reply_to_message_id`), subject default `replySubjectOf(firstSubject)`; a thread made
by the other gateway falls back to a fresh thread with a notice (`MAILBOX_MOVED` otherwise). Row saved `SCHEDULED`,
`send_at = sendAt ?? now`. **A manual send into a live run's thread stops the run `MANUAL`** in the same
transaction (a canned follow-up must not land after a human took the thread over; the composer says so).

**Dispatch**: `outreach/repository/OutreachMessageClaims` — `OutreachEnrollmentClaims`' SQL verbatim against the
message table (`status = 'SCHEDULED' AND send_at <= ? AND sending_since IS NULL … FOR UPDATE SKIP LOCKED`).
`OutreachDispatcher.dispatchAt` gains a second loop (`stopUncertainClaims` → `FAILED/SEND_UNCERTAIN`, `claimDue`,
`sendClaimed`). `sendClaimed` mirrors `OutreachSendService`'s three-transaction shape: prepare (re-check mailbox,
archive, recipient, DNC, ledger, gateway; cap → defer `send_at` to the next opening in the mailbox zone; a manual
message is **pinned**, never held by the window), `gateway.send`, record (`SENT`, ids, `sent_at = occurred_at`,
the optimistic-lock retry and `recordDelivered` last resort). Never retried; `ProviderAppUnavailable` and
rate limits defer as today.

**Attachments through the gateways** (`OutgoingEmail` gains `List<Attachment(fileName, contentType, bytes)>`;
bytes read at send time from `DocumentStore` by `storage_key`, never held on the row): Gmail `RawEmail` becomes
`multipart/mixed` (HTML part + base64 file parts); Graph `POST /me/messages/{id}/attachments` with
`#microsoft.graph.fileAttachment` on the draft before `/send`; Nylas `attachments[]` base64 in the send body.
Cap `lightmove.outreach.attachment-max-bytes` (3 MB per email — the inline limit all three share; refuse above
with `ATTACHMENT_TOO_LARGE`; Graph's upload session is not in this epic). Sending a person's document out is a
disclosure: audited like a download (`PERSON_DOCUMENT_SENT`, with the message id), and the conversation card lists
the attachment names.

**Status effects on record**: `CandidateOutreachService.recordEmailSent` gains an overload without a sequence,
writing `EMAIL_SENT` with details `{manual: true, messageId}` and running the same `EMAIL_SENT` status automation
(Identified → Contacted). **No new activity kind** (no CHECK migration; `candidateActivity.ts` phrases "sent an
email" when `sequence` is absent).

**"Sending resolves this reply task"**: the record transaction publishes `OutreachReplySent(workspaceId,
projectId, personId, candidateId, senderUserId, messageId)`; `task` listens (`AFTER_COMMIT` + `REQUIRES_NEW` beside
`TaskRuleService.onReplied`) and completes every **open task with `rule = REPLY`** on that (project, person) —
never a manual task — `completed_by = sender`, `TASK_COMPLETED` line, audit. Direction stays `task → outreach`.

**Cancel** `DELETE …/outreach/messages/{id}`: only `SCHEDULED` with `sending_since IS NULL`
(`MESSAGE_ALREADY_SENDING`) → `CANCELLED` (row kept, hidden from reads). `PositionArchiveOutreach` also cancels
the position's scheduled messages; the Disconnect confirm counts them.

**Reply task key** (Phase 2): `OutreachReplied` carries `messageId` and a nullable `enrollmentId`;
`TaskRuleService` keys `"reply:" + messageId` and skips when an open REPLY task already exists for the person on the
position — or three quick replies file three tasks.

Audit (`core/audit/constant/ProjectEventType`): `OUTREACH_MESSAGE_SCHEDULED / SENT / FAILED / CANCELLED`,
`OUTREACH_REPLY_STORED` (details `messageId`, `threadKnownBy`), `PERSON_DOCUMENT_SENT`. Error codes (`ErrorCode` +
`apps/web/src/lib/errorCodes.ts`, the coverage test fails otherwise): `OUTREACH_DAILY_CAP_REACHED`,
`ADDRESS_NOT_ON_LEDGER`, `MESSAGE_ALREADY_SENDING`, `MESSAGE_NOT_SCHEDULED`, `ATTACHMENT_TOO_LARGE`,
`MAILBOX_NOT_CONNECTED`.

## C. The reads

**`GET /projects/{p}/outreach/conversations?view=needs|all|scheduled|sent&who=mine|team&q=&before=&limit=`**
→ `ConversationListResponse(counts{needs, scheduled}, nextScheduledAt, rows[], nextCursor)`;
`ConversationRowResponse(candidateId, personId, fullName, title, companyName, enrichedAt, status, statusLabel,
statusColour, pipelineStage, group, action{taskId, text, kind, dueAt, assigneeUserId, assigneeName, rule},
latest{channel, direction, kind, at, preview ≤140}, scheduledAt, lastSentAt)`.

Groups, in `outreach/service/ConversationService`, from the executive's open tasks and message rows:
- `NEEDS_REPLY`: earliest open task has `rule = REPLY`; **or** no open task but the latest message is an inbound
  REPLY newer than the latest outbound SENT (the workspace switched the reply rule off — the reply must still show).
- `ATTENTION`: an open task with `rule = BOUNCE`; or the latest message is a BOUNCE or a FAILED outbound; or the
  latest run is `BOUNCED` / stopped `SEND_FAILED | SEND_UNCERTAIN | MAILBOX_INACTIVE | MAILBOX_MOVED |
  ADDRESS_REMOVED` with no later SENT.
- `PREPARATION`: an open task and **no outbound SENT ever** for this person on this position.
- `FOLLOW_UP`: any other open task. `null`: nothing open — absent from `needs`, present in `all`.
- Precedence ATTENTION > NEEDS_REPLY > PREPARATION > FOLLOW_UP. Sort ("Priority"): overdue first by `dueAt`, then
  `latest.at` desc. `who=mine`: in `needs` the task is mine; elsewhere `sender_user_id = me`.
- Views: `all` = anyone with a message row, a run or a meeting on the position; `scheduled` = a SCHEDULED row or a
  live run's `next_send_at`, by time asc; `sent` = ≥1 outbound SENT, by `lastSentAt` desc. `needs` returned
  whole (bounded by open tasks); the others paged `before=&limit=` (50, max 100).

Dependencies keep `candidate ← outreach ← task`: executives through a new
`CandidateOutreachService.conversationSubjectsOf(workspace, project, q)` (name/title/company `ILIKE`); open tasks
through `NextActionLookup.openTasksOf(projectId, candidateIds)` (new, in `task`'s adapter; today's
`CandidateNextActionDto` lacks `rule` and `assigneeUserId`); messages through new JPQL on
`OutreachMessageRepository` (`latestPerPerson`, `scheduledPerPerson`, `hasSentPerPerson`, `searchIds` on
`subject ILIKE OR body_text ILIKE`). **No full-text index**: a position holds hundreds of people and a few
thousand rows, the project index bounds the scan before `ILIKE` runs; `pg_trgm` is a later migration if measured
slow. `q` ≤ 100 chars, `%`/`_` escaped.

**`GET …/outreach/conversations/{candidateId}?before=&limit=`** → `ConversationResponse(person, stage,
statusLabel/colour, clientVisible, contact{emails[], phones[], location} (the ledger as `ContactPanel` reads it; no
time-zone row — nothing stores one), keyDates{addedAt, firstContactedAt, repliedAt, lastUpdatedAt}, currentRun
(reuse `OutreachMonitorService.ofCandidate`), openTask, emailSentMovesTo{statusLabel}|null (from
`StatusAutomationService.targetOf`, for the composer's footnote), mailbox{readsReplies}, events[], nextCursor)`.

`events` — **one `ConversationService` composing four sources** (the activity table never holds a body; notes and
meetings are their own tables), merged in memory by instant desc, cut at `limit`, each source asked `< before`:
1. message rows (SENT → "sent email" with `body`; REPLY → "synced email" with `body_text`; BOUNCE / FAILED →
   "delivery"; SCHEDULED → a scheduled card with Cancel), context "{sequence} · Step n of 3" or "One-off",
   attachments by name;
2. run and status events from `PersonTimelineService.linesOf(workspace, person, project, kinds, before, limit)` —
   `OUTREACH_ENROLLED, OUTREACH_STOPPED, MEETING_BOOKED, STATUS_CHANGED, TASK_COMPLETED` (`EMAIL_SENT`/`EMAIL_REPLIED`
   excluded: the message rows say it) → "automation" cards; "Sequence ended on reply" is drawn from the run's
   `status = REPLIED` + `replied_at` (a reply writes no stop line today);
3. notes via `PersonNoteService.list(…, NoteViewpoint.position(projectId))` — this position's + All positions,
   staff — "human note" cards (`kind = CALL` → "Call note added by …");
4. meetings via `PersonMeetingRepository` for the person.

Fix `PersonTimelineService.DETAIL_KEYS` on the way: add `sequence, sequenceId, step, reason, startsAt, viaLink,
manual, messageId`, with a regression test that an `EMAIL_SENT` line answers `details.sequence` and `step`.

## D. Live refresh

`ProjectStreamKind` gains `OUTREACH` and `TASK`; published (inside the recording transactions —
`ProjectStreamPublisher` is `MANDATORY`) from `OutreachMessageService`, `OutreachInboxService`, `OutreachOutcomes`,
`TaskService` and `TaskRuleService.file`. SPA: move `features/triage/lib/useProjectStream.ts` to
`lib/useProjectStream.ts`, extend the union; `OutreachPage` invalidates `["outreach", projectId]` (and
`TASKS_KEY_PREFIX` on `task`), polling 60 s only while the stream is not live; `useNotificationStream` also
invalidates `["outreach"]`. Ship the enum and the TS union in the same PR (an unknown kind refetches everything).

## E. The SPA

- **Route**: `/projects/:projectId/outreach` wrapped in `RequireStaff` (every read is `WORK_EXECUTE`; the
  `NotYetBuilt` client branch goes); new `/projects/:projectId/outreach/sequences` → `SequencesPage`; the editor
  route unchanged.
- **Layout**: a CSS grid in the page, no `SplitPane` primitive (nothing resizes; one consumer). Full-height column
  (tab row + `min-h-0 flex-1` grid). `xl:grid-cols-[clamp(260px,24vw,336px)_minmax(420px,1fr)_clamp(260px,22vw,320px)]`;
  at `lg` two columns with the details pane behind the header's "⋯" in the existing `Drawer`; below `lg` one pane
  at a time (queue, or conversation when `?candidate=` is set, with a back control). Check `ProjectLayout` lets a
  child own the scroll (`min-h-0` chain) and add a full-bleed outlet option if it pads and scrolls.
- **URL state** (`useSearchParams`, as `PipelinePage`): `?view=needs|all|scheduled|sent&who=mine|team&candidate=&q=`;
  defaults `needs`, `mine`; `q` debounced 250 ms; the first row auto-selects and is written to the URL.
- **Components** (`features/outreach/components/`, API `features/outreach/api/conversationApi.ts`, keys
  `["outreach", projectId, "conversations", …]` / `["outreach", projectId, "conversation", candidateId]`):
  `OutreachTabs` (`TabList` + counts; right: `MailboxChip`, `NewOutreachMenu` — a `Popover`: "Add to sequence" →
  `EnrolDialog`, "Sequences" → the list page); `ActionQueue` + `QueueRow` (search, `SegmentedControl` Mine|Team,
  groups with counts, avatar/name/relative time/role/dot + action sentence/preview, footer "{n} scheduled ·
  {when}" → `?view=scheduled`; `isError` before empty); `ConversationPane` → `ConversationHeader` (name, stage pill,
  role; **Log call**, **Add note**, **Mark handled**), `NextActionStrip` (reuses `useCandidateTasks` /
  `useTaskCompletion`, opens `NextActionModal`), `ConversationHistory` + `ConversationEventCard` (type tags exactly
  the mockup's: `SENT EMAIL`, `SYNCED EMAIL`, `AUTOMATION`, `HUMAN NOTE`, `DELIVERY`; a reply body rendered as
  pre-wrapped text, never HTML; a scheduled card with Cancel), `ReplyComposer`; `CandidateDetailsPane`;
  `MailboxChip`; `MailboxBanner`; `NoteComposerModal`; `SequencesPage`.
- **`ReplyComposer`**: the notes editor (`features/notes/components/NoteEditor.tsx`, Markdown out, no mentions)
  with the sequence token bar (`lib/sequenceTokens.ts`); subject row (read-only "Re: …" in a thread, an input for a
  fresh one); "From {address}"; **Attach** (a popover: the person's documents from `…/documents`, or Upload →
  the uploads route; chips with size; the 3 MB total shown); **Insert follow-up** (the position's sequence steps,
  rendered); **Schedule** (`Popover` with `DateInput` + time → an instant, pinned); **Send now**. "Saved template"
  is not drawn (nothing to pick from yet; README says so). Footnote composed client-side: "Sending resolves this
  reply task." when `openTask.rule === "reply"`; "Pipeline stage stays {label}." vs "Moves to {target}." from
  `emailSentMovesTo`; "Sending stops the running sequence." when `currentRun` is live; "Sends within a minute."
  on Send now. Disabled with a reason: no mailbox, do-not-contact, no email on the ledger, archived position; a
  cap refusal is the 409 toast.
- **`CandidateDetailsPane`**: avatar/name/role; stage pill; **Client visible** `role="switch"` via
  `useClientVisibility` (invalidate pipeline and outreach keys) with the mockup's caption; Contact details as a
  read-only `DetailList` (Email, Mobile, Location); Key dates; Current sequence (name, n / 3, three bars, send
  status from `lib/runVocabulary.ts`, **View sequence** → editor, **View history** → the drawer's Activity tab);
  **Open persistent candidate record** → `CandidateDrawerById` in place (portalled; navigating away would lose the
  queue).
- **Mailbox states** — a pure `features/outreach/lib/mailboxState.ts` (`mailboxStateOf(useMailbox())` → `loading |
  refused | unavailable | none | error | movesOffNylas | readScopeMissing | ready`, precedence `error` >
  `movesOffNylas` > `readScopeMissing` > `ready`, unit-tested per branch) that `MailboxBanner` under the tab row,
  `MailboxChip` and the composer's `ComposerLockedCard` all switch on; the connect popup plumbing lifts out of
  `OutreachPage.tsx` into `lib/useMailboxConnect.ts`, shared by all three. `readScopeMissing` keeps the live composer
  (sending works; only the reply text is missing):
  `notOffered` (Uncava support turns it on), `none` → "Connect your mailbox to send and receive email from Uncava"
  with one button per provider (`useMailbox().offered.providers`, `connectMailboxInPopup`), `error` → Reconnect,
  `reconnectToReadReplies` (a Google grant without the read scope: replies are still detected, their text is not
  shown), `movesOffNylas` (the existing prompt), `ok` (no banner). The connect copy changes from "It never keeps
  what they wrote" to "Uncava keeps the emails you send from here and the replies to them, for your team only."
  With a mailbox but no conversation yet: `EmptyState` pointing at New outreach and the Pipeline.
- **`MailboxChip`**: dot + address + "12 of 50 today"; popover holds `MailboxTimeZoneSelect`, Send a test email,
  `ZoomConnectControl`, Disconnect (`ConfirmDialog` with live counts + scheduled messages) and the reconnect
  prompts. With no mailbox the chip reads "Connect mailbox".
- Log call → `NoteComposerModal` wrapping `NotesSection`'s composer with new `initialKind` / `onSaved` props
  (`PersonSections.tsx`); Add note → `general`. Mark handled → `useTaskCompletion` on `openTask.taskId`, disabled
  with a tooltip when none.
- Mockup: copy the uploaded file over `claude-design/Outreach.dc.html`; update `claude-design/README.md`'s row
  (the workspace's panes, the `?view=` knob, what is not drawn: formatting icons beyond the editor's, Saved
  template). `docs/glossary.md`: add Conversation, Action queue, Needs reply, Synced email (the mockup's words).

## F. Drawer parity (minimal)

`OutreachSection.tsx`: an "Open conversation" link to `/projects/{p}/outreach?candidate={c}` in the fold header,
and the latest reply's text (first 300 chars) inside the existing "replied" notice; `CandidateOutreachResponse`
gains `latestReply{at, text}|null` from `OutreachMonitorService.ofCandidate`.

## G. Security and privacy

Every new route `@RequireProjectPermission(WORK_EXECUTE)`; `messages/{id}` and uploads re-read the workspace and
project; every repository query carries `workspaceId`. Reply text appears only in `ConversationResponse` and
`latestReply` — never on `CandidateResponse`, the report, `publicapi`, `mcp`, a notification row ("{name}
replied", no excerpt) or `CandidateMaterialReader`; AI reading the conversation is **not in this epic** (when it
comes, through `ContactRedaction`). Activity details carry `messageId`, never a subject or body. Logs never print
`body_text`. Retention: person and workspace cascades take the text; `candidate_id SET NULL` keeps the approach on
unmap; uploaded attachment bytes are deleted with the message's cascade by a purge like `PersonNotePurge`. When
person merge is built, message and attachment rows move with the enrollments.

## H. Phases (one PR each, each shippable)

1. **Ledger + conversation reads (no UI change)** — V144; `OutreachMessage` + enums; `OutreachMessageRepository`
   queries; `OutreachSendService.recordSent` sets the new columns; `NextActionLookup.openTasksOf` +
   `NextActionLookupAdapter`, `OpenTaskDto`; `CandidateOutreachService.conversationSubjectsOf`;
   `PersonTimelineService` (`DETAIL_KEYS` fix, `linesOf`); `ConversationService`, `OutreachConversationController`,
   DTOs. Tests: new `OutreachConversationIntegrationTest` (`@IntegrationTest`, the dispatch test's helpers):
   grouping per rule, Mine/Team, search, client seat 403, foreign position 404, event merge order, `EMAIL_SENT`
   now carries `sequence`/`step`. Verify `./mvnw test`.
2. **Reply bodies, bounce rows, listening by thread** — V145; `GrantedMailbox.scopes`, `MailboxConnection`,
   `ConnectedMailboxResponse`, `MailboxService`; `MailboxGateway.messagesInThread` + `ThreadMessage`; the three
   gateways + `Routing`/`Unconfigured`/`Recording`; `ReplyText`; `OutreachInboxService` (message-first resolution,
   thread scan, REPLY/BOUNCE rows, dedupe, replies on ended runs and manual threads, no double `replied()`, no
   resurrecting a STOPPED run); `OutreachReplied` (+`messageId`); `TaskRuleService` key change; settings; SPA
   `mailboxApi` types + the "Reconnect to read replies" prompt on the current page. Tests: gateway tests with
   recorded bodies, `ReplyTextTest`, dispatch integration cases (stored text stripped, duplicate webhook once,
   reply after run ended files one task, two quick replies one task, bounce row, no-read-scope mailbox still stops
   by address). Manual: a real Google reconnect in `npm run dev` shows the new scope; add it to
   `docs/integrations/registration.md`.
3. **One-off sends (Markdown body), schedule, cancel, task resolution** — `OutreachMessageClaims`,
   `OutreachMessageService`, `OutreachEligibility`, `OutreachMarkdownEmail`, `OutreachDispatcher` second loop,
   POST/DELETE routes; `OutreachReplySent` + listener in `task`; `recordEmailSent` overload; `PositionArchiveOutreach`;
   `MailboxService` disconnect counts; `ErrorCode` + `errorCodes.ts`; `docs/validation-matrix.md` rows. Tests: new
   `OutreachManualSendIntegrationTest` (Send now outside the window still goes, threads on the reply's id, scheduled
   waits pinned, cancel before/after claim, cap refuses now / defers scheduled, DNC refused at POST and at send,
   archive cancels, a reply send completes the REPLY task only, first contact Identified → Contacted, a send in a
   live thread stops it MANUAL, two dispatchers send once, a mention never reaches the HTML).
4. **The screen** (Attach control absent until phase 5) — routes, `OutreachPage` rewrite, `SequencesPage`, `conversationApi.ts`, the components in E,
   `PersonSections.tsx` props, mockup swap + README, glossary. Tests: `OutreachPage.test.tsx` (tabs, URL state,
   auto-select, 403 shows no counts, each `mailboxState`), `ActionQueue.test.tsx`, `ReplyComposer.test.tsx`
   (payloads, footnotes, disabled reasons, attachment cap), `CandidateDetailsPane.test.tsx`, `MailboxChip.test.tsx`;
   `copyGuard`, `styleRatchet`, `node e2e/spa/responsive.mjs` with the route added. Verify `npm run build`, then
   `/verify`: connect mailbox → New outreach → a reply arrives → Needs reply → Send now → task gone.
5. **Attachments** — V146; `OutreachMessageAttachment` entity/repository; `OutreachAttachmentSettings` (`max-files` 5,
   `max-total-bytes` 3 MB); `OutgoingEmail.attachments` + `EmailAttachment` (`toString` omits bytes); `RawEmail`
   `multipart/mixed` (boundary, RFC 2231 `filename*`, 76-col base64, CRLF); Graph `fileAttachment` posts before `/send`
   (a failed post deletes the draft); Nylas `attachments[]`; the POST becomes `multipart/form-data` (`message` JSON part +
   `files[]`, `documentVersionIds`); the attachment download route (`PERSON_DOCUMENT_DOWNLOADED` /
   `OUTREACH_ATTACHMENT_DOWNLOADED`); `attachableDocuments` on the conversation read; `PERSON_DOCUMENT_SENT`; the SPA
   Attach control + chips; validation-matrix rows. Tests: `RawEmailTest` multipart, the Graph and Nylas gateway tests,
   integration (a person document sent audits the disclosure, a name/bytes mismatch refused, 3 MB + 1 refused, cancel
   deletes the bytes). Verify `./mvnw test`, `npm run build`, a real send from each provider in `npm run dev`.
6. **Live refresh, drawer parity, docs** — stream kinds + publish sites, `useProjectStream` move, subscriptions;
   `OutreachSection` link + `latestReply`; `docs/outreach-conversations.md` (tables, routes, groups, listening
   rule, channel seam, what AI never reads), `docs/help/outreach-conversations.md`, `docs/tasks-notifications.md`
   (reply key, resolution on send), `CLAUDE.md` outreach paragraph + V144–V146 lines, `e2e/api/21-outreach-
   conversations.sh` (reads, a note, a client-seat 403; sends unreachable without a gateway). Verify
   `cd e2e && PROFILE=e2e ./run-all.sh`.

## I. Traps

1. A send is never retried — the manual path keeps the three-transaction shape; FAILED / SEND_UNCERTAIN shows in
   Attention, no Retry button.
2. The claim idiom: `sending_since` committed before the gateway call, `version` bumped in SQL, the
   optimistic-lock second attempt, `recordDelivered` last resort; `stopUncertainClaims` for messages too; the claim
   lives in the repository bean (a `@Scheduled` self-call is not proxied).
3. The daily cap counts `sent_at` — inbound rows keep it null (V144's CHECK).
4. `(enrollment_id, step)` unique, the `step` CHECK and four NOT NULLs block the new rows until relaxed; the
   drawer's `findBy…EnrollmentIdIn…` must filter `kind = SEQUENCE_STEP`.
5. `OutreachMessage` is `updatable = false` throughout — a scheduled row later sent needs updatable columns.
6. Reply task key collides on a null enrollment — key on `messageId`, dedupe on an open REPLY task.
7. A reply for an ended run: store the row and file one task; never call `enrollment.replied()` twice or resurrect
   a STOPPED run.
8. A manual reply after do-not-contact: refused at POST and at send.
9. Google: `gmail.readonly` needs a reconnect with `prompt=consent`; same-mailbox reconnect drops the old token
   without revoking (keep); an unticked scope is `reads_replies = false`; the shared app's console entry must list
   the scope before deploy.
10. Nylas without read scope: gate on the setting, fall back to addresses, log once per thread.
11. `MAILBOX_MOVED`: a reply into the other gateway's thread cannot thread — fresh thread with a notice at POST,
    FAIL at send.
12. `copyGuard` / glossary: no "Nylas", "triage", "this deployment"; the old "never keeps what they wrote" sentence
    becomes false; `errorCodes.coverage.test.ts` needs every new code.
13. `NoteViewpoint`: never read notes by person alone from this page.
14. Archived position: scheduled messages cancelled; Send/Schedule disabled under the banner.
15. Send now is pinned but the cap still holds it; the dispatcher runs every minute, so "within a minute".
16. `ProjectStreamPublisher` is `MANDATORY`; `AFTER_COMMIT` listeners need their own `REQUIRES_NEW`.
17. The e2e matrix (`PROFILE=e2e`) has no mail gateway: reads, notes and permissions only; sends and replies stay
    in `@IntegrationTest` with `RecordingMailboxGateway` (never `@Primary`).
18. Attachments: 3 MB inline limit shared by all three gateways; bytes read at send time, never on the row; a
    person's document send is a disclosure and is audited; uploaded bytes purged with the message.
19. `twMerge` per breakpoint: pane widths live in the page grid; the details-pane `Drawer` below `xl` gets the
    same `lg:` width override.
20. Markdown email: links limited to http/https/mailto as the notes reader does; tokens substituted before
    rendering so a `{{bookingLink}}` is a real anchor and nothing a consultant types becomes markup.
21. Reply rule switched off: by decision 6 the reply then shows in All conversations and the bell, not in Needs
    action; say so in the docs and in the Settings → Notifications rule's helper text so nobody files it as a bug.
22. Graph drafts: an attachment post that fails after the draft exists must delete the draft, as a refused send does,
    or a half-built message sits in Drafts one click from a send.
