import type { PersonNote } from "../../candidates/api/types";
import { isContactNote } from "../../candidates/lib/noteKinds";
import { recordingIdOfCall } from "./notetakerChip";
import type { ConversationEvent } from "../api/conversationApi";
import type { Meeting } from "../api/meetingApi";

/** What the timeline narrows to. */
export type TimelineFilter = "all" | "email" | "calls" | "linkedin" | "meetings";

/** One line of the drawer's Outreach timeline. */
export type TimelineLine =
  | { key: string; at: string; kind: "message"; event: Extract<ConversationEvent, { type: "MESSAGE" }> }
  | { key: string; at: string; kind: "note"; note: PersonNote }
  | {
      key: string;
      at: string;
      kind: "call";
      meeting: Meeting;
      /** The Notetaker's notes, filed from its recording. */
      recordedNote: PersonNote | null;
      /** Notes written up by hand about this meeting, oldest first. */
      loggedNotes: PersonNote[];
    };

/** The calls that are over and the note each became, as `MeetingsSection` hands them over. */
export interface PastCallNotes {
  meetings: Meeting[];
  noteIdOf: (meeting: Meeting) => string | null;
}

/**
 * Every line of the timeline, newest first: the conversation read's emails and contact notes, and the calls. Status and
 * run lines are the Activity tab's.
 */
export function linesOf(events: readonly ConversationEvent[], past: PastCallNotes): TimelineLine[] {
  const notesById = new Map<string, PersonNote>();
  for (const event of events) if (event.type === "NOTE") notesById.set(event.note.id, event.note);
  const pairedNoteIds = new Set<string>();
  const calls: TimelineLine[] = past.meetings.map((meeting) => {
    const noteId = past.noteIdOf(meeting);
    const recordedNote = noteId ? (notesById.get(noteId) ?? null) : null;
    if (recordedNote) pairedNoteIds.add(recordedNote.id);
    const loggedNotes = [...notesById.values()]
      .filter((note) => isAbout(note, meeting) && note.id !== recordedNote?.id)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    loggedNotes.forEach((note) => pairedNoteIds.add(note.id));
    return { key: `call:${meeting.id}`, at: meeting.startsAt, kind: "call", meeting, recordedNote, loggedNotes };
  });
  const rest: TimelineLine[] = [];
  for (const event of events) {
    if (event.type === "MESSAGE") rest.push({ key: `message:${event.message.id}`, at: event.at, kind: "message", event });
    else if (event.type === "NOTE" && isContactNote(event.note.kind) && !pairedNoteIds.has(event.note.id))
      rest.push({ key: `note:${event.note.id}`, at: event.at, kind: "note", note: event.note });
  }
  return [...rest, ...calls].sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime());
}

/** A note written up about this call: by its calendar meeting, or for a call known only from it, by its recording. */
function isAbout(note: PersonNote, call: Meeting): boolean {
  const recordingId = recordingIdOfCall(call.id);
  return recordingId ? note.recordingId === recordingId : note.meetingId === call.id;
}

export function matches(line: TimelineLine, filter: TimelineFilter): boolean {
  switch (filter) {
    case "all":
      return true;
    case "email":
      return line.kind === "message" || (line.kind === "note" && line.note.kind === "email");
    case "calls":
      return line.kind === "note" && line.note.kind === "call";
    case "linkedin":
      return line.kind === "note" && line.note.kind === "linkedin";
    case "meetings":
      return line.kind === "call" || (line.kind === "note" && line.note.kind === "meeting");
  }
}

