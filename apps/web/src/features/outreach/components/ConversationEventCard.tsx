import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { NoteText } from "../../notes/components/NoteText";
import * as conversationApi from "../api/conversationApi";
import type { ConversationEvent, ConversationMessage } from "../api/conversationApi";
import { cardTimeOf, eventFaceOf, TONE_TEXT } from "../lib/conversationVocabulary";
import { sendTimeOf } from "../lib/runVocabulary";

/**
 * One line of an executive's communication history (`Outreach.dc.html`'s history cards): an email that went or is
 * waiting, a reply, a bounce, a run or status change, a team note, a meeting. A body is drawn as text or as a note's
 * Markdown — never as HTML, since a reply's words are the sender's.
 */
export function ConversationEventCard({
  event,
  projectId,
  firstName,
  viewerUserId,
}: {
  event: ConversationEvent;
  projectId: string;
  firstName: string;
  viewerUserId: string | null;
}) {
  const face = eventFaceOf(event, firstName, projectId);
  return (
    <li className="flex gap-3 rounded-u-chip border border-u-border bg-u-surface px-3.5 py-3">
      <span
        aria-hidden="true"
        className={cn("grid size-6 flex-none place-items-center rounded-full bg-u-raised", TONE_TEXT[face.tone])}
      >
        <Icon d={iconOf(event)} size={13} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-3">
          <span className="min-w-0 flex-1 text-body font-medium text-u-text">{face.title}</span>
          <time dateTime={event.at} className="flex-none font-mono text-eyebrow text-u-text3">
            {cardTimeOf(event.at)}
          </time>
        </div>
        <EventBody event={event} projectId={projectId} viewerUserId={viewerUserId} />
        <div className="mt-1.5 text-eyebrow text-u-text3">
          <span className={cn("type-tag", TONE_TEXT[face.tone])}>{face.tag}</span>
          {face.context && <span> · {face.context}</span>}
        </div>
      </div>
    </li>
  );
}

function EventBody({
  event,
  projectId,
  viewerUserId,
}: {
  event: ConversationEvent;
  projectId: string;
  viewerUserId: string | null;
}) {
  switch (event.type) {
    case "MESSAGE":
      return <MessageBody message={event.message} projectId={projectId} viewerUserId={viewerUserId} />;
    case "NOTE":
      return <NoteText body={event.note.body} format={event.note.format} className="mt-1 text-body text-u-text2" />;
    case "MEETING":
      return (
        <p className="mt-1 text-body text-u-text2">
          {sendTimeOf(event.meeting.startsAt)}
          {event.meeting.ownerName && <> · with {event.meeting.ownerName}</>}
          {event.meeting.joinUrl && (
            <>
              {" · "}
              <a href={event.meeting.joinUrl} target="_blank" rel="noreferrer" className="text-u-accent hover:underline">
                Join
              </a>
            </>
          )}
        </p>
      );
    case "ACTIVITY":
      return null;
  }
}

function MessageBody({
  message,
  projectId,
  viewerUserId,
}: {
  message: ConversationMessage;
  projectId: string;
  viewerUserId: string | null;
}) {
  if (message.kind === "BOUNCE") {
    return <p className="mt-1 text-body text-u-text2">The recipient&rsquo;s server rejected the message.</p>;
  }
  if (message.direction === "INBOUND" && !message.text) {
    return <p className="mt-1 text-body text-u-text3">What they wrote isn&rsquo;t kept for this mailbox.</p>;
  }
  return (
    <>
      {message.text &&
        (message.format === "MARKDOWN" ? (
          <NoteText body={message.text} format="markdown" className="mt-1 text-body text-u-text2" />
        ) : (
          <p className="mt-1 whitespace-pre-wrap text-body text-u-text2">{message.text}</p>
        ))}
      {message.status === "SCHEDULED" && (
        <ScheduledLine message={message} projectId={projectId} isMine={message.senderUserId === viewerUserId} />
      )}
    </>
  );
}

function ScheduledLine({
  message,
  projectId,
  isMine,
}: {
  message: ConversationMessage;
  projectId: string;
  isMine: boolean;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const cancel = useMutation({
    mutationFn: () => conversationApi.cancelMessage(projectId, message.id),
    onSuccess: () => toast("Scheduled email cancelled."),
    onError: (error) => toast.error(messageFor(error)),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] }),
  });
  return (
    <div className="mt-2 flex flex-wrap items-center gap-2 text-note text-u-text3">
      <span>
        Goes {sendTimeOf(message.at)}
        {message.senderName && !isMine && <> · from {message.senderName}</>}
      </span>
      {isMine && message.kind === "MANUAL" && (
        <Button variant="secondary" size="xs" loading={cancel.isPending} onClick={() => cancel.mutate()}>
          Cancel
        </Button>
      )}
    </div>
  );
}

function iconOf(event: ConversationEvent): string {
  switch (event.type) {
    case "MESSAGE":
      if (event.message.kind === "BOUNCE" || event.message.status === "FAILED") return ICONS.warning;
      if (event.message.status === "SCHEDULED") return ICONS.clock;
      return ICONS.mail;
    case "NOTE":
      return event.note.kind === "call" ? ICONS.phone : ICONS.fileText;
    case "MEETING":
      return ICONS.calendar;
    case "ACTIVITY":
      return ICONS.activity;
  }
}
