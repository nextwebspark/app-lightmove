/**
 * Where a captured company lands. The universe alone: shortlisting and declining are decisions made on the Companies
 * screen with the rest of the universe in view, never from a single LinkedIn page.
 */
export const CAPTURED_COMPANY_STAGE = "inUniverse";

export type TriageDestination = typeof CAPTURED_COMPANY_STAGE;

/** A company's stage on the position, as its record's pill reads it. */
export const TRIAGE_STAGE_LABELS: Record<string, string> = {
  inUniverse: "In universe",
  shortlisted: "Shortlisted",
  declined: "Declined",
};
