import type { AddedBy, Candidate } from "../../candidates/api/types";
import type { TriageCompany } from "../api/types";

/**
 * One line of the Companies grid: a person at a company. A company with nobody mapped keeps one line with
 * `candidate: null` (the "Add executive" slot); `company: null` is an executive whose employer is not in the universe.
 */
export type TriageCompanyRow = (
  | { company: TriageCompany; candidate: Candidate | null }
  | { company: null; candidate: Candidate }
) & {
  /** Who filed the line's executive, where a staff read said; never on a client seat's rows. */
  candidateAddedBy?: AddedBy;
};

/** Stable and collision-free across both nullable sides — the empty slot needs an id of its own. */
export function triageRowId(row: TriageCompanyRow): string {
  return `${row.company?.id ?? "unmapped"}:${row.candidate?.id ?? "slot"}`;
}

/**
 * Research on a plugin capture normally lands within seconds; past this, it failed and the row will
 * stay as captured — polling forever for it would be a heartbeat nobody asked for.
 */
const RESEARCH_WINDOW_MS = 3 * 60 * 1000;

/**
 * Whether some visible executive is still being researched — a plugin capture, not yet enriched,
 * young enough that the answer is still coming. The page polls while this holds and stops by itself:
 * either the research lands (enrichedAt fills in) or the window expires (it failed).
 */
export function awaitingResearch(candidates: Candidate[], now: number = Date.now()): boolean {
  return candidates.some(
    (candidate) =>
      candidate.source === "extension" &&
      !candidate.enrichedAt &&
      now - new Date(candidate.addedAt).getTime() < RESEARCH_WINDOW_MS,
  );
}
