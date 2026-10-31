// Ported from apps/web/src/features/candidates/lib/noteKinds.ts; keep the two in step.
import type { NoteKind } from "../../../api/types";
import { ICONS } from "../../lib/icons";

/** The kinds a note is filed as, in the composer's order, each with the prompt it opens on. */
export const NOTE_KINDS: { value: NoteKind; label: string; placeholder: string }[] = [
  { value: "general", label: "Note", placeholder: "What your team should know…" },
  { value: "call", label: "Call", placeholder: "What was said on the call, and what happens next…" },
  { value: "meeting", label: "Meeting", placeholder: "Where you met, what came out of it…" },
  { value: "email", label: "Email", placeholder: "What was sent or received, and the reply…" },
  { value: "linkedin", label: "LinkedIn", placeholder: "What was said on LinkedIn: the message, the request, the reply…" },
];

/** The panel sits on LinkedIn, so what a consultant writes there is most often a LinkedIn exchange. The extension's own. */
export const DEFAULT_NOTE_KIND: NoteKind = "linkedin";

/** The stroke glyph of each kind but LinkedIn, whose mark is the network's own. */
export const NOTE_GLYPHS: Record<Exclude<NoteKind, "linkedin">, string> = {
  general: ICONS.fileText,
  call: ICONS.phoneCall,
  meeting: ICONS.video,
  email: ICONS.mail,
};

export function noteKindLabel(kind: NoteKind): string {
  return NOTE_KINDS.find((option) => option.value === kind)?.label ?? "Note";
}

export function noteKindPlaceholder(kind: NoteKind): string {
  return NOTE_KINDS.find((option) => option.value === kind)?.placeholder ?? "";
}

/** A note's kind as the API carries it; an unknown one is a general note. */
export function noteKindOf(value: string | null | undefined): NoteKind {
  const kind = (value ?? "general").toLowerCase();
  return NOTE_KINDS.find((option) => option.value === kind)?.value ?? "general";
}
