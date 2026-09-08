import type { Candidate } from "../../candidates/api/types";
import type { TriageCompany } from "../api/types";

/**
 * One line of the Companies grid.
 *
 * <p>A grid line is a <i>person at a company</i>, not a company: a company with three executives
 * mapped is three lines with the company repeated, which is how a consultant reads a mapping and how
 * the mockup's own candidate table is built. A company with nobody mapped keeps one line carrying the
 * "Add executive" slot, because a company nobody has looked at yet is the most important thing the
 * screen has to show.
 *
 * <p>Exactly one side may be null, and the type says so: `candidate: null` is that empty slot;
 * `company: null` is an executive whose employer is not in the mandate's universe at all, which the
 * In-universe stage shows after the companies rather than hiding.
 */
export type TriageCompanyRow =
  | { company: TriageCompany; candidate: Candidate | null }
  | { company: null; candidate: Candidate };

/** Stable and collision-free across both nullable sides — the empty slot needs an id of its own. */
export function triageRowId(row: TriageCompanyRow): string {
  return `${row.company?.id ?? "unmapped"}:${row.candidate?.id ?? "slot"}`;
}

/**
 * Expands a page of companies against the people mapped at them, in the companies' server order so a
 * company's lines stay contiguous however the grid is sorted.
 *
 * <p>`unmappedCandidates` are appended after them. They belong to the mandate rather than to any
 * company on this page, so they are passed only on the page that should carry them — the caller
 * decides that, because "the last page of the universe" is a paging fact this function has no view of.
 */
export function toTriageRows(
  companies: TriageCompany[],
  candidates: Candidate[],
  unmappedCandidates: Candidate[] = [],
): TriageCompanyRow[] {
  const byCompany = new Map<string, Candidate[]>();
  for (const candidate of candidates) {
    if (!candidate.triageCompanyId) continue;
    const held = byCompany.get(candidate.triageCompanyId);
    if (held) held.push(candidate);
    else byCompany.set(candidate.triageCompanyId, [candidate]);
  }

  const rows: TriageCompanyRow[] = [];
  for (const company of companies) {
    const mapped = byCompany.get(company.id) ?? [];
    if (mapped.length === 0) {
      rows.push({ company, candidate: null });
      continue;
    }
    for (const candidate of mapped) rows.push({ company, candidate });
  }

  for (const candidate of unmappedCandidates) rows.push({ company: null, candidate });

  return rows;
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
