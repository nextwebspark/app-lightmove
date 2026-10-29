import { useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, buttonClassName } from "../../../../components/ui";
import { LinesSkeleton } from "../../../../components/ui/Skeleton";
import { Modal } from "../../../../components/ui/Modal";
import { messageFor } from "../../../../lib/errorCodes";
import { NoteText } from "../../../notes/components/NoteText";
import { getConversation, CONVERSATION_KEY, type ConversationMessage } from "../../../outreach/api/conversationApi";
import { getMeetings, MEETINGS_KEY, type Meeting } from "../../../outreach/api/meetingApi";
import { CANDIDATE_TASKS_KEY, getCandidateTasks } from "../../../tasks/api/tasksApi";
import * as personCrmApi from "../../api/personCrmApi";
import type { PersonDocument, PersonDocumentVersion, PersonNoteKind, PersonTimelineEntry } from "../../api/types";
import type { DrawerTab } from "../../lib/drawerTabs";
import {
  actorOf,
  automationCauseOf,
  doorOf,
  fieldLabel,
  fullDateTime,
  iconOf,
  shortDateTime,
  verbOf,
  actorNameOf,
  actorRoleOf,
  type ActivityExecutive,
  type TaskState,
} from "../../lib/activityPresentation";
import { STOP_REASONS, statusLabel } from "../../lib/candidateActivity";
import { noteKindLabel } from "../../lib/noteKinds";
import { StatusPill } from "./ActivityItems";
import { ActorFace, KindTile } from "./ActivityVisuals";
import { formatInstantTime } from "../../../../lib/datetime";

export interface ActivityDestinations {
  onShowNote: (noteId: string) => void;
  onOpenTab: (tab: DrawerTab) => void;
  onOpenMeetings: () => void;
  onOpenTask: (taskId: string) => void;
  onPreviewDocument: (document: PersonDocument, version: PersonDocumentVersion) => void;
}

/**
 * One history line opened: its whole story read-only, under the same who-and-when the card carried, with a way to the
 * tab where it can be changed. What the line points at may have gone since (a note deleted, a call cancelled); the
 * dialog says so rather than failing, and a read that fails says why with the line's own facts still shown.
 */
export function ActivityDetailDialog({
  entry,
  onClose,
  projectId,
  candidateId,
  executive,
  documents,
  destinations,
  taskState,
}: {
  entry: PersonTimelineEntry;
  onClose: () => void;
  projectId: string;
  candidateId: string;
  executive: ActivityExecutive;
  documents: readonly PersonDocument[] | undefined;
  destinations: ActivityDestinations;
  /** Where the line's task stands now by the history, for a done task older than the task list returns. */
  taskState?: TaskState;
}) {
  const actor = actorOf(entry);
  const name = actorNameOf(actor, executive);
  const go = (action: () => void) => () => {
    onClose();
    action();
  };
  const way = wayOf(entry, projectId, candidateId, destinations);
  return (
    <Modal
      open
      onClose={onClose}
      title={titleOf(entry)}
      className="md:w-[560px]"
      footer={
        <div className="flex items-center gap-2">
          <span className="me-auto text-note text-u-text3">Read-only. Changes are made on the tab itself.</span>
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            Close
          </Button>
          {way?.kind === "action" && (
            <Button type="button" size="sm" onClick={go(way.run)}>
              {way.label}
              <Icon d={ICONS.arrowRight} size={14} />
            </Button>
          )}
          {way?.kind === "link" && (
            <Link to={way.to} onClick={onClose} className={buttonClassName("primary", "whitespace-nowrap px-3.5 py-[7px] text-body")}>
              {way.label}
              <Icon d={ICONS.arrowRight} size={14} />
            </Link>
          )}
        </div>
      }
    >
      <div className="flex flex-col gap-4 pb-4">
        <div className="flex flex-wrap items-center gap-3 rounded-xl border border-u-border bg-u-bg px-3 py-2.5">
          <KindTile icon={iconOf(entry)} size="head" />
          <ActorFace actor={actor} executive={executive} projectId={projectId} size="lg" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-body font-semibold text-u-text">{name}</p>
            <p className="truncate text-note text-u-text3">{actorRoleOf(actor)}</p>
          </div>
          <div className="text-end">
            <time dateTime={entry.occurredAt} className="block font-mono text-meta text-u-text2">
              {fullDateTime(entry.occurredAt)}
            </time>
            <span className="block text-meta text-u-text3">
              {entry.projectId === null ? "About the person · every position" : (entry.projectTitle ?? "This position")}
            </span>
          </div>
        </div>
        <p className="text-body text-u-text2">
          <span className="font-semibold text-u-text">{name}</span> {verbOf(entry)}
          {automationCauseOf(entry) && <span className="text-u-text3"> · {automationCauseOf(entry)}</span>}
        </p>
        <DetailBody
          entry={entry}
          projectId={projectId}
          candidateId={candidateId}
          executive={executive}
          documents={documents}
          taskState={taskState}
          onPreviewDocument={(document, version) => {
            onClose();
            destinations.onPreviewDocument(document, version);
          }}
        />
      </div>
    </Modal>
  );
}

function DetailBody({
  entry,
  projectId,
  candidateId,
  executive,
  documents,
  taskState,
  onPreviewDocument,
}: {
  entry: PersonTimelineEntry;
  projectId: string;
  candidateId: string;
  executive: ActivityExecutive;
  documents: readonly PersonDocument[] | undefined;
  taskState: TaskState | undefined;
  onPreviewDocument: (document: PersonDocument, version: PersonDocumentVersion) => void;
}) {
  const { details } = entry;
  switch (entry.kind) {
    case "NOTE_ADDED":
    case "NOTE_EDITED":
    case "NOTE_RESTORED":
    case "NOTE_REMOVED":
      return <NoteDetail entry={entry} projectId={projectId} candidateId={candidateId} />;
    case "EMAIL_SENT":
    case "EMAIL_REPLIED":
      return <EmailDetail entry={entry} projectId={projectId} candidateId={candidateId} executive={executive} />;
    case "MEETING_BOOKED":
    case "MEETING_RESCHEDULED":
    case "MEETING_CANCELLED":
      return <MeetingDetail entry={entry} projectId={projectId} candidateId={candidateId} />;
    case "STATUS_CHANGED":
      return (
        <>
          <div className="flex items-center justify-center gap-3 rounded-xl border border-u-border bg-u-bg p-4">
            {details.from && <StatusPill label={statusLabel(details.from, details.fromLabel)} />}
            {details.from && <Icon d={ICONS.arrowRight} size={16} className="text-u-text3" />}
            <StatusPill label={statusLabel(details.to, details.toLabel)} isCurrent />
          </div>
          <Facts
            rows={[
              ["How", details.automation ? "Moved automatically by the pipeline's rule" : "Moved by hand"],
              ["Set off by", details.automation ? causeOf(entry) : null],
            ]}
          />
        </>
      );
    case "PROFILE_EDITED":
    case "UPDATED_FROM_CALL":
      return <ChangesDetail entry={entry} />;
    case "DOCUMENT_ADDED":
    case "DOCUMENT_VERSION_ADDED":
    case "DOCUMENT_REMOVED":
    case "DOCUMENT_VERSION_REMOVED":
      return <DocumentDetail entry={entry} documents={documents} onPreview={onPreviewDocument} />;
    case "TASK_CREATED":
    case "TASK_COMPLETED":
    case "TASK_REOPENED":
    case "TASK_REASSIGNED":
    case "TASK_CANCELLED":
    case "NEXT_ACTION_SET":
      return <TaskDetail entry={entry} projectId={projectId} candidateId={candidateId} state={taskState} />;
    case "CONTACT_FOUND":
      return (
        <>
          <Facts
            rows={[
              ["Channel", details.channel === "PHONE" ? "Phone" : "Email"],
              ["Result", Number(details.found ?? "0") > 0 ? `${details.found} found` : "None found. The miss is remembered, so nobody pays to ask again."],
              ["Provider", details.via === "CONTACTOUT" || !details.via ? "ContactOut" : details.via],
            ]}
          />
          <Callout>The history keeps no address or number. They are on the Contact tab.</Callout>
        </>
      );
    case "CONTACTS_EDITED":
      return (
        <>
          <Facts rows={[["On file now", `${details.emails ?? "?"} emails · ${details.phones ?? "?"} phones`]]} />
          <Callout>The history keeps counts only, so an address deleted from the record never lingers here.</Callout>
        </>
      );
    case "OUTREACH_ENROLLED":
    case "OUTREACH_STOPPED":
      return (
        <Facts
          rows={[
            ["Sequence", details.sequence ?? null],
            ["Why it stopped", entry.kind === "OUTREACH_STOPPED" ? stopReasonOf(entry) : null],
          ]}
        />
      );
    case "ADDED_TO_POOL":
    case "MAPPED":
      return (
        <Facts
          rows={[
            ["How", doorOf(details.door)],
            ["Matched", entry.kind === "MAPPED" ? "Already in your candidates, so added rather than duplicated" : null],
          ]}
        />
      );
    case "TAGGED":
    case "UNTAGGED":
      return <Facts rows={[["Tag", details.tag ?? "A tag that has since been retired"]]} />;
    case "OWNER_CHANGED":
      return <Facts rows={[["Owner", details.owner ?? "Nobody"]]} />;
    case "AI_ASSESSED":
      return (
        <Callout>
          {details.scope === "BACKGROUND"
            ? "The AI proposed what was missing from the background. Each value it filled is marked AI until a researcher changes it."
            : "The AI drafted a scorecard against this position's brief. A consultant reviews it on the Assessment tab."}
        </Callout>
      );
    case "TRIAGE_STATUS_CHANGED":
      return <Facts rows={[["With their company", withCompanyOf(entry)]]} />;
  }
  return null;
}

function NoteDetail({ entry, projectId, candidateId }: { entry: PersonTimelineEntry; projectId: string; candidateId: string }) {
  const notes = useQuery({
    queryKey: personCrmApi.PERSON_NOTES_KEY(projectId, candidateId),
    queryFn: ({ signal }) => personCrmApi.getPersonNotes(projectId, candidateId, signal),
    enabled: entry.kind !== "NOTE_REMOVED",
  });
  if (entry.kind === "NOTE_REMOVED") return <Callout>This note was deleted. The history names it and never keeps a copy.</Callout>;
  if (notes.isPending) return <LinesSkeleton lines={4} />;
  if (notes.isError) return <ReadFailed error={notes.error} onRetry={() => void notes.refetch()} />;
  const note = notes.data.find((each) => each.id === entry.details.noteId);
  if (!note) return <Callout>This note was deleted since, or is no longer readable from this position.</Callout>;
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-1.5">
        <Pill>{noteTitleOf(note.kind)}</Pill>
        {note.pinned && <Pill>Pinned</Pill>}
        <Pill>{note.projectId === null ? "About the person · every position" : "This position"}</Pill>
      </div>
      <div className="max-h-[40dvh] overflow-y-auto rounded-xl border border-u-border bg-u-bg p-3.5">
        <NoteText body={note.body} format={note.format} className="text-body text-u-text" />
      </div>
      {note.editedAt && (
        <p className="text-meta text-u-text3">
          Last edited by {note.editedByName ?? "someone"} · {fullDateTime(note.editedAt)}
        </p>
      )}
    </div>
  );
}

/** "Call note", "Meeting note"; a general one is just "Note". */
function noteTitleOf(kind: PersonNoteKind): string {
  return kind === "general" ? noteKindLabel(kind) : `${noteKindLabel(kind)} note`;
}

/** Why a sequence ended, as its `reason` names it; a consultant's own Stop carries none. */
function stopReasonOf(entry: PersonTimelineEntry): string | null {
  const { reason } = entry.details;
  return reason ? (STOP_REASONS[reason] ?? null) : "Stopped by hand";
}

function withCompanyOf(entry: PersonTimelineEntry): string {
  return String(entry.details.withCompany) === "true" ? "Yes, the company moved too" : "No, them alone";
}

function EmailDetail({
  entry,
  projectId,
  candidateId,
  executive,
}: {
  entry: PersonTimelineEntry;
  projectId: string;
  candidateId: string;
  executive: ActivityExecutive;
}) {
  const messageId = entry.details.messageId;
  const conversation = useQuery({
    queryKey: CONVERSATION_KEY(projectId, candidateId),
    queryFn: ({ signal }) => getConversation(projectId, candidateId, signal),
    enabled: Boolean(messageId),
  });
  const lineFacts = (
    <Facts
      rows={[
        ["Sequence", entry.details.sequence ?? null],
        ["Step", entry.details.step ?? null],
      ]}
    />
  );
  if (!messageId) {
    return (
      <>
        {lineFacts}
        <Callout>The email itself is in the conversation on the Outreach page.</Callout>
      </>
    );
  }
  if (conversation.isPending) return <LinesSkeleton lines={5} />;
  if (conversation.isError) {
    return (
      <>
        {lineFacts}
        <ReadFailed error={conversation.error} onRetry={() => void conversation.refetch()} />
      </>
    );
  }
  const message = conversation.data.events.flatMap((event) => (event.type === "MESSAGE" ? [event.message] : []))
    .find((each) => each.id === messageId);
  if (!message) {
    return (
      <>
        {lineFacts}
        <Callout>This email is no longer in the conversation's history.</Callout>
      </>
    );
  }
  return <EmailBody message={message} executive={executive} />;
}

function EmailBody({ message, executive }: { message: ConversationMessage; executive: ActivityExecutive }) {
  const isInbound = message.direction === "INBOUND";
  return (
    <div className="flex flex-col gap-3">
      <dl className="grid grid-cols-[56px_minmax(0,1fr)] gap-x-3 gap-y-1 border-b border-u-border pb-3 text-note">
        <dt className="text-u-text3">From</dt>
        <dd className="truncate text-u-text">{isInbound ? executive.fullName : (message.senderName ?? message.fromAddress ?? "—")}</dd>
        <dt className="text-u-text3">To</dt>
        <dd className="truncate text-u-text">{isInbound ? (message.senderName ?? "Your team") : executive.fullName}</dd>
        <dt className="text-u-text3">{isInbound ? "Received" : "Sent"}</dt>
        <dd className="font-mono text-meta text-u-text">{fullDateTime(message.at)}</dd>
        {message.sequenceName && (
          <>
            <dt className="text-u-text3">Sequence</dt>
            <dd className="text-u-text2">
              {message.sequenceName}
              {message.stepNumber ? ` · step ${message.stepNumber}${message.stepCount ? ` of ${message.stepCount}` : ""}` : ""}
            </dd>
          </>
        )}
      </dl>
      {message.subject && <p className="text-body font-semibold text-u-text">{message.subject}</p>}
      {message.kind === "BOUNCE" ? (
        <Callout>The recipient's server rejected the message.</Callout>
      ) : message.text ? (
        <div className="max-h-[40dvh] overflow-y-auto">
          {message.format === "MARKDOWN" ? (
            <NoteText body={message.text} format="markdown" className="text-body text-u-text" />
          ) : (
            <p className="whitespace-pre-wrap text-body text-u-text">{message.text}</p>
          )}
        </div>
      ) : (
        <Callout>What they wrote isn't kept for this mailbox.</Callout>
      )}
      {message.attachments.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {message.attachments.map((attachment) => (
            <span key={attachment.id} className="inline-flex items-center gap-1.5 rounded-lg border border-u-border bg-u-surface px-2.5 py-1.5 text-note text-u-text2">
              <Icon d={ICONS.paperclip} size={13} />
              {attachment.fileName}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function MeetingDetail({ entry, projectId, candidateId }: { entry: PersonTimelineEntry; projectId: string; candidateId: string }) {
  const isCancelled = entry.kind === "MEETING_CANCELLED";
  const meetings = useQuery({
    queryKey: MEETINGS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => getMeetings(projectId, candidateId, signal),
    enabled: !isCancelled,
  });
  const lineFacts = (
    <Facts
      rows={[
        [isCancelled ? "Was booked for" : "When", shortDateTime(entry.details.startsAt)],
        ["Moved from", shortDateTime(entry.details.previousStartsAt)],
        ["Booked", String(entry.details.viaLink) === "true" ? "By the executive, through the booking link" : null],
      ]}
    />
  );
  if (isCancelled) {
    return (
      <>
        {lineFacts}
        <Callout>The call was cancelled and the executive was told.</Callout>
      </>
    );
  }
  if (meetings.isPending) return <LinesSkeleton lines={4} />;
  if (meetings.isError) {
    return (
      <>
        {lineFacts}
        <ReadFailed error={meetings.error} onRetry={() => void meetings.refetch()} />
      </>
    );
  }
  const meeting = findMeeting([...meetings.data.upcoming, ...meetings.data.past], entry.details.startsAt);
  if (!meeting) {
    return (
      <>
        {lineFacts}
        <Callout>This call is no longer on the calendar at that time. It may have been moved or removed since.</Callout>
      </>
    );
  }
  const minutes = Math.round((new Date(meeting.endsAt).getTime() - new Date(meeting.startsAt).getTime()) / 60_000);
  return (
    <Facts
      rows={[
        ["Call", meeting.title],
        ["When", `${shortDateTime(meeting.startsAt)} – ${formatInstantTime(meeting.endsAt) ?? "—"}`],
        ["Duration", Number.isFinite(minutes) && minutes > 0 ? `${minutes} minutes` : null],
        ["Moved from", shortDateTime(entry.details.previousStartsAt)],
        ["Video", meeting.videoProvider],
        ["Calendar", meeting.ownerName ? `${meeting.ownerName}'s` : null],
        ["Booked", meeting.viaLink ? "By the executive, through the booking link" : meeting.bookedInUncava ? "In Uncava" : "On the calendar"],
      ]}
    />
  );
}

/** A meeting row is keyed by the calendar, never by the line: the line keeps its start, which is how it is found. */
function findMeeting(meetings: readonly Meeting[], startsAt: string | undefined): Meeting | null {
  if (!startsAt) return null;
  const wanted = new Date(startsAt).getTime();
  if (Number.isNaN(wanted)) return null;
  return meetings.find((meeting) => new Date(meeting.startsAt).getTime() === wanted) ?? null;
}

function ChangesDetail({ entry }: { entry: PersonTimelineEntry }) {
  const changes = entry.changes ?? [];
  const fromCall = entry.kind === "UPDATED_FROM_CALL";
  return (
    <div className="flex flex-col gap-3">
      {fromCall && (
        <Facts
          rows={[
            ["Call", entry.details.call ?? null],
            ["Held", shortDateTime(entry.details.callAt)],
          ]}
        />
      )}
      {changes.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-body">
            <thead>
              <tr className="text-start">
                <th className="type-micro-label pb-2 pe-3 text-start text-u-text3">Field</th>
                <th className="type-micro-label pb-2 pe-3 text-start text-u-text3">Before</th>
                <th className="type-micro-label pb-2 text-start text-u-text3">After</th>
              </tr>
            </thead>
            <tbody>
              {changes.map((change) => (
                <tr key={change.field} className="border-t border-u-border align-top">
                  <td className="py-2 pe-3 text-u-text2">{fieldLabel(change.field)}</td>
                  {change.from === null && change.to === null ? (
                    <td colSpan={2} className="py-2 text-u-text3">
                      Edited
                    </td>
                  ) : (
                    <>
                      <td className="py-2 pe-3 text-u-text3 line-through decoration-u-offlimits">{change.from ?? "—"}</td>
                      <td className="py-2 font-medium text-u-text">{change.to ?? "Cleared"}</td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : entry.details.fields ? (
        <Facts rows={[["Fields", entry.details.fields.split(",").filter(Boolean).map(fieldLabel).join(", ")]]} />
      ) : (
        <Callout>
          {String(entry.details.backgroundConfirmed) === "true"
            ? "The AI's background values were confirmed as the researcher's own."
            : "No profile field this history tracks changed in this save (a position's own columns are not listed), or it was saved before Uncava kept what changed."}
        </Callout>
      )}
    </div>
  );
}

function DocumentDetail({
  entry,
  documents,
  onPreview,
}: {
  entry: PersonTimelineEntry;
  documents: readonly PersonDocument[] | undefined;
  onPreview: (document: PersonDocument, version: PersonDocumentVersion) => void;
}) {
  const isRemoval = entry.kind === "DOCUMENT_REMOVED" || entry.kind === "DOCUMENT_VERSION_REMOVED";
  const document = documents?.find((each) => each.id === entry.details.documentId) ?? null;
  const version = document ? versionOf(document, entry.details.version) : null;
  return (
    <div className="flex flex-col gap-3">
      <Facts
        rows={[
          ["Document", entry.details.document ?? document?.title ?? null],
          ["Version", entry.details.version ?? null],
          ["File", version?.fileName ?? null],
        ]}
      />
      {isRemoval ? (
        <Callout>Deleted. The history keeps no copy of the file.</Callout>
      ) : documents === undefined ? (
        <LinesSkeleton lines={2} />
      ) : document && version ? (
        <Button type="button" variant="secondary" size="sm" className="w-fit" onClick={() => onPreview(document, version)}>
          <Icon d={ICONS.eye} size={14} />
          Preview
        </Button>
      ) : (
        <Callout>This document, or this version of it, was deleted since.</Callout>
      )}
    </div>
  );
}

/**
 * The file a line names: its own version, or the latest where the line names none. A version deleted since is
 * nothing, never another file shown under its number.
 */
function versionOf(document: PersonDocument, number: string | undefined): PersonDocumentVersion | null {
  if (number === undefined) return document.versions[0] ?? null;
  const wanted = Number(number);
  return document.versions.find((version) => version.versionNo === wanted) ?? null;
}

function TaskDetail({
  entry,
  projectId,
  candidateId,
  state,
}: {
  entry: PersonTimelineEntry;
  projectId: string;
  candidateId: string;
  state: TaskState | undefined;
}) {
  const tasks = useQuery({
    queryKey: CANDIDATE_TASKS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => getCandidateTasks(projectId, candidateId, signal),
    enabled: Boolean(entry.details.taskId),
  });
  const task = tasks.data ? [...tasks.data.open, ...tasks.data.done].find((each) => each.id === entry.details.taskId) : undefined;
  return (
    <div className="flex flex-col gap-3">
      <Facts
        rows={[
          ["Task", entry.details.title ?? entry.details.text ?? null],
          ["Due", shortDateTime(task?.dueAt ?? entry.details.dueAt)],
          ["Assigned to", task?.assignee.fullName ?? entry.details.assignee ?? null],
          ["Was with", entry.details.previousAssignee ?? null],
          ["Now", TASK_STATES[task?.status ?? state ?? ""] ?? null],
        ]}
      />
      {tasks.isError && <ReadFailed error={tasks.error} onRetry={() => void tasks.refetch()} />}
    </div>
  );
}

const TASK_STATES: Record<string, string> = {
  open: "Open",
  done: "Done",
  cancelled: "Cancelled",
};

type Way = { kind: "action"; label: string; run: () => void } | { kind: "link"; label: string; to: string };

/** Where the dialog's main button goes: the tab that holds the thing, or the conversation. */
function wayOf(entry: PersonTimelineEntry, projectId: string, candidateId: string, to: ActivityDestinations): Way | null {
  const conversation = `/projects/${projectId}/outreach?candidate=${candidateId}`;
  switch (entry.kind) {
    case "NOTE_ADDED":
    case "NOTE_EDITED":
    case "NOTE_RESTORED":
      return entry.details.noteId && entry.noteExcerpt !== null
        ? { kind: "action", label: "Open in Notes", run: () => to.onShowNote(entry.details.noteId) }
        : { kind: "action", label: "Open Notes", run: () => to.onOpenTab("notes") };
    case "EMAIL_SENT":
    case "EMAIL_REPLIED":
    case "OUTREACH_ENROLLED":
    case "OUTREACH_STOPPED":
      return { kind: "link", label: "Open conversation", to: conversation };
    case "MEETING_BOOKED":
    case "MEETING_RESCHEDULED":
    case "MEETING_CANCELLED":
    case "UPDATED_FROM_CALL":
      return { kind: "action", label: "Open in Meetings", run: to.onOpenMeetings };
    case "PROFILE_EDITED":
    case "RESEARCHED":
      return { kind: "action", label: "Open Career", run: () => to.onOpenTab("career") };
    case "CONTACT_FOUND":
    case "CONTACTS_EDITED":
    case "DO_NOT_CONTACT_SET":
    case "DO_NOT_CONTACT_CLEARED":
      return { kind: "action", label: "Open Contact", run: () => to.onOpenTab("contact") };
    case "DOCUMENT_ADDED":
    case "DOCUMENT_VERSION_ADDED":
    case "DOCUMENT_REMOVED":
    case "DOCUMENT_VERSION_REMOVED":
      return { kind: "action", label: "Open Documents", run: () => to.onOpenTab("documents") };
    case "AI_ASSESSED":
      return entry.details.scope === "BACKGROUND"
        ? { kind: "action", label: "Open Career", run: () => to.onOpenTab("career") }
        : { kind: "action", label: "Open Assessment", run: () => to.onOpenTab("assessment") };
    case "ASSESSMENT_REVIEWED":
      return { kind: "action", label: "Open Assessment", run: () => to.onOpenTab("assessment") };
    case "TASK_CREATED":
    case "TASK_COMPLETED":
    case "TASK_REOPENED":
    case "TASK_REASSIGNED":
    case "TASK_CANCELLED":
      return entry.details.taskId ? { kind: "action", label: "Open task", run: () => to.onOpenTask(entry.details.taskId) } : null;
  }
  return null;
}

/** The line's kind as the server writes it (`CALL`), read as a note's own kind (`call`); an unknown one is a general note. */
function noteKindOf(detail: string | undefined): PersonNoteKind {
  const kind = (detail ?? "general").toLowerCase();
  return kind === "call" || kind === "meeting" || kind === "email" ? kind : "general";
}

function titleOf(entry: PersonTimelineEntry): string {
  switch (entry.kind) {
    case "NOTE_ADDED":
    case "NOTE_EDITED":
    case "NOTE_RESTORED":
    case "NOTE_REMOVED":
      return noteTitleOf(noteKindOf(entry.details.kind));
    case "EMAIL_SENT":
      return "Email sent";
    case "EMAIL_REPLIED":
      return "Reply";
    case "MEETING_BOOKED":
      return "Call booked";
    case "MEETING_RESCHEDULED":
      return "Call moved";
    case "MEETING_CANCELLED":
      return "Call cancelled";
    case "STATUS_CHANGED":
      return `Status changed to ${statusLabel(entry.details.to, entry.details.toLabel)}`;
    case "PROFILE_EDITED":
      return entry.changes && entry.changes.length > 0
        ? `Profile edited · ${entry.changes.length} ${entry.changes.length === 1 ? "field" : "fields"}`
        : "Profile edited";
    case "UPDATED_FROM_CALL":
      return "Changes from a call";
    case "ADDED_TO_POOL":
    case "MAPPED":
      return entry.projectId === null ? "Added to your candidates" : "Added to this position";
  }
  const verb = verbOf(entry);
  return verb.charAt(0).toUpperCase() + verb.slice(1);
}

/** "Dana Aboud's email", "The reply": what set a rule off, as a value rather than the card's "after …" phrase. */
function causeOf(entry: PersonTimelineEntry): string | null {
  const cause = automationCauseOf(entry)?.replace(/^after /, "");
  return cause ? cause.charAt(0).toUpperCase() + cause.slice(1) : null;
}

function Facts({ rows }: { rows: [string, ReactNode | null][] }) {
  const shown = rows.filter(([, value]) => value !== null && value !== undefined && value !== "");
  if (shown.length === 0) return null;
  return (
    <dl className="grid grid-cols-[minmax(96px,max-content)_minmax(0,1fr)] gap-x-4 gap-y-2 text-body">
      {shown.map(([label, value]) => (
        <div key={label} className="contents">
          <dt className="text-u-text3">{label}</dt>
          <dd className="min-w-0 break-words text-u-text">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function Callout({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-xl border border-u-border bg-u-bg px-3 py-2.5 text-note text-u-text2">{children}</p>
  );
}

function Pill({ children }: { children: ReactNode }) {
  return <span className="inline-flex items-center rounded-full bg-u-raised px-2 py-px text-meta font-medium text-u-text2">{children}</span>;
}

function ReadFailed({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-u-offlimits-tint bg-u-offlimits-tint px-3 py-2.5 text-note text-u-offlimits">
      <span className="flex-1">Couldn't load the details. {messageFor(error)}</span>
      <button type="button" onClick={onRetry} className="font-medium underline-offset-2 hover:underline">
        Try again
      </button>
    </div>
  );
}
