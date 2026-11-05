import { useQuery } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { LinesSkeleton } from "../../../components/ui";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import type { PersonNote } from "../../candidates/api/types";
import { KindTile } from "../../candidates/components/activity/ActivityVisuals";
import { NoteKindMark } from "../../candidates/components/NoteKindMark";
import { noteAnchorOf } from "../../candidates/components/NoteParts";
import { PositionNoteCard, type NoteFocus } from "../../candidates/components/PersonSections";
import { ClampedNoteText } from "../../notes/components/ClampedNoteText";
import { NoteReaderModal } from "../../notes/components/NoteReaderModal";
import * as conversationApi from "../api/conversationApi";
import type { ConversationEvent } from "../api/conversationApi";
import type { Meeting } from "../api/meetingApi";
import { cardTimeOf, eventFaceOf } from "../lib/conversationVocabulary";
import { meetingWhenOf } from "../lib/meetingTimes";
import { ConversationEventBody } from "./ConversationEventCard";
import { MeetingDateTile, type PastCalls } from "./MeetingsSection";
import { linesOf, matches, type TimelineFilter, type TimelineLine as Line } from "../lib/drawerTimeline";

const FILTERS: readonly { value: TimelineFilter; label: string }[] = [
  { value: "all", label: "All" },
  { value: "email", label: "Email" },
  { value: "calls", label: "Calls" },
  { value: "linkedin", label: "LinkedIn" },
  { value: "meetings", label: "Meetings" },
];

/** The timeline opens on its latest few; See more reveals the next batch. */
const FIRST_SHOWN = 5;
const MORE_SHOWN = 10;

/**
 * The executive drawer's Outreach timeline (`Position.dc.html`'s Outreach): everything that passed between the team
 * and the executive on this position, newest first — emails and replies from the conversation read, the calls and
 * LinkedIn messages logged by hand, and each call that is over with the Meeting note the Notetaker filed from it.
 * Plain notes are the Notes tab's and never drawn here. Glyphs are grey on a spine, as the Activity tab draws them.
 */
export function DrawerOutreachTimeline({
  projectId,
  candidateId,
  firstName,
  past,
  focus = null,
}: {
  projectId: string;
  candidateId: string;
  firstName: string;
  past: PastCalls;
  /** A call, LinkedIn or meeting note a link asked for: brought into view. */
  focus?: NoteFocus | null;
}) {
  const { user } = useAuth();
  const conversation = useQuery({
    queryKey: conversationApi.CONVERSATION_KEY(projectId, candidateId),
    queryFn: ({ signal }) => conversationApi.getConversation(projectId, candidateId, signal),
  });
  const [filter, setFilter] = useState<TimelineFilter>("all");
  const [shown, setShown] = useState(FIRST_SHOWN);

  const lines = conversation.data ? linesOf(conversation.data.events, past) : [];
  const visible = lines.filter((line) => matches(line, filter));
  const focusIndex = focus ? visible.findIndex((line) => noteIdOfLine(line) === focus.noteId) : -1;
  const limit = Math.max(shown, focusIndex + 1);
  const page = visible.slice(0, limit);

  useEffect(() => {
    if (!focus || !conversation.isSuccess) return;
    const card = document.getElementById(noteAnchorOf(focus.noteId));
    card?.scrollIntoView({ block: "center", behavior: "smooth" });
    card?.focus({ preventScroll: true });
  }, [focus, conversation.isSuccess]);

  return (
    <section aria-label="Timeline" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="type-micro-label text-u-text3">Timeline</h3>
        <SegmentedControl
          label="Show"
          options={FILTERS}
          value={filter}
          onChange={(next) => {
            setFilter(next);
            setShown(FIRST_SHOWN);
          }}
          className="ms-auto"
        />
      </div>

      {conversation.isError ? (
        <p className="text-note text-u-text3">{messageFor(conversation.error)}</p>
      ) : conversation.isPending ? (
        <LinesSkeleton />
      ) : visible.length === 0 ? (
        <EmptyTimeline firstName={firstName} filtered={filter !== "all"} />
      ) : (
        <>
          <ol className="relative flex flex-col">
            <span aria-hidden="true" className="absolute inset-y-3 start-[10.5px] w-px bg-u-border" />
            {page.map((line, index) => (
              <TimelineRow
                key={line.key}
                line={line}
                month={monthOf(line.at) !== (index > 0 ? monthOf(page[index - 1].at) : null) ? monthOf(line.at) : null}
                projectId={projectId}
                candidateId={candidateId}
                firstName={firstName}
                viewerUserId={user?.id ?? null}
                callMenu={line.kind === "call" ? past.menuOf(line.meeting) : null}
                flashAt={focus && noteIdOfLine(line) === focus.noteId ? focus.at : null}
              />
            ))}
          </ol>
          {(visible.length > page.length || page.length > FIRST_SHOWN) && (
            <div className="flex gap-4 ps-[34px] text-note">
              {visible.length > page.length && (
                <button
                  type="button"
                  onClick={() => setShown(page.length + MORE_SHOWN)}
                  className="text-u-accent hover:underline"
                >
                  See more ({visible.length - page.length})
                </button>
              )}
              {page.length > FIRST_SHOWN && (
                <button
                  type="button"
                  onClick={() => setShown(FIRST_SHOWN)}
                  className="text-u-text3 hover:text-u-text hover:underline"
                >
                  Show less
                </button>
              )}
            </div>
          )}
          {conversation.data.truncated && page.length === visible.length && (
            <p className="ps-[34px] text-meta text-u-text3">Older history is in the Activity tab.</p>
          )}
        </>
      )}
    </section>
  );
}

function TimelineRow({
  line,
  month,
  projectId,
  candidateId,
  firstName,
  viewerUserId,
  callMenu,
  flashAt,
}: {
  line: Line;
  month: string | null;
  projectId: string;
  candidateId: string;
  firstName: string;
  viewerUserId: string | null;
  callMenu: ReactNode;
  flashAt: number | null;
}) {
  return (
    <li className="flex flex-col">
      {month && <span className="pb-2 ps-[34px] text-meta text-u-text3">{month}</span>}
      <div className="flex gap-3 pb-3">
        <Spine line={line} />
        <div className="min-w-0 flex-1">
          {line.kind === "note" ? (
            <ul>
              <PositionNoteCard
                projectId={projectId}
                candidateId={candidateId}
                note={line.note}
                flashAt={flashAt}
                showsKindMark={false}
              />
            </ul>
          ) : line.kind === "call" ? (
            <CallCard
              projectId={projectId}
              candidateId={candidateId}
              meeting={line.meeting}
              recordedNote={line.recordedNote}
              loggedNotes={line.loggedNotes}
              menu={callMenu}
              flashAt={flashAt}
            />
          ) : line.kind === "activity" ? (
            <ActivityLine event={line.event} firstName={firstName} projectId={projectId} />
          ) : (
            <MessageCard event={line.event} firstName={firstName} projectId={projectId} viewerUserId={viewerUserId} />
          )}
        </div>
      </div>
    </li>
  );
}

/** The line's kind on the spine: the app's own grey glyph, a note's kind by its mark (LinkedIn's the network's). */
function Spine({ line }: { line: Line }) {
  if (line.kind === "note") {
    return (
      <span aria-hidden="true" className="relative grid size-[22px] flex-none place-items-center bg-u-surface text-u-text3">
        <NoteKindMark kind={line.note.kind} size={14} />
      </span>
    );
  }
  return <KindTile icon={glyphOf(line)} size="dot" className="relative bg-u-surface" />;
}

function MessageCard({
  event,
  firstName,
  projectId,
  viewerUserId,
}: {
  event: Extract<ConversationEvent, { type: "MESSAGE" }>;
  firstName: string;
  projectId: string;
  viewerUserId: string | null;
}) {
  const face = eventFaceOf(event, firstName, projectId);
  return (
    <article aria-label={face.title} className="rounded-u-chip border border-u-border px-3 py-2.5">
      <CardHead title={face.title} at={event.at} />
      <ConversationEventBody event={event} title={face.title} projectId={projectId} viewerUserId={viewerUserId} />
      {face.context && <p className="mt-1.5 text-meta text-u-text3">{face.context}</p>}
    </article>
  );
}

function ActivityLine({
  event,
  firstName,
  projectId,
}: {
  event: Extract<ConversationEvent, { type: "ACTIVITY" }>;
  firstName: string;
  projectId: string;
}) {
  const face = eventFaceOf(event, firstName, projectId);
  return (
    <p className="flex min-h-[22px] items-center gap-2 text-note text-u-text3">
      <span className="min-w-0 flex-1">
        {face.title}
        {face.context && <> · {face.context}</>}
      </span>
      <time dateTime={event.at} className="flex-none font-mono text-meta">
        {cardTimeOf(event.at)}
      </time>
    </p>
  );
}

/**
 * A call that is over: its calendar line and ⋯, the Meeting note the Notetaker filed from it, held short, and any notes
 * written up about it by hand, each editable where it sits.
 */
function CallCard({
  projectId,
  candidateId,
  meeting,
  recordedNote,
  loggedNotes,
  menu,
  flashAt,
}: {
  projectId: string;
  candidateId: string;
  meeting: Meeting;
  recordedNote: PersonNote | null;
  loggedNotes: PersonNote[];
  menu: ReactNode;
  flashAt: number | null;
}) {
  const [reading, setReading] = useState(false);
  const title = meeting.title ?? "Call";
  return (
    <article
      aria-label={title}
      id={recordedNote ? noteAnchorOf(recordedNote.id) : undefined}
      tabIndex={recordedNote ? -1 : undefined}
      className="rounded-u-chip border border-u-border px-3 py-2.5 outline-none"
    >
      <div className="flex items-start gap-3">
        <MeetingDateTile startsAt={meeting.startsAt} tense="past" />
        <div className="min-w-0 flex-1">
          <span className="block text-body font-semibold text-u-text">{title}</span>
          <span className="block font-mono text-meta text-u-text3">
            {meetingWhenOf(meeting.startsAt, meeting.endsAt)}
            {meeting.ownerName ? ` · ${meeting.ownerName}` : ""}
          </span>
        </div>
        {menu && <span className="flex-none">{menu}</span>}
      </div>
      {recordedNote && (
        <div className="mt-2.5 border-t border-u-border pt-2.5">
          <p className="mb-1 flex items-center gap-1.5 text-meta font-medium text-u-text2">
            <Icon d={ICONS.sparkle} size={13} className="text-u-text3" />
            Meeting notes · written by the Notetaker
          </p>
          <ClampedNoteText
            body={recordedNote.body}
            format={recordedNote.format}
            className="text-body text-u-text2"
            onOpen={() => setReading(true)}
          />
          <NoteReaderModal
            open={reading}
            onClose={() => setReading(false)}
            title={`Meeting notes · ${title}`}
            writtenAt={recordedNote.createdAt}
            body={recordedNote.body}
            format={recordedNote.format}
          />
        </div>
      )}
      {loggedNotes.length > 0 && (
        <ul aria-label="Notes logged about this meeting" className="mt-2.5 flex flex-col gap-2 border-t border-u-border pt-2.5">
          {loggedNotes.map((note) => (
            <PositionNoteCard
              key={note.id}
              projectId={projectId}
              candidateId={candidateId}
              // Its meeting's own title is the card's heading already.
              note={note.title === meeting.title ? { ...note, title: null } : note}
              flashAt={flashAt}
              showsKindMark={false}
            />
          ))}
        </ul>
      )}
    </article>
  );
}

function CardHead({ title, at }: { title: string; at: string }) {
  return (
    <div className="flex items-baseline gap-3">
      <span className="min-w-0 flex-1 text-body font-semibold text-u-text">{title}</span>
      <time dateTime={at} className="flex-none font-mono text-meta text-u-text3">
        {cardTimeOf(at)}
      </time>
    </div>
  );
}

function EmptyTimeline({ firstName, filtered }: { firstName: string; filtered: boolean }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-u-card border border-dashed border-u-border-strong px-6 py-7 text-center">
      <Icon d={ICONS.activity} size={20} className="text-u-text3" />
      <p className="text-body font-semibold text-u-text">
        {filtered ? "Nothing of this kind yet" : `No contact with ${firstName} yet`}
      </p>
      <p className="max-w-[380px] text-note text-u-text3">
        Emails you send, replies, the calls, LinkedIn messages and meetings you log, and recorded calls with their
        notes all land here, newest first.
      </p>
    </div>
  );
}

function noteIdOfLine(line: Line): string | null {
  if (line.kind === "note") return line.note.id;
  if (line.kind === "call") return line.recordedNote?.id ?? line.loggedNotes[0]?.id ?? null;
  return null;
}

function glyphOf(line: Exclude<Line, { kind: "note" }>): string {
  switch (line.kind) {
    case "call":
      return ICONS.video;
    case "activity":
      return ICONS.pipeline;
    case "message": {
      const message = line.event.message;
      if (message.kind === "BOUNCE" || message.status === "FAILED") return ICONS.warning;
      if (message.direction === "INBOUND") return ICONS.arrowLeft;
      if (message.status === "SCHEDULED") return ICONS.clock;
      return ICONS.mail;
    }
  }
}

const MONTH = new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" });

function monthOf(isoInstant: string): string {
  const at = new Date(isoInstant);
  return Number.isNaN(at.getTime()) ? "" : MONTH.format(at);
}
