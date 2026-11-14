import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, DateInput, Select, useToast } from "../../../components/ui";
import { Popover } from "../../../components/ui/Popover";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import * as emailTemplatesApi from "../../emailtemplates/api/emailTemplatesApi";
import type { EmailTemplate } from "../../emailtemplates/api/emailTemplatesApi";
import { SaveAsTemplateDialog } from "../../emailtemplates/components/SaveAsTemplateDialog";
import { TemplatePicker } from "../../emailtemplates/components/TemplatePicker";
import { templateVocabulary } from "../../emailtemplates/lib/templateVariables";
import { NoteEditor, type NoteEditorHandle } from "../../notes/components/NoteEditor";
import { parseNoteMarkdown, plainNoteDoc, serializeNoteDoc } from "../../notes/lib/noteMarkdown";
import { PrivacyFooterNote } from "./PrivacyFooterPreview";
import { SignatureChooser, SignatureUnderEmail } from "../../signatures/components/SignatureChooser";
import * as signaturesApi from "../../signatures/api/signaturesApi";
import { useSignatureChoice } from "../../signatures/lib/useSignatureChoice";
import * as conversationApi from "../api/conversationApi";
import type { AttachmentLimits, Conversation } from "../api/conversationApi";
import type { RecipientTokens } from "../api/sequenceApi";
import * as mailboxApi from "../api/mailboxApi";
import * as sequenceApi from "../api/sequenceApi";
import { AttachButton, AttachmentChips } from "./ComposerAttachments";
import { TokenInsertBar } from "./TokenInsertBar";
import { MailboxProviderMark } from "./MailboxProviderMark";
import { attachmentProblemOf, type ComposerAttachment } from "../lib/composerAttachments";
import { composerFootnoteOf } from "../lib/conversationVocabulary";
import { canSendFrom, providerLabel, type MailboxState } from "../lib/mailboxState";
import { isLive } from "../lib/runVocabulary";
import { HALF_HOURS, instantOf, wallClockOf, whenLabelOf } from "../lib/sendSchedule";
import { firstNameOf, templateTokenOptions, unfilledIn, unknownIn } from "../lib/sequenceTokens";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { formatClock, getDateTimePreferences } from "../../../lib/datetime";

/** As far ahead as the server takes a scheduled email. */
const SCHEDULE_HORIZON_DAYS = 60;
/** The server's own limit on what a consultant writes, before the tokens are filled in. */
const BODY_MAX_LENGTH = 10_000;

/**
 * The conversation's composer (`Outreach.dc.html`): a reply into the thread the consultant's mailbox last wrote to the
 * person in, or a first email of their own, sent now or at a time they pick. It writes the notes' Markdown; the
 * server fills the sequence tokens and sends it once. Locked, with the reason and its fix, while nothing could go.
 */
export function ReplyComposer({
  projectId,
  conversation,
  mailbox,
  connector,
  isArchived,
  autoFocus = false,
  onSent,
  onDraftChange,
  onExpandChange,
}: {
  projectId: string;
  conversation: Conversation;
  mailbox: MailboxState;
  connector: MailboxConnect;
  isArchived: boolean;
  /** Puts the caret where writing starts — the subject of a first email, the message of a reply. */
  autoFocus?: boolean;
  /** After an email is sent or scheduled. */
  onSent?: () => void;
  /** Whether anything is written yet: a subject, words or a file. */
  onDraftChange?: (hasDraft: boolean) => void;
  /** Expand grows the dialog it fills, told through this. */
  onExpandChange?: (expanded: boolean) => void;
}) {
  const { executive, contact, composer, currentRun, openTasks } = conversation;
  const firstName = firstNameOf(executive.fullName) ?? executive.fullName;
  const locked = lockOf({ mailbox, isArchived, contact, firstName });
  if (locked) {
    return <LockedComposer reason={locked} mailbox={mailbox} connector={connector} />;
  }
  return (
    <OpenComposer
      projectId={projectId}
      candidateId={executive.candidateId}
      firstName={firstName}
      fromAddress={"connection" in mailbox ? mailbox.connection.address : null}
      timeZone={"connection" in mailbox ? mailbox.connection.timeZone : getDateTimePreferences().timeZone}
      emails={contact.emails.map((email) => email.address)}
      replySubject={composer.replySubject}
      replyStopsRun={composer.replyStopsRun}
      resolvesReplyTask={openTasks.some((task) => task.rule === "reply")}
      statusLabel={executive.statusLabel}
      movesTo={composer.emailSentMovesTo?.label ?? null}
      runningSequenceId={currentRun.run && isLive(currentRun.run) ? currentRun.run.sequenceId : null}
      nextStepIndex={currentRun.run ? currentRun.run.sentCount : null}
      attachmentLimits={composer.attachmentLimits}
      tokens={composer.tokens}
      addsPrivacyFooter={composer.privacyFooter != null}
      autoFocus={autoFocus}
      onSent={onSent}
      onDraftChange={onDraftChange}
      onExpandChange={onExpandChange}
    />
  );
}

function OpenComposer({
  projectId,
  candidateId,
  firstName,
  fromAddress,
  timeZone,
  emails,
  replySubject,
  replyStopsRun,
  resolvesReplyTask,
  statusLabel,
  movesTo,
  runningSequenceId,
  nextStepIndex,
  attachmentLimits,
  tokens,
  addsPrivacyFooter,
  autoFocus,
  onSent,
  onDraftChange,
  onExpandChange,
}: {
  projectId: string;
  candidateId: string;
  firstName: string;
  fromAddress: string | null;
  timeZone: string;
  emails: string[];
  replySubject: string | null;
  replyStopsRun: boolean;
  resolvesReplyTask: boolean;
  statusLabel: string;
  movesTo: string | null;
  runningSequenceId: string | null;
  nextStepIndex: number | null;
  attachmentLimits: AttachmentLimits;
  tokens: RecipientTokens | null;
  addsPrivacyFooter: boolean;
  autoFocus: boolean;
  onSent?: () => void;
  onDraftChange?: (hasDraft: boolean) => void;
  onExpandChange?: (expanded: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [wantsReply, setWantsReply] = useState(replySubject !== null);
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");
  const [chosenAddress, setChosenAddress] = useState(emails[0]);
  const [attachments, setAttachments] = useState<ComposerAttachment[]>([]);
  const [appliedTemplateId, setAppliedTemplateId] = useState<string | null>(null);
  const [isSavingTemplate, setIsSavingTemplate] = useState(false);
  const [lastField, setLastField] = useState<"subject" | "body">("body");
  const subjectRef = useRef<HTMLInputElement>(null);
  const bodyEditor = useRef<NoteEditorHandle | null>(null);
  const signature = useSignatureChoice();
  const templates = useQuery({
    queryKey: emailTemplatesApi.EMAIL_TEMPLATES_KEY,
    queryFn: ({ signal }) => emailTemplatesApi.listEmailTemplates(signal),
    staleTime: 60_000,
  });
  const bookingLinkOffered = templates.data?.bookingLinkOffered === true;
  // The ledger can change under an open composer: an address since removed falls back to the first one left.
  const toAddress = emails.includes(chosenAddress) ? chosenAddress : emails[0];
  // A thread that went away (a reconnect elsewhere) leaves a first email; one that appears is offered only to an empty draft.
  const isReply = wantsReply && replySubject !== null;
  useEffect(() => {
    if (replySubject !== null && !body.trim() && !subject.trim()) setWantsReply(true);
    // Only the thread's coming or going moves the mode; a draft being typed never does.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [replySubject]);

  const send = useMutation({
    mutationFn: (sendAt: Date | null) =>
      conversationApi.sendMessage(projectId, candidateId, {
        toAddress,
        subject: isReply ? null : subject.trim(),
        body,
        sendAt: sendAt ? sendAt.toISOString() : null,
        replyInThread: isReply,
        documentVersionIds: attachments.flatMap((attachment) => (attachment.kind === "document" ? [attachment.versionId] : [])),
        signatureId: signature.signatureId,
        templateId: appliedTemplateId,
      }, attachments.flatMap((attachment) => (attachment.kind === "upload" ? [attachment.file] : []))),
    onSuccess: (scheduled, sendAt) => {
      setBody("");
      setSubject("");
      setAttachments([]);
      setAppliedTemplateId(null);
      toast(sendAt ? `Scheduled for ${whenLabelOf(new Date(scheduled.sendAt), timeZone)}.` : `Goes to ${firstName} within a minute.`);
      void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
      void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
      onSent?.();
    },
    onError: (error) => {
      // Deleted in another tab: the picker reads the list again and falls back to the default.
      if (codeOf(error) === "EMAIL_SIGNATURE_NOT_FOUND") {
        void queryClient.invalidateQueries({ queryKey: signaturesApi.SIGNATURES_KEY });
      }
      // A value went between the read and the send (a title cleared in another tab): read it again, and the line under
      // the editor names what is now blank.
      if (codeOf(error) === "OUTREACH_TOKENS_UNFILLED") {
        void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
      }
      toast.error(messageFor(error));
    },
  });

  const insertFollowUp = useMutation({
    mutationFn: () => sequenceApi.getSequence(projectId, runningSequenceId!),
    onSuccess: (sequence) => {
      const step = nextStepIndex === null ? undefined : sequence.steps[nextStepIndex];
      if (!step) {
        toast("Every step of this sequence has gone.");
        return;
      }
      // A step is plain text: read as Markdown, a "*" in it would turn into emphasis.
      insertDoc(plainNoteDoc(step.body));
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  /** Into an empty editor it is the body; into words it goes in at the cursor, nothing typed lost. */
  function insertDoc(doc: ReturnType<typeof plainNoteDoc>) {
    if (!body.trim() || !bodyEditor.current) {
      setBody(body.trim() ? `${body}\n\n${serializeNoteDoc(doc)}` : serializeNoteDoc(doc));
      return;
    }
    bodyEditor.current.insertDoc(doc);
  }

  /** The body only into a reply, which keeps its thread's subject; a new email's subject only where it has none. */
  const applyTemplate = (template: EmailTemplate) => {
    insertDoc(parseNoteMarkdown(template.body));
    if (!isReply && !subject.trim() && template.subject) setSubject(template.subject);
    setAppliedTemplateId(template.id);
  };

  /** Into whichever of the subject or the message the caret was last in, as the sequence editor does. */
  const insertVariable = (token: string) => {
    if (lastField === "subject" && !isReply) {
      const field = subjectRef.current;
      const start = field?.selectionStart ?? subject.length;
      const end = field?.selectionEnd ?? subject.length;
      setSubject(subject.slice(0, start) + token + subject.slice(end));
      requestAnimationFrame(() => {
        field?.focus();
        field?.setSelectionRange(start + token.length, start + token.length);
      });
      return;
    }
    if (bodyEditor.current) bodyEditor.current.insertText(token);
    else setBody(body + token);
  };

  const unsendable = unsendableVariablesOf(isReply ? body : `${subject}\n${body}`, tokens, templateVocabulary(bookingLinkOffered));
  const hasBody = body.trim().length > 0;
  const hasDraft = hasBody || subject.trim().length > 0 || attachments.length > 0;
  useEffect(() => onDraftChange?.(hasDraft), [hasDraft, onDraftChange]);
  const isTooLong = body.length > BODY_MAX_LENGTH;
  const attachmentProblem = attachmentProblemOf(attachments, attachmentLimits);
  // Still reading the signatures: a send now would go without the default the sender expects.
  const canSend =
    hasBody &&
    !isTooLong &&
    attachmentProblem === null &&
    (isReply || subject.trim().length > 0) &&
    unsendable.length === 0 &&
    !send.isPending &&
    !signature.isPending;
  const footnote = composerFootnoteOf({
    isReply,
    resolvesReplyTask,
    statusLabel,
    movesTo,
    stopsRunningSequence: replyStopsRun,
  });

  return (
    <section aria-label="Reply" className="flex min-h-0 flex-1 flex-col">
      <div className="mb-1 flex flex-none flex-wrap items-center gap-x-3 gap-y-1">
        <span className="inline-flex items-center gap-1.5 text-body font-semibold text-u-text">
          <Icon d={ICONS.mail} size={14} className="text-u-accent" />
          {isReply ? `Reply to ${firstName}` : `First email to ${firstName}`}
        </span>
        {replySubject !== null && (
          <button type="button" className="text-note text-u-accent hover:underline" onClick={() => setWantsReply(!isReply)}>
            {isReply ? "New email instead" : "Reply in the thread"}
          </button>
        )}
        {fromAddress && <span className="ms-auto font-mono text-meta text-u-text3">From {fromAddress}</span>}
      </div>
      {addsPrivacyFooter && <PrivacyFooterNote />}
      <NoteEditor
        value={body}
        format="markdown"
        onChange={setBody}
        teammates={null}
        ariaLabel="Message"
        placeholder={`Write to ${firstName}…`}
        variant="field"
        expandTitle={isReply ? `Reply to ${firstName}` : `First email to ${firstName}`}
        autoFocus={autoFocus && isReply}
        fillsDialog
        onExpandChange={onExpandChange}
        editorRef={bodyEditor}
        header={
          <>
            <div className="flex items-center gap-2 border-b border-u-border py-2 text-note">
              <span className="w-14 flex-none text-u-text3">To</span>
              {emails.length === 1 ? (
                <span className="min-w-0 truncate font-mono text-u-text2">{toAddress}</span>
              ) : (
                <Select
                  aria-label="To"
                  value={toAddress}
                  onChange={(event) => setChosenAddress(event.target.value)}
                  className="min-w-0 flex-1 py-1 font-mono text-note"
                >
                  {emails.map((address, index) => (
                    <option key={address} value={address}>
                      {address}
                      {index === 0 ? " · primary" : ""}
                    </option>
                  ))}
                </Select>
              )}
            </div>
            {isReply ? (
              <div className="flex items-center gap-2 border-b border-u-border py-2 text-note">
                <span className="w-14 flex-none text-u-text3">Subject</span>
                <span className="min-w-0 truncate text-u-text2">{replySubject}</span>
              </div>
            ) : (
              <label className="flex items-center gap-2 border-b border-u-border py-2 text-note">
                <span className="w-14 flex-none text-u-text3">Subject</span>
                <input
                  ref={subjectRef}
                  aria-label="Subject"
                  autoFocus={autoFocus}
                  onFocus={() => setLastField("subject")}
                  value={subject}
                  maxLength={200}
                  onChange={(event) => setSubject(event.target.value)}
                  className="min-w-0 flex-1 bg-transparent text-u-text outline-none placeholder:text-u-text3"
                  placeholder="What the email is about"
                />
              </label>
            )}
            <TokenInsertBar
              options={templateTokenOptions(bookingLinkOffered)}
              onInsert={insertVariable}
              className="mt-2 border-none bg-transparent px-0 py-1"
            />
          </>
        }
        footer={
          <>
            <SignatureUnderEmail choice={signature} />
            <AttachmentChips attachments={attachments} limits={attachmentLimits} onChange={setAttachments} />
            {unsendable.length > 0 && (
              <p role="alert" className="mt-1.5 text-meta text-u-offlimits">
                Fill in or remove {unsendable.length === 1 ? "1 variable" : `${unsendable.length} variables`} to send:{" "}
                {unsendable.map((problem) => problem.text).join("; ")}. Give one a fallback for anyone with none, like{" "}
                {"{{location|your city}}"}.
              </p>
            )}
            {isTooLong && (
              <p role="alert" className="mt-1.5 text-meta text-u-offlimits">
                Too long to send. Shorten it to {BODY_MAX_LENGTH.toLocaleString("en-GB")} characters.
              </p>
            )}
            <div className="mt-1 flex flex-wrap items-center gap-2">
              <AttachButton
                projectId={projectId}
                candidateId={candidateId}
                attachments={attachments}
                limits={attachmentLimits}
                disabled={send.isPending}
                onChange={setAttachments}
              />
              <TemplatePicker
                tokens={tokens}
                disabled={send.isPending}
                canSaveCurrent={hasBody}
                onApply={applyTemplate}
                onSaveCurrent={() => setIsSavingTemplate(true)}
              />
              {runningSequenceId && (
                <Button variant="ghost" size="xs" loading={insertFollowUp.isPending} onClick={() => insertFollowUp.mutate()}>
                  Insert follow-up
                </Button>
              )}
              <SignatureChooser choice={signature} disabled={send.isPending} />
              <div className="ms-auto flex flex-wrap items-center gap-2">
                <SchedulePicker
                  timeZone={timeZone}
                  disabled={!canSend}
                  pending={send.isPending && send.variables !== null}
                  onSchedule={(at) => send.mutate(at)}
                />
                <Button
                  size="xs"
                  disabled={!canSend}
                  loading={send.isPending && send.variables === null}
                  onClick={() => send.mutate(null)}
                >
                  Send now
                </Button>
              </div>
            </div>
            <p className="mt-2 text-meta text-u-text3">{footnote}</p>
          </>
        }
      />
      <SaveAsTemplateDialog
        open={isSavingTemplate}
        subject={isReply ? null : subject}
        buildBody={() => body}
        onClose={() => setIsSavingTemplate(false)}
      />
    </section>
  );
}

interface UnsendableVariable {
  name: string;
  text: string;
}

/**
 * What the server would refuse with `OUTREACH_TOKENS_UNFILLED`, worked out the same way: a variable no email fills,
 * then one this executive has no value for and no fallback. Without the server's values, only the first.
 */
function unsendableVariablesOf(
  text: string,
  tokens: RecipientTokens | null,
  vocabulary: readonly string[],
): UnsendableVariable[] {
  const unknown = unknownIn(text, vocabulary).map((name) => ({
    name,
    text: `{{${name}}} isn't a variable Uncava fills`,
  }));
  const blank = (tokens ? unfilledIn(text, tokens) : []).map((name) => ({
    name,
    text: `{{${name}}} has nothing on file`,
  }));
  return [...unknown, ...blank];
}

/** A day and a half hour in the mailbox's own zone, the clock its sending runs on. */
function SchedulePicker({
  timeZone,
  disabled,
  pending,
  onSchedule,
}: {
  timeZone: string;
  disabled: boolean;
  pending: boolean;
  onSchedule: (at: Date) => void;
}) {
  const now = new Date();
  const horizon = now.getTime() + SCHEDULE_HORIZON_DAYS * 86_400_000;
  const tomorrow = wallClockOf(new Date(now.getTime() + 86_400_000), timeZone).date;
  const latest = wallClockOf(new Date(horizon), timeZone).date;
  const [date, setDate] = useState(tomorrow);
  const [time, setTime] = useState("09:00");
  // A cleared date input reports "": an instant built from it is invalid, and formatting one throws mid-render.
  const at = date && time ? instantOf(date, time, timeZone) : null;
  const problem = !at
    ? "Choose a day and a time"
    : at.getTime() <= now.getTime()
      ? "Pick a time still ahead"
      : at.getTime() > horizon
        ? "Pick a time within the next 60 days"
        : null;

  return (
    <Popover
      align="right"
      width={280}
      hasPopup="dialog"
      disabled={disabled || pending}
      triggerClassName="rounded-u-control border border-u-border-strong bg-u-surface px-3.5 py-1.5 text-note font-medium text-u-text2 hover:text-u-text disabled:opacity-50"
      trigger={() => (pending ? "Scheduling…" : "Schedule")}
    >
      {(close) => (
        <div className="flex flex-col gap-2.5 p-3.5">
          <div className="text-meta text-u-text3">Send at, {timeZone.replace(/_/g, " ")} time</div>
          <div className="flex gap-2">
            <DateInput value={date} onChange={setDate} min={wallClockOf(now, timeZone).date} max={latest} ariaLabel="Send on" />
            <Select aria-label="Send at" value={time} onChange={(event) => setTime(event.target.value)}>
              {HALF_HOURS.map((halfHour) => (
                <option key={halfHour} value={halfHour}>
                  {formatClock(halfHour)}
                </option>
              ))}
            </Select>
          </div>
          <Button
            size="xs"
            disabled={problem !== null}
            onClick={() => {
              if (!at || problem) return;
              close();
              onSchedule(at);
            }}
          >
            {problem ?? `Schedule for ${whenLabelOf(at!, timeZone)}`}
          </Button>
        </div>
      )}
    </Popover>
  );
}

type LockReason =
  | { kind: "mailbox" }
  | { kind: "archived" }
  | { kind: "doNotContact"; firstName: string }
  | { kind: "noEmail"; firstName: string };

function lockOf({
  mailbox,
  isArchived,
  contact,
  firstName,
}: {
  mailbox: MailboxState;
  isArchived: boolean;
  contact: Conversation["contact"];
  firstName: string;
}): LockReason | null {
  if (isArchived) return { kind: "archived" };
  if (!canSendFrom(mailbox)) return { kind: "mailbox" };
  if (contact.doNotContact) return { kind: "doNotContact", firstName };
  if (contact.emails.length === 0) return { kind: "noEmail", firstName };
  return null;
}

function LockedComposer({
  reason,
  mailbox,
  connector,
}: {
  reason: LockReason;
  mailbox: MailboxState;
  connector: MailboxConnect;
}) {
  return (
    <section aria-label="Reply">
      <div className="flex flex-wrap items-center gap-3 rounded-u-chip border border-dashed border-u-border-strong bg-u-raised px-4 py-3 text-body text-u-text2">
        <span className="min-w-0 flex-1 basis-60">{lockedSentenceOf(reason, mailbox)}</span>
        {reason.kind === "mailbox" && mailbox.kind === "none" &&
          mailbox.providers.map((provider) => (
            <Button
              key={provider}
              variant="secondary"
              size="xs"
              loading={connector.connectingProvider === provider}
              disabled={connector.connectingProvider !== null}
              onClick={() => connector.connect(provider)}
            >
              <MailboxProviderMark provider={provider} size={13} />
              Connect {providerLabel(provider)}
            </Button>
          ))}
        {reason.kind === "mailbox" && mailbox.kind === "error" && (
          <Button
            variant="secondary"
            size="xs"
            loading={connector.connectingProvider === mailbox.connection.provider}
            onClick={() => connector.connect(mailbox.connection.provider)}
          >
            Reconnect
          </Button>
        )}
      </div>
    </section>
  );
}

function lockedSentenceOf(reason: LockReason, mailbox: MailboxState): string {
  switch (reason.kind) {
    case "mailbox":
      switch (mailbox.kind) {
        case "none":
          return "Connect your mailbox to write from here. Emails go from your own address.";
        case "error":
          return "Your mailbox needs reconnecting before anything more can go from it.";
        case "unavailable":
          return "Outreach email isn't switched on for your workspace.";
        case "loading":
          return "Checking your mailbox…";
        default:
          return "Your mailbox couldn't be read just now.";
      }
    case "archived":
      return "This position is archived. Restore it to write from here.";
    case "doNotContact":
      return `${reason.firstName} is marked do not contact.`;
    case "noEmail":
      return `No email address on file for ${reason.firstName}. Add one in the candidate record.`;
  }
}
