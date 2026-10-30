import { ICONS } from "../../../components/layout/Icon";
import type { PersonNoteKind } from "../api/types";

/** The kinds a note is filed as, in the composer's order, each with the prompt it opens on. */
export const NOTE_KINDS: { value: PersonNoteKind; label: string; placeholder: string }[] = [
  { value: "general", label: "Note", placeholder: "What your team should know about this person…" },
  { value: "call", label: "Call", placeholder: "What was said on the call, and what happens next…" },
  { value: "meeting", label: "Meeting", placeholder: "Where you met, what came out of it…" },
  { value: "email", label: "Email", placeholder: "What was sent or received, and the reply…" },
  { value: "linkedin", label: "LinkedIn", placeholder: "What was said on LinkedIn: the message, the request, the reply…" },
];

/** The stroke glyph of each kind but LinkedIn, whose mark is the network's own (`NoteKindMark`). */
export const NOTE_GLYPHS: Record<Exclude<PersonNoteKind, "linkedin">, string> = {
  general: ICONS.fileText,
  call: ICONS.phoneCall,
  meeting: ICONS.video,
  email: ICONS.mail,
};

export function noteKindLabel(kind: PersonNoteKind): string {
  return NOTE_KINDS.find((option) => option.value === kind)?.label ?? "Note";
}

/** A note's kind as a note carries it (`call`) or as a timeline line does (`CALL`); an unknown one is a general note. */
export function noteKindOf(value: string | null | undefined): PersonNoteKind {
  const kind = (value ?? "general").toLowerCase();
  return NOTE_KINDS.find((option) => option.value === kind)?.value ?? "general";
}
