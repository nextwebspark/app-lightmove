import type { NoteOutcome, PersonNoteKind } from "../api/types";

/** Each outcome's words: a call's are the sequence call step's own four (`Outreach.dc.html`), a WhatsApp log's two. */
const OUTCOME_LABELS: Record<NoteOutcome, string> = {
  connected: "Connected",
  voicemail: "Left voicemail",
  no_answer: "No answer",
  wrong_number: "Wrong number",
  sent: "Sent a message",
  replied: "They replied",
};

const optionsOf = (outcomes: readonly NoteOutcome[]) =>
  outcomes.map((value) => ({ value, label: OUTCOME_LABELS[value] }));

export const CALL_OUTCOMES = optionsOf(["connected", "voicemail", "no_answer", "wrong_number"]);
export const WHATSAPP_OUTCOMES = optionsOf(["sent", "replied"]);

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
  return OUTCOME_LABELS[outcome];
}

/** The toast once a log that says how it went is saved, from the composer or the log-it tray. */
export function loggedToastOf(kind: PersonNoteKind): string {
  return kind === "whatsapp" ? "WhatsApp logged" : "Call logged";
}

/** An outcome as a note carries it (`no_answer`) or as a timeline line does (`NO_ANSWER`); null for none or unknown. */
export function noteOutcomeOf(value: string | null | undefined): NoteOutcome | null {
  const outcome = (value ?? "").toLowerCase();
  return outcome in OUTCOME_LABELS ? (outcome as NoteOutcome) : null;
}
