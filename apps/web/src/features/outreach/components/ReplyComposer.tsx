import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useId, useRef, useState } from "react";
import { cn } from "../../../lib/cn";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, DateInput, Select, useToast } from "../../../components/ui";
import { Popover } from "../../../components/ui/Popover";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import * as emailTemplatesApi from "../../emailtemplates/api/emailTemplatesApi";
import type { EmailTemplate } from "../../emailtemplates/api/emailTemplatesApi";
import { SaveAsTemplateDialog } from "../../emailtemplates/components/SaveAsTemplateDialog";
import { TemplatePicker } from "../../emailtemplates/components/TemplatePicker";
import { templateVocabulary } from "../../emailtemplates/lib/templateVariables";
import type { RecentApproach } from "../../candidates/api/types";
import { RecentApproachConfirm } from "../../candidates/components/RecentApproachConfirm";
import { RecentApproachNote } from "../../candidates/components/RecentApproachNote";
import { approachKeyOf, isCaution } from "../../candidates/lib/recentApproach";
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
import { VariableChipPopover } from "./VariableChipPopover";
import { VariableMenu } from "./VariableMenu";
import { MailboxProviderMark } from "./MailboxProviderMark";
import { attachmentProblemOf, type ComposerAttachment } from "../lib/composerAttachments";
import { composerFootnoteOf } from "../lib/conversationVocabulary";
import { canSendFrom, providerLabel, type MailboxState } from "../lib/mailboxState";
import { isLive } from "../lib/runVocabulary";
import { HALF_HOURS, instantOf, wallClockOf, whenLabelOf } from "../lib/sendSchedule";
import { firstNameOf, replaceVariable, templateTokenOptions, unfilledIn, unknownIn } from "../lib/sequenceTokens";
import { CHIP_CLASS, chipOf, piecesOf, VARIABLE_LABELS, type VariableChip, type VariableChipKind } from "../lib/variableChips";
import { createVariableChips, replaceRangeInEditor, replaceVariableInEditor } from "../lib/variableChipsExtension";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { formatClock, getDateTimePreferences } from "../../../lib/datetime";

/** As far ahead as the server takes a scheduled email. */
const SCHEDULE_HORIZON_DAYS = 60;
/** The Subject field's own limit. */
const SUBJECT_MAX_LENGTH = 200;
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
  startsExpanded = false,
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
  /** The dialog it fills opens expanded. */
  startsExpanded?: boolean;
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
      fullName={executive.fullName}
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
      recentApproach={composer.recentApproach}
      autoFocus={autoFocus}
      onSent={onSent}
      onDraftChange={onDraftChange}
      onExpandChange={onExpandChange}
      startsExpanded={startsExpanded}
    />
  );
}

/** A first email opens greeting the executive by name; it is ordinary text, deleted like any other. */
const GREETING = "Hi {{firstName}},";

function OpenComposer({
  projectId,
  candidateId,
  fullName,
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
  recentApproach,
  autoFocus,
  onSent,
  onDraftChange,
  onExpandChange,
  startsExpanded = false,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
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
  recentApproach: RecentApproach | null;
  autoFocus: boolean;
  onSent?: () => void;
  onDraftChange?: (hasDraft: boolean) => void;
  onExpandChange?: (expanded: boolean) => void;
  /** The dialog it fills opens expanded. */
  startsExpanded?: boolean;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [wantsReply, setWantsReply] = useState(replySubject !== null);
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState(replySubject === null ? GREETING : "");
  const isBodyUntouched = body.trim() === "" || body.trim() === GREETING;
  const [chosenAddress, setChosenAddress] = useState(emails[0]);
  const [understoodApproach, setUnderstoodApproach] = useState<string | null>(null);
  const [heldSend, setHeldSend] = useState<{ at: Date | null } | null>(null);
  const isApproaching = isCaution(recentApproach);
  const approachKey = approachKeyOf(recentApproach);
  useEffect(() => {
    if (!isApproaching) setHeldSend(null);
  }, [isApproaching]);
  const [attachments, setAttachments] = useState<ComposerAttachment[]>([]);
  const [appliedTemplateId, setAppliedTemplateId] = useState<string | null>(null);
  const [isSavingTemplate, setIsSavingTemplate] = useState(false);
  const [lastField, setLastField] = useState<"subject" | "body">("body");
  const subjectRef = useRef<HTMLInputElement>(null);
  const bodyEditor = useRef<NoteEditorHandle | null>(null);
  const subjectId = useId();
  const [isSubjectFocused, setIsSubjectFocused] = useState(false);
  // Shipped bug: only the subject's focus was recorded, so once it had been visited every variable went there,
  // even with the caret back in the message.
  useEffect(() => {
    const recordBodyFocus = (event: FocusEvent) => {
      const editorDom = bodyEditor.current?.editor.view.dom;
      if (editorDom && event.target instanceof Node && editorDom.contains(event.target)) setLastField("body");
    };
    document.addEventListener("focusin", recordBodyFocus);
    return () => document.removeEventListener("focusin", recordBodyFocus);
  }, []);
  const [openChip, setOpenChip] = useState<OpenChip | null>(null);
  const [chips] = useState(() => {
    const created = createVariableChips();
    return { ...created, extensions: [created.extension] };
  });
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
    if (replySubject !== null && isBodyUntouched && !subject.trim()) {
      setWantsReply(true);
      setBody("");
    }
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
    if (isBodyUntouched) {
      setBody(serializeNoteDoc(doc));
      return;
    }
    if (!bodyEditor.current) {
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

  // An email to one person reads each variable as their value, those the composer accepts; until the values are read,
  // it reads as typed. A booking link where none is offered stays as typed: the line under the editor names it.
  useEffect(() => {
    const accepted = new Set(templateVocabulary(bookingLinkOffered));
    chips.setHost(
      tokens
        ? {
            chipFor: (name, fallback) => (accepted.has(name) ? chipOf(name, fallback, tokens, firstName) : null),
            // A second press on the open chip closes its popover.
            onChip: (target) => setOpenChip((open) => (open?.name === target.name && open.scope === "body" && open.from === target.from ? null : { ...target, scope: "body" })),
          }
        : null,
    );
  }, [chips, tokens, firstName, bookingLinkOffered]);

  // The popover is fixed where its chip was: a word typed before it would leave it behind.
  useEffect(() => {
    setOpenChip(null);
  }, [body, subject]);

  const returnFocus = (scope: OpenChip["scope"]) => {
    if (scope === "subject") subjectRef.current?.focus();
    else bodyEditor.current?.editor.commands.focus();
  };

  /** Words typed for this email, or none to take it out: wherever the variable has nothing on file, subject and all. */
  const fillBlank = (open: OpenChip, text: string) => {
    if (bodyEditor.current) replaceVariableInEditor(bodyEditor.current.editor, open.name, text, true);
    if (!isReply) setSubject((current) => replaceVariable(current, open.name, text, true).slice(0, SUBJECT_MAX_LENGTH));
    setOpenChip(null);
    returnFocus(open.scope);
  };

  /** Only the chip that was pressed: its words given the value as plain text, or taken out. */
  const settleChip = (open: OpenChip, text: string) => {
    if (open.scope === "body") {
      if (bodyEditor.current) replaceRangeInEditor(bodyEditor.current.editor, open.from, open.to, text);
    } else {
      setSubject((current) => (current.slice(0, open.from) + text + current.slice(open.to)).slice(0, SUBJECT_MAX_LENGTH));
    }
    setOpenChip(null);
    returnFocus(open.scope);
  };

  const unsendable = unsendableVariablesOf(isReply ? body : `${subject}\n${body}`, tokens, templateVocabulary(bookingLinkOffered));
  const subjectChips = tokens
    ? piecesOf(subject, tokens, firstName, (name) => templateVocabulary(bookingLinkOffered).includes(name))
    : [];
  const subjectPieces = subjectChips.some((piece) => "chip" in piece) ? subjectChips : null;
  const hasBody = !isBodyUntouched;
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
  const requestSend = (at: Date | null) => {
    if (isApproaching && understoodApproach !== approachKey) {
      setHeldSend({ at });
      return;
    }
    send.mutate(at);
  };
  const footnote = composerFootnoteOf({
    isReply,
    resolvesReplyTask,
    statusLabel,
    movesTo,
    stopsRunningSequence: replyStopsRun,
  });

  return (
    <section aria-label="Reply" className="flex min-h-fit flex-1 flex-col">
      <div className="mb-1 flex flex-none flex-wrap items-center gap-x-3 gap-y-1">
        <span className="inline-flex items-center gap-1.5 text-body font-semibold text-u-text">
          <Icon d={ICONS.mail} size={14} className="text-u-accent" />
          {isReply ? `Reply to ${firstName}` : `First email to ${firstName}`}
        </span>
        {replySubject !== null && (
          <button
            type="button"
            className="text-note text-u-accent hover:underline"
            onClick={() => {
              if (isBodyUntouched) setBody(isReply ? GREETING : "");
              setWantsReply(!isReply);
            }}
          >
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
        startsExpanded={startsExpanded}
        editorRef={bodyEditor}
        extraExtensions={chips.extensions}
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
              <div className="flex items-center gap-2 border-b border-u-border py-2 text-note">
                {/* Not wrapped round the field: a label names the first control inside it, and the chips come first. */}
                <label htmlFor={subjectId} className="w-14 flex-none text-u-text3">
                  Subject
                </label>
                <span className="relative flex min-w-0 flex-1 items-center">
                  {/* The words in the field stay the variables; this reads them as the executive's values until the field is
                      pressed, since a field of plain text cannot draw a chip in place. Before the field, so Tab reaches the
                      chips first; a press on its words puts the caret at the same words in the field. */}
                  {subjectPieces && !isSubjectFocused && (
                    <span
                      className="absolute inset-0 z-10 flex cursor-text items-center text-u-text"
                      onMouseDown={(event) => {
                        if (event.target instanceof Element && event.target.closest("[data-variable]")) return;
                        event.preventDefault();
                        const field = subjectRef.current;
                        if (!field) return;
                        const placed = caretOffsetIn(event.target, event.clientX, event.clientY);
                        field.focus();
                        const at = placed === null ? subject.length : placed;
                        field.setSelectionRange(at, at);
                      }}
                    >
                      <span className="min-w-0 truncate">
                        {subjectPieces.map((piece, index) =>
                          "chip" in piece ? (
                            <button
                              key={index}
                              type="button"
                              title={piece.chip.title}
                              data-variable={piece.name}
                              onClick={(event) => {
                                event.preventDefault();
                                const rect = event.currentTarget.getBoundingClientRect();
                                setOpenChip((open) =>
                                  open?.name === piece.name && open.scope === "subject" && open.from === piece.start
                                    ? null
                                    : { name: piece.name, kind: piece.chip.kind, chip: piece.chip, rect, scope: "subject", from: piece.start, to: piece.end },
                                );
                              }}
                              className={CHIP_CLASS[piece.chip.kind]}
                            >
                              {piece.text}
                            </button>
                          ) : (
                            <span key={index} data-start={piece.start}>
                              {piece.text}
                            </span>
                          ),
                        )}
                      </span>
                    </span>
                  )}
                  <input
                    id={subjectId}
                    ref={subjectRef}
                    aria-label="Subject"
                    autoFocus={autoFocus}
                    onFocus={() => {
                      setLastField("subject");
                      setIsSubjectFocused(true);
                    }}
                    onBlur={() => setIsSubjectFocused(false)}
                    value={subject}
                    maxLength={SUBJECT_MAX_LENGTH}
                    onChange={(event) => setSubject(event.target.value)}
                    className={cn(
                      "min-w-0 flex-1 bg-transparent text-u-text outline-none placeholder:text-u-text3",
                      subjectPieces && !isSubjectFocused && "text-transparent",
                    )}
                    placeholder="What the email is about"
                  />
                </span>
              </div>
            )}
          </>
        }
        footer={
          <>
            <SignatureUnderEmail choice={signature} />
            <AttachmentChips attachments={attachments} limits={attachmentLimits} onChange={setAttachments} />
            <RecentApproachNote approach={recentApproach} className="mt-1.5" />
            {isApproaching && (
              <RecentApproachConfirm
                open={heldSend !== null}
                fullName={fullName}
                approach={recentApproach}
                confirmLabel={heldSend?.at ? "Schedule anyway" : "Send anyway"}
                canConfirm={canSend}
                onClose={() => setHeldSend(null)}
                onConfirm={() => {
                  if (!heldSend || !canSend) return;
                  const at = heldSend.at;
                  setUnderstoodApproach(approachKey);
                  setHeldSend(null);
                  send.mutate(at);
                }}
              />
            )}
            {/* Stuck to the dialog's foot: on a short window the body scrolls, and Send must not scroll away with it. */}
            <div className="sticky bottom-0 z-10 mt-1 bg-u-surface pb-2 pt-1">
              {/* With the row: what holds Send is as much in view as Send itself. */}
              {unsendable.length > 0 && (
                <p role="alert" className="mb-1.5 text-meta text-u-offlimits">
                  {unsendableLineOf(unsendable, firstName)}
                </p>
              )}
              {isTooLong && (
                <p role="alert" className="mb-1.5 text-meta text-u-offlimits">
                  Too long to send. Shorten it to {BODY_MAX_LENGTH.toLocaleString("en-GB")} characters.
                </p>
              )}
              <div className="flex flex-wrap items-center gap-2">
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
              <VariableMenu
                options={templateTokenOptions(bookingLinkOffered)}
                tokens={tokens}
                forWhom={firstName}
                disabled={send.isPending}
                onInsert={insertVariable}
              />
              <SignatureChooser choice={signature} disabled={send.isPending} />
              <div className="ms-auto flex flex-wrap items-center gap-2">
                <SchedulePicker
                  timeZone={timeZone}
                  disabled={!canSend}
                  pending={send.isPending && send.variables !== null}
                  onSchedule={(at) => requestSend(at)}
                />
                <Button
                  size="xs"
                  disabled={!canSend}
                  loading={send.isPending && send.variables === null}
                  onClick={() => requestSend(null)}
                >
                  Send now
                </Button>
              </div>
              </div>
            </div>
            <p className="mt-2 text-meta text-u-text3">{footnote}</p>
          </>
        }
      />
      {openChip && (
        <VariableChipPopover
          target={openChip}
          forWhom={firstName}
          onTypeValue={(text) => fillBlank(openChip, text)}
          onEditAsText={() => settleChip(openChip, openChip.chip.text)}
          onRemove={() => (openChip.kind === "blank" ? fillBlank(openChip, "") : settleChip(openChip, ""))}
          onClose={() => setOpenChip(null)}
          onEscape={() => {
            setOpenChip(null);
            returnFocus(openChip.scope);
          }}
        />
      )}
      <SaveAsTemplateDialog
        open={isSavingTemplate}
        subject={isReply ? null : subject}
        buildBody={() => body}
        onClose={() => setIsSavingTemplate(false)}
      />
    </section>
  );
}

/** Where in a plain run of the subject's overlay a press landed, as an offset in the field's own words. */
function caretOffsetIn(target: EventTarget, x: number, y: number): number | null {
  const run = target instanceof Element ? target.closest<HTMLElement>("[data-start]") : null;
  if (!run) return null;
  const doc = document as Document & {
    caretRangeFromPoint?: (x: number, y: number) => Range | null;
    caretPositionFromPoint?: (x: number, y: number) => { offsetNode: Node; offset: number } | null;
  };
  const inside = doc.caretRangeFromPoint?.(x, y)?.startOffset ?? doc.caretPositionFromPoint?.(x, y)?.offset;
  return inside === undefined ? null : Number(run.dataset.start) + inside;
}

/** A chip whose popover is open: where its words are in the body (document positions) or the subject (offsets). */
interface OpenChip {
  name: string;
  kind: VariableChipKind;
  chip: VariableChip;
  rect: DOMRect;
  scope: "body" | "subject";
  from: number;
  to: number;
}

interface UnsendableVariable {
  name: string;
  text: string;
  /** No email fills it, or this executive has nothing on file for it. */
  kind: "unknown" | "blank";
}

/** What holds Send, in a sentence: a variable with nothing on file is a red chip to click, so the line says so. */
function unsendableLineOf(problems: readonly UnsendableVariable[], firstName: string): string {
  const blanks = problems.filter((problem) => problem.kind === "blank");
  if (blanks.length === problems.length) {
    const labels = blanks.map((problem) => VARIABLE_LABELS[problem.name] ?? problem.name);
    return blanks.length === 1
      ? `${labels[0]} has nothing on file for ${firstName}: click it to type one for this email, or remove it.`
      : `${blanks.length} variables have nothing on file for ${firstName}: ${labels.join(", ")}. Click one to type it for this email, or remove it.`;
  }
  return `Fill in or remove ${problems.length === 1 ? "1 variable" : `${problems.length} variables`} to send: ${problems
    .map((problem) => problem.text)
    .join("; ")}.`;
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
    kind: "unknown" as const,
  }));
  const blank = (tokens ? unfilledIn(text, tokens) : []).map((name) => ({
    name,
    text: `{{${name}}} has nothing on file`,
    kind: "blank" as const,
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
