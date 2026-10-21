import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, DateInput, Select, useToast } from "../../../components/ui";
import { Popover } from "../../../components/ui/Popover";
import { messageFor } from "../../../lib/errorCodes";
import { NoteEditor } from "../../notes/components/NoteEditor";
import * as conversationApi from "../api/conversationApi";
import type { AttachmentLimits, Conversation } from "../api/conversationApi";
import * as mailboxApi from "../api/mailboxApi";
import * as sequenceApi from "../api/sequenceApi";
import { AttachButton, AttachmentChips } from "./ComposerAttachments";
import { attachmentProblemOf, type ComposerAttachment } from "../lib/composerAttachments";
import { composerFootnoteOf } from "../lib/conversationVocabulary";
import { canSendFrom, providerLabel, type MailboxState } from "../lib/mailboxState";
import { isLive } from "../lib/runVocabulary";
import { HALF_HOURS, instantOf, wallClockOf, whenLabelOf } from "../lib/sendSchedule";
import { firstNameOf } from "../lib/sequenceTokens";
import type { MailboxConnect } from "../lib/useMailboxConnect";

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
}: {
  projectId: string;
  conversation: Conversation;
  mailbox: MailboxState;
  connector: MailboxConnect;
  isArchived: boolean;
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
      timeZone={"connection" in mailbox ? mailbox.connection.timeZone : "UTC"}
      emails={contact.emails.map((email) => email.address)}
      replySubject={composer.replySubject}
      replyStopsRun={composer.replyStopsRun}
      resolvesReplyTask={openTasks.some((task) => task.rule === "reply")}
      statusLabel={executive.statusLabel}
      movesTo={composer.emailSentMovesTo?.label ?? null}
      runningSequenceId={currentRun.run && isLive(currentRun.run) ? currentRun.run.sequenceId : null}
      nextStepIndex={currentRun.run ? currentRun.run.sentCount : null}
      attachmentLimits={composer.attachmentLimits}
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
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [wantsReply, setWantsReply] = useState(replySubject !== null);
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");
  const [chosenAddress, setChosenAddress] = useState(emails[0]);
  const [attachments, setAttachments] = useState<ComposerAttachment[]>([]);
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
      }, attachments.flatMap((attachment) => (attachment.kind === "upload" ? [attachment.file] : []))),
    onSuccess: (scheduled, sendAt) => {
      setBody("");
      setSubject("");
      setAttachments([]);
      toast(sendAt ? `Scheduled for ${whenLabelOf(new Date(scheduled.sendAt), timeZone)}.` : `Goes to ${firstName} within a minute.`);
      void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
      void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const insertFollowUp = useMutation({
    mutationFn: () => sequenceApi.getSequence(projectId, runningSequenceId!),
    onSuccess: (sequence) => {
      const step = nextStepIndex === null ? undefined : sequence.steps[nextStepIndex];
      if (!step) {
        toast("Every step of this sequence has gone.");
        return;
      }
      setBody((current) => (current.trim() ? `${current}\n\n${step.body}` : step.body));
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const hasBody = body.trim().length > 0;
  const isTooLong = body.length > BODY_MAX_LENGTH;
  const attachmentProblem = attachmentProblemOf(attachments, attachmentLimits);
  const canSend =
    hasBody && !isTooLong && attachmentProblem === null && (isReply || subject.trim().length > 0) && !send.isPending;
  const footnote = composerFootnoteOf({
    isReply,
    resolvesReplyTask,
    statusLabel,
    movesTo,
    stopsRunningSequence: replyStopsRun,
  });

  return (
    <section aria-label="Reply" className="border-t border-u-border bg-u-surface px-5 py-3.5">
      <div className="mb-2 flex flex-wrap items-center gap-x-3 gap-y-1">
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
      <div className="rounded-u-chip border border-u-border bg-u-raised">
        <div className="flex items-center gap-2 border-b border-u-border px-3 py-2 text-note">
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
              {emails.map((address) => (
                <option key={address} value={address}>
                  {address}
                </option>
              ))}
            </Select>
          )}
        </div>
        <div className="flex items-center gap-2 border-b border-u-border px-3 py-2 text-note">
          <span className="w-14 flex-none text-u-text3">Subject</span>
          {isReply ? (
            <span className="min-w-0 truncate text-u-text2">{replySubject}</span>
          ) : (
            <input
              aria-label="Subject"
              value={subject}
              maxLength={200}
              onChange={(event) => setSubject(event.target.value)}
              className="min-w-0 flex-1 bg-transparent text-u-text outline-none placeholder:text-u-text3"
              placeholder="What the email is about"
            />
          )}
        </div>
        <div className="px-1.5 py-1.5">
          <NoteEditor
            value={body}
            format="markdown"
            onChange={setBody}
            teammates={null}
            ariaLabel="Message"
            placeholder={`Write to ${firstName}…`}
            variant="field"
            expandTitle={isReply ? `Reply to ${firstName}` : `First email to ${firstName}`}
          />
        </div>
      </div>
      <AttachmentChips attachments={attachments} limits={attachmentLimits} onChange={setAttachments} />
      {isTooLong && (
        <p role="alert" className="mt-1.5 text-meta text-u-offlimits">
          Too long to send. Shorten it to {BODY_MAX_LENGTH.toLocaleString("en-GB")} characters.
        </p>
      )}
      <div className="mt-2.5 flex flex-wrap items-center gap-2">
        <AttachButton
          projectId={projectId}
          candidateId={candidateId}
          attachments={attachments}
          limits={attachmentLimits}
          disabled={send.isPending}
          onChange={setAttachments}
        />
        {runningSequenceId && (
          <Button variant="ghost" size="xs" loading={insertFollowUp.isPending} onClick={() => insertFollowUp.mutate()}>
            Insert follow-up
          </Button>
        )}
        <div className="ms-auto flex flex-wrap items-center gap-2">
          <SchedulePicker
            timeZone={timeZone}
            disabled={!canSend}
            pending={send.isPending && send.variables !== null}
            onSchedule={(at) => send.mutate(at)}
          />
          <Button size="xs" disabled={!canSend} loading={send.isPending && send.variables === null} onClick={() => send.mutate(null)}>
            Send now
          </Button>
        </div>
      </div>
      <p className="mt-2 text-meta text-u-text3">{footnote}</p>
    </section>
  );
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
                  {halfHour}
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
    <section aria-label="Reply" className="border-t border-u-border bg-u-surface px-5 py-4">
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
