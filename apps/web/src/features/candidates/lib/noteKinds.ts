import type { PersonNoteKind } from "../api/types";

/** The four kinds a note is filed as, in the composer's order, each with the prompt it opens on. */
export const NOTE_KINDS: { value: PersonNoteKind; label: string; placeholder: string }[] = [
  { value: "general", label: "Note", placeholder: "What your team should know about this person…" },
  { value: "call", label: "Call", placeholder: "What was said on the call, and what happens next…" },
  { value: "meeting", label: "Meeting", placeholder: "Where you met, what came out of it…" },
  { value: "email", label: "Email", placeholder: "What was sent or received, and the reply…" },
];

export function noteKindLabel(kind: PersonNoteKind): string {
  return NOTE_KINDS.find((option) => option.value === kind)?.label ?? "Note";
}
