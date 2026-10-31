import { formatInstantDate } from "../../../lib/format";
import type { TaskNote, TaskNoteKind } from "../api/types";

const NOTE_WORDS: Record<TaskNoteKind, string> = {
  general: "a note",
  call: "a call note",
  meeting: "a meeting note",
  email: "an email note",
  linkedin: "a LinkedIn note",
};

/** "a call note": how a task or a bell row names the note it came from. */
export function noteWordsOf(kind: TaskNoteKind): string {
  return NOTE_WORDS[kind];
}

/** "From a call note · 03 Oct 2026". */
export function fromNoteLabel(note: TaskNote): string {
  const date = formatInstantDate(note.writtenAt);
  return `From ${NOTE_WORDS[note.kind]}${date ? ` · ${date}` : ""}`;
}

/** "Linked to a call note · 03 Oct 2026". */
export function linkedNoteLabel(note: TaskNote): string {
  const date = formatInstantDate(note.writtenAt);
  return `Linked to ${NOTE_WORDS[note.kind]}${date ? ` · ${date}` : ""}`;
}
