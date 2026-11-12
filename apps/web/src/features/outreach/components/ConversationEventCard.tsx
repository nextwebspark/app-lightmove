import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { saveBlob } from "../../../lib/saveBlob";
import { formatBytes } from "../../candidates/lib/documents";
import type { ActivityExecutive } from "../../candidates/lib/activityPresentation";
import { ClampedNoteText } from "../../notes/components/ClampedNoteText";
import { NoteReaderModal } from "../../notes/components/NoteReaderModal";
import { NoteText } from "../../notes/components/NoteText";
import * as conversationApi from "../api/conversationApi";
import type { ConversationAttachment, ConversationEvent, ConversationMessage } from "../api/conversationApi";
import { actorOfEvent, addressLineOf, eventFaceOf, tileOfEvent, TONE_TEXT } from "../lib/conversationVocabulary";
import { sendTimeOf } from "../lib/runVocabulary";
import { EntryActor, OutreachEntryCard } from "./OutreachEntryCard";

/**
 * One line of an executive's communication history (`Outreach.dc.html`'s history cards): an email that went or is
 * waiting, a reply, a bounce, a team note, a meeting — led by its kind's mark and the face of whoever it was. A body is
 * drawn as text or as a note's Markdown — never as HTML, since a reply's words are the sender's.
 */
export function ConversationEventCard({
  event,
  projectId,
  firstName,
  executive,
  viewerUserId,
}: {
  event: ConversationEvent;
  projectId: string;
  firstName: string;
  executive: ActivityExecutive;
  viewerUserId: string | null;
}) {
  const face = eventFaceOf(event, firstName, projectId);
  const actor = actorOfEvent(event);
  const address = event.type === "MESSAGE" ? addressLineOf(event.message) : null;
  return (
    <li>
      <OutreachEntryCard
        label={face.title}
        tile={tileOfEvent(event)}
        title={face.title}
        at={event.at}
        actor={
          <EntryActor actor={actor} executive={executive} name={actor.kind === "automation" ? "Uncava" : undefined} />
        }
        byline={address && <span className="min-w-0 truncate text-u-text3">{address}</span>}
        footer={
          <>
            <span className={cn("type-tag", TONE_TEXT[face.tone])}>{face.tag}</span>
            {face.context && <span> · {face.context}</span>}
          </>
        }
      >
        <ConversationEventBody event={event} title={face.title} projectId={projectId} viewerUserId={viewerUserId} />
      </OutreachEntryCard>
    </li>
  );
}

/** A team note held to the card's height, Open reading it whole; a note is edited where it lives, not here. */
function NoteEventBody({ note, title }: { note: Extract<ConversationEvent, { type: "NOTE" }>["note"]; title: string }) {
  const [reading, setReading] = useState(false);
  return (
    <>
      <ClampedNoteText
        body={note.body}
        format={note.format}
        className="mt-1 text-body text-u-text2"
        onOpen={() => setReading(true)}
      />
      <NoteReaderModal
        open={reading}
        onClose={() => setReading(false)}
        title={title}
        writtenAt={note.createdAt}
        body={note.body}
        format={note.format}
      />
    </>
  );
}

/** An event's words — an email's text and files, a note, a meeting's time — without the card around them. */
export function ConversationEventBody({
  event,
  title,
  projectId,
  viewerUserId,
}: {
  event: ConversationEvent;
  title: string;
  projectId: string;
  viewerUserId: string | null;
}) {
  switch (event.type) {
    case "MESSAGE":
      return <MessageBody message={event.message} projectId={projectId} viewerUserId={viewerUserId} />;
    case "NOTE":
      return <NoteEventBody note={event.note} title={title} />;
    case "MEETING":
      return (
        <p className="mt-1 text-body text-u-text2">
          {event.meeting.videoProvider && <>{event.meeting.videoProvider} · </>}
          {sendTimeOf(event.meeting.startsAt)}
          {event.meeting.joinUrl && isWebLink(event.meeting.joinUrl) && (
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
      {message.attachments.length > 0 && <MessageAttachments message={message} projectId={projectId} />}
      {message.status === "SCHEDULED" && (
        <ScheduledLine message={message} projectId={projectId} isMine={message.senderUserId === viewerUserId} />
      )}
    </>
  );
}

/** The files an email carried, each a download of the bytes as they went (a fetch: the bytes need the bearer token). */
function MessageAttachments({ message, projectId }: { message: ConversationMessage; projectId: string }) {
  const toast = useToast();
  const download = useMutation({
    mutationFn: (attachment: ConversationAttachment) =>
      conversationApi.attachmentContent(projectId, message.id, attachment.id),
    onSuccess: (blob, attachment) => saveBlob(blob, attachment.fileName),
    onError: (error) => toast.error(messageFor(error)),
  });
  return (
    <ul aria-label="Attachments" className="mt-2 flex flex-wrap gap-1.5">
      {message.attachments.map((attachment) => (
        <li key={attachment.id}>
          <button
            type="button"
            aria-label={`Download ${attachment.fileName}`}
            disabled={download.isPending && download.variables?.id === attachment.id}
            onClick={() => download.mutate(attachment)}
            className="inline-flex max-w-full items-center gap-1.5 rounded-u-chip border border-u-border bg-u-raised px-2 py-0.5 text-note text-u-text2 hover:text-u-text disabled:opacity-50"
          >
            <Icon d={ICONS.paperclip} size={12} className="flex-none text-u-text3" />
            <span className="min-w-0 truncate">{attachment.fileName}</span>
            <span className="flex-none font-mono text-meta text-u-text3">{formatBytes(attachment.sizeBytes)}</span>
          </button>
        </li>
      ))}
    </ul>
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

/** A calendar event's link is whoever made the event's to write: only a web address becomes a link. */
function isWebLink(url: string): boolean {
  try {
    const { protocol } = new URL(url);
    return protocol === "https:" || protocol === "http:";
  } catch {
    return false;
  }
}
