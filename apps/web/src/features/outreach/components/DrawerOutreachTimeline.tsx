import { useQuery } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { LinesSkeleton } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import type { PersonNote } from "../../candidates/api/types";
import { noteAnchorOf } from "../../candidates/components/NoteParts";
import { PositionNoteCard, type NoteFocus } from "../../candidates/components/PersonSections";
import { NoteKindMark } from "../../candidates/components/NoteKindMark";
import { ActivityFilterMenu, type FilterMenuOption } from "../../candidates/components/activity/ActivityFilterMenu";
import { KindTile } from "../../candidates/components/activity/ActivityVisuals";
import type { ActivityExecutive } from "../../candidates/lib/activityPresentation";
import { ClampedNoteText } from "../../notes/components/ClampedNoteText";
import { NoteReaderModal } from "../../notes/components/NoteReaderModal";
import * as conversationApi from "../api/conversationApi";
import type { ConversationEvent } from "../api/conversationApi";
import type { Meeting } from "../api/meetingApi";
import {
  actorOfEvent,
  actorOfMeeting,
  actorOfNote,
  addressLineOf,
  eventFaceOf,
  tileOfEvent,
  tileOfMeeting,
  tileOfNote,
} from "../lib/conversationVocabulary";
import { meetingWhenOf } from "../lib/meetingTimes";
import { ConversationEventBody } from "./ConversationEventCard";
import type { PastCalls } from "./MeetingsSection";
import { EntryActor, EntryLeadMark, OutreachEntryCard, RecordedBadge } from "./OutreachEntryCard";
import { kindOfLine, linesOf, matches, type TimelineKind, type TimelineLine as Line } from "../lib/drawerTimeline";

const KINDS: readonly { kind: TimelineKind; label: string; icon: string | null }[] = [
  { kind: "email", label: "Email", icon: ICONS.mail },
  { kind: "calls", label: "Calls", icon: ICONS.phoneCall },
  { kind: "linkedin", label: "LinkedIn", icon: null },
  { kind: "whatsapp", label: "WhatsApp", icon: null },
  { kind: "meetings", label: "Meetings", icon: ICONS.video },
];

/** The timeline opens on its latest few; See more reveals the next batch. */
const FIRST_SHOWN = 5;
const MORE_SHOWN = 10;

/**
 * The executive drawer's Outreach timeline (`Position.dc.html`'s Outreach): everything that passed between the team
 * and the executive on this position, newest first — emails and replies from the conversation read, the calls and
 * LinkedIn and WhatsApp messages logged by hand, and each call that is over with the Meeting note the Notetaker filed
 * from it.
 * Plain notes are the Notes tab's and never drawn here. Each is a card led by its kind's mark and the face of whoever
 * did it; status and run changes are the Activity tab's.
 */
export function DrawerOutreachTimeline({
  projectId,
  candidateId,
  firstName,
  executive,
  past,
  focus = null,
}: {
  projectId: string;
  candidateId: string;
  firstName: string;
  /** Their face, for a reply they sent. */
  executive: ActivityExecutive;
  past: PastCalls;
  /** A call, LinkedIn or meeting note a link asked for: brought into view. */
  focus?: NoteFocus | null;
}) {
  const { user } = useAuth();
  const conversation = useQuery({
    queryKey: conversationApi.CONVERSATION_KEY(projectId, candidateId),
    queryFn: ({ signal }) => conversationApi.getConversation(projectId, candidateId, signal),
  });
  const [kinds, setKinds] = useState<ReadonlySet<TimelineKind>>(() => new Set());
  const [shown, setShown] = useState(FIRST_SHOWN);

  const lines = conversation.data ? linesOf(conversation.data.events, past) : [];
  const visible = lines.filter((line) => matches(line, kinds));
  const kindOptions: FilterMenuOption[] = KINDS.map(({ kind, label, icon }) => ({
    key: kind,
    label,
    visual: icon ? (
      <KindTile icon={icon} size="dot" />
    ) : (
      <span aria-hidden="true" className="grid size-[22px] flex-none place-items-center">
        <NoteKindMark kind={kind} size={14} />
      </span>
    ),
    count: lines.filter((line) => kindOfLine(line) === kind).length,
  })).filter((option) => option.count > 0 || kinds.has(option.key as TimelineKind));
  const isFiltering = kinds.size > 0;
  const chooseKinds = (next: ReadonlySet<string>) => {
    setKinds(new Set(next as ReadonlySet<TimelineKind>));
    setShown(FIRST_SHOWN);
  };
  const focusIndex = focus ? visible.findIndex((line) => noteIdsOfLine(line).includes(focus.noteId)) : -1;
  // A link to a note further down opens the timeline as far as that note, once; Show less still folds it after.
  const [widenedFor, setWidenedFor] = useState<number | null>(null);
  if (focus && focusIndex >= 0 && widenedFor !== focus.at) {
    setWidenedFor(focus.at);
    if (focusIndex >= shown) setShown(focusIndex + 1);
  }
  const page = visible.slice(0, shown);

  useEffect(() => {
    if (!focus || !conversation.isSuccess) return;
    const card = document.getElementById(noteAnchorOf(focus.noteId));
    card?.scrollIntoView({ block: "center", behavior: "smooth" });
    card?.focus({ preventScroll: true });
  }, [focus, conversation.isSuccess]);

  return (
    <section aria-label="History" data-activity-root className="flex flex-col gap-3">
      <div className="flex items-center gap-2">
        <h3 className="type-micro-label text-u-text3">History</h3>
        {conversation.isSuccess && lines.length > 0 && (
          <>
            <span className="ms-auto font-mono text-meta text-u-text3">
              {isFiltering ? `${visible.length} of ${lines.length}` : lines.length}
            </span>
            <ActivityFilterMenu
              label="Filter by kind"
              heading="Show"
              clearLabel="Show all"
              options={kindOptions}
              selected={kinds}
              onChange={chooseKinds}
              summary={<KindSummary selected={kinds} options={kindOptions} />}
              footnote="Tick several to combine, or choose Only. Nothing ticked shows everything."
            />
            {isFiltering && (
              <button
                type="button"
                onClick={() => chooseKinds(new Set())}
                aria-label="Clear filters"
                title="Clear filters"
                className="grid size-6 place-items-center rounded-md border border-u-border bg-u-bg text-u-text2 hover:border-u-border-strong hover:text-u-text"
              >
                <Icon d={ICONS.x} size={12} />
              </button>
            )}
          </>
        )}
      </div>

      {conversation.isError ? (
        <p className="text-note text-u-text3">{messageFor(conversation.error)}</p>
      ) : conversation.isPending ? (
        <LinesSkeleton />
      ) : visible.length === 0 ? (
        <EmptyTimeline firstName={firstName} filtered={isFiltering} />
      ) : (
        <>
          <ol className="flex flex-col gap-2.5">
            {page.map((line, index) => (
              <TimelineRow
                key={line.key}
                line={line}
                month={monthOf(line.at) !== (index > 0 ? monthOf(page[index - 1].at) : null) ? monthOf(line.at) : null}
                projectId={projectId}
                candidateId={candidateId}
                firstName={firstName}
                executive={executive}
                viewerUserId={user?.id ?? null}
                callMenu={line.kind === "call" ? past.menuOf(line.meeting) : null}
                flashAt={focus && noteIdsOfLine(line).includes(focus.noteId) ? focus.at : null}
              />
            ))}
          </ol>
          {(visible.length > page.length || page.length > FIRST_SHOWN) && (
            <div className="flex gap-4 text-note">
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
            <p className="text-meta text-u-text3">Older history is in the Activity tab.</p>
          )}
        </>
      )}
    </section>
  );
}

function KindSummary({ selected, options }: { selected: ReadonlySet<string>; options: readonly FilterMenuOption[] }) {
  const chosen = options.filter((option) => selected.has(option.key));
  if (chosen.length === 0) {
    return (
      <>
        <Icon d={ICONS.filter} size={14} className="flex-none text-u-text3" />
        <span className="truncate">All activity</span>
      </>
    );
  }
  return (
    <>
      {chosen[0].visual}
      <span className="truncate">
        {chosen[0].label}
        {chosen.length > 1 && <span className="opacity-75"> +{chosen.length - 1}</span>}
      </span>
    </>
  );
}

function TimelineRow({
  line,
  month,
  projectId,
  candidateId,
  firstName,
  executive,
  viewerUserId,
  callMenu,
  flashAt,
}: {
  line: Line;
  month: string | null;
  projectId: string;
  candidateId: string;
  firstName: string;
  executive: ActivityExecutive;
  viewerUserId: string | null;
  callMenu: ReactNode;
  flashAt: number | null;
}) {
  return (
    <li className="flex flex-col gap-2.5">
      {month && (
        <div className="flex items-center gap-2.5 pt-1">
          <span className="type-micro-label text-u-text3">{month}</span>
          <span aria-hidden="true" className="h-px flex-1 bg-u-border" />
        </div>
      )}
      {line.kind === "note" ? (
        <ul>
          <PositionNoteCard
            projectId={projectId}
            candidateId={candidateId}
            note={line.note}
            flashAt={flashAt}
            lead={{
              mark: <EntryLeadMark tile={tileOfNote(line.note)} />,
              heading: line.note.title ?? NOTE_HEADINGS[line.note.kind],
            }}
          />
        </ul>
      ) : line.kind === "call" ? (
        <CallCard
          projectId={projectId}
          candidateId={candidateId}
          meeting={line.meeting}
          recordedNote={line.recordedNote}
          loggedNotes={line.loggedNotes}
          executive={executive}
          menu={callMenu}
          flashAt={flashAt}
        />
      ) : (
        <MessageCard
          event={line.event}
          firstName={firstName}
          executive={executive}
          projectId={projectId}
          viewerUserId={viewerUserId}
        />
      )}
    </li>
  );
}

function MessageCard({
  event,
  firstName,
  executive,
  projectId,
  viewerUserId,
}: {
  event: Extract<ConversationEvent, { type: "MESSAGE" }>;
  firstName: string;
  executive: ActivityExecutive;
  projectId: string;
  viewerUserId: string | null;
}) {
  const face = eventFaceOf(event, firstName, projectId);
  const actor = actorOfEvent(event);
  const address = addressLineOf(event.message);
  return (
    <OutreachEntryCard
      label={face.title}
      tile={tileOfEvent(event)}
      title={face.title}
      at={event.at}
      actor={
        <EntryActor actor={actor} executive={executive} name={actor.kind === "automation" ? "Uncava" : undefined} />
      }
      byline={address && <span className="min-w-0 truncate text-u-text3">{address}</span>}
      footer={face.context}
    >
      <ConversationEventBody event={event} title={face.title} projectId={projectId} viewerUserId={viewerUserId} />
    </OutreachEntryCard>
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
  executive,
  menu,
  flashAt,
}: {
  projectId: string;
  candidateId: string;
  meeting: Meeting;
  recordedNote: PersonNote | null;
  loggedNotes: PersonNote[];
  executive: ActivityExecutive;
  menu: ReactNode;
  flashAt: number | null;
}) {
  const [reading, setReading] = useState(false);
  const title = meeting.title ?? "Call";
  const when = [meeting.videoProvider, meetingWhenOf(meeting.startsAt, meeting.endsAt)].filter(Boolean).join(" · ");
  // A call known only from its recording carries no calendar owner; its notes are filed in the sender's name.
  const calendarOwner = actorOfMeeting(meeting);
  const owner = calendarOwner.kind === "user" || !recordedNote ? calendarOwner : actorOfNote(recordedNote);
  return (
    <OutreachEntryCard
      label={title}
      id={recordedNote ? noteAnchorOf(recordedNote.id) : undefined}
      tabIndex={recordedNote ? -1 : undefined}
      tile={tileOfMeeting(meeting, recordedNote !== null)}
      title={title}
      badge={recordedNote && <RecordedBadge />}
      menu={menu}
      actor={owner.kind === "user" && <EntryActor actor={owner} executive={executive} />}
      byline={<span className="font-mono text-meta text-u-text3">{when}</span>}
    >
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
    </OutreachEntryCard>
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
        Emails you send, replies, the calls, LinkedIn and WhatsApp messages and meetings you log, and recorded calls with their
        notes all land here, newest first.
      </p>
    </div>
  );
}

/** Every note a line holds: a logged note, or a call's Notetaker notes and those written up about it by hand. */
function noteIdsOfLine(line: Line): string[] {
  if (line.kind === "note") return [line.note.id];
  if (line.kind === "call") {
    return [...(line.recordedNote ? [line.recordedNote.id] : []), ...line.loggedNotes.map((note) => note.id)];
  }
  return [];
}

/** A logged note's heading where it was given no title of its own. */
const NOTE_HEADINGS: Record<PersonNote["kind"], string> = {
  general: "Note",
  call: "Call",
  meeting: "Meeting",
  email: "Email",
  linkedin: "LinkedIn",
  whatsapp: "WhatsApp",
};

const MONTH = new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" });

function monthOf(isoInstant: string): string {
  const at = new Date(isoInstant);
  return Number.isNaN(at.getTime()) ? "" : MONTH.format(at);
}
