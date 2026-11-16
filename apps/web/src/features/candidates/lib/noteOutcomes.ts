import type { NoteOutcome, PersonNoteKind } from "../api/types";

/** A call's outcomes, the sequence call step's own four (`Outreach.dc.html`), and a WhatsApp log's two. */
export const CALL_OUTCOMES: readonly { value: NoteOutcome; label: string }[] = [
  { value: "connected", label: "Connected" },
  { value: "voicemail", label: "Left voicemail" },
  { value: "no_answer", label: "No answer" },
  { value: "wrong_number", label: "Wrong number" },
];

export const WHATSAPP_OUTCOMES: readonly { value: NoteOutcome; label: string }[] = [
  { value: "sent", label: "Sent a message" },
  { value: "replied", label: "They replied" },
];

/** The outcomes a kind of log may say, or null for one that says none. */
export function outcomesOf(kind: PersonNoteKind): readonly { value: NoteOutcome; label: string }[] | null {
  if (kind === "call") return CALL_OUTCOMES;
  if (kind === "whatsapp") return WHATSAPP_OUTCOMES;
  return null;
}

export function outcomeQuestionOf(kind: PersonNoteKind): string {
  return kind === "whatsapp" ? "What happened?" : "How did the call go?";
}

export function outcomeLabel(outcome: NoteOutcome): string {
  return [...CALL_OUTCOMES, ...WHATSAPP_OUTCOMES].find((option) => option.value === outcome)?.label ?? outcome;
}

/** An outcome as a note carries it (`no_answer`) or as a timeline line does (`NO_ANSWER`); null for none or unknown. */
export function noteOutcomeOf(value: string | null | undefined): NoteOutcome | null {
  if (!value) return null;
  const outcome = value.toLowerCase();
  return [...CALL_OUTCOMES, ...WHATSAPP_OUTCOMES].find((option) => option.value === outcome)?.value ?? null;
}
