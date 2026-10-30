import type { NoteKind } from "../../../api/types";

/**
 * Whether the API takes a LinkedIn note yet. The backend is adding `linkedin` to the person's and the company's note
 * kinds; until it ships, a LinkedIn note would be refused, so the chip is not offered. Flip this with that release.
 */
export const LINKEDIN_NOTE_KIND_OFFERED = false;

const ALL_KINDS: { value: NoteKind; label: string; placeholder: string }[] = [
  { value: "general", label: "Note", placeholder: "What your team should know…" },
  { value: "call", label: "Call", placeholder: "What was said on the call, and what happens next…" },
  { value: "meeting", label: "Meeting", placeholder: "Where you met, what came out of it…" },
  { value: "email", label: "Email", placeholder: "What was sent or received, and the reply…" },
  { value: "linkedin", label: "LinkedIn", placeholder: "What was said on LinkedIn — a message, a connection…" },
];

/** The kinds a note is filed as, in the composer's order, each with the prompt it opens on. */
export const NOTE_KINDS = ALL_KINDS.filter((kind) => kind.value !== "linkedin" || LINKEDIN_NOTE_KIND_OFFERED);

export function noteKindLabel(kind: NoteKind): string {
  return ALL_KINDS.find((option) => option.value === kind)?.label ?? "Note";
}

export function noteKindPlaceholder(kind: NoteKind): string {
  return ALL_KINDS.find((option) => option.value === kind)?.placeholder ?? "";
}
