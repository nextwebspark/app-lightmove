import type { CandidateSeniority, CandidateStatus } from "../api/types";

/**
 * How a mandate's research on a person reads on screen, in one place — the grid's Status pill and the
 * drawer's Status select are the same seven values and must not drift into two vocabularies.
 *
 * <p>The colours carry the reading: green is progress, red is a closed door, amber is a door that was
 * never open for this brief, and grey is "mapped and nothing more", which is where every profile
 * starts and is the most common value on any grid.
 */
export const CANDIDATE_STATUSES: { value: CandidateStatus; label: string; className: string }[] = [
  { value: "identified", label: "Identified", className: "text-text2 bg-line-soft" },
  { value: "contacted", label: "Contacted", className: "text-sky bg-sky-dim" },
  { value: "engaged", label: "Engaged", className: "text-sky bg-sky-dim" },
  { value: "interested", label: "Interested", className: "text-green bg-green-dim" },
  { value: "notInterested", label: "Not interested", className: "text-text3 bg-panel2" },
  { value: "offLimits", label: "Off-limits", className: "text-red bg-red-dim" },
  { value: "outOfScope", label: "Out of scope", className: "text-amber bg-amber-dim" },
];

const BY_VALUE = new Map(CANDIDATE_STATUSES.map((status) => [status.value, status]));

/**
 * The style for a status, falling back to Identified for a token this build has not heard of. A row
 * the server can store is a row the grid must be able to draw: a status added on the server before a
 * deploy reaches the browser would otherwise render an empty pill.
 */
export function candidateStatusStyle(status: CandidateStatus) {
  return BY_VALUE.get(status) ?? CANDIDATE_STATUSES[0];
}

/** The wire tokens are what a consultant writes, so they are also the labels. */
export const CANDIDATE_SENIORITIES: CandidateSeniority[] = ["N", "N-1", "N-2", "N-3"];
