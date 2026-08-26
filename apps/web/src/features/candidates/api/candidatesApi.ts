import { request } from "../../../lib/apiClient";
import type {
  Candidate,
  CandidatesPage,
  CandidateStatus,
  SaveCandidatePayload,
} from "./types";

/**
 * A mandate's mapped executives — the people half of a talent map.
 *
 * <p>The Companies grid reads this alongside the companies rather than through them: `triagecompany`
 * knows nothing about people, deliberately, so the grid asks for the companies on its page and then
 * for the people at exactly those. Two requests, one cache entry each, and neither feature has to
 * learn the other's storage.
 */

export const CANDIDATES_KEY_PREFIX = (projectId: string) => ["candidates", projectId] as const;

/** What the read varies by, so a company page, the unmapped list and a search are separate entries. */
export interface CandidateQuery {
  /** The companies on the page being rendered. Empty means an empty answer, not "everyone". */
  triageCompanyIds?: string[];
  /** The other side: people whose employer is not one of the mandate's companies. */
  unmapped?: boolean;
  query?: string;
}

export const CANDIDATES_KEY = (projectId: string, scope: CandidateQuery) =>
  [
    ...CANDIDATES_KEY_PREFIX(projectId),
    scope.triageCompanyIds ?? null,
    scope.unmapped ?? false,
    scope.query ?? "",
  ] as const;

export function getCandidates(
  projectId: string,
  scope: CandidateQuery,
  size: number,
  signal?: AbortSignal,
): Promise<CandidatesPage> {
  const params = new URLSearchParams({ size: String(size) });
  // Repeated rather than comma-joined: Spring binds a repeated parameter to a List without anyone
  // having to agree on a separator that a UUID could never contain but a future id might.
  scope.triageCompanyIds?.forEach((id) => params.append("triageCompanyId", id));
  if (scope.unmapped) params.set("unmapped", "true");
  if (scope.query) params.set("q", scope.query);
  return request<CandidatesPage>(`/projects/${projectId}/candidates?${params}`, { signal });
}

export function createCandidate(
  projectId: string,
  candidate: SaveCandidatePayload,
): Promise<Candidate> {
  return request<Candidate>(`/projects/${projectId}/candidates`, {
    method: "POST",
    body: candidate,
  });
}

/** A full replace, not a merge: the drawer holds every field, so what it omits is what it cleared. */
export function updateCandidate(
  projectId: string,
  candidateId: string,
  candidate: SaveCandidatePayload,
): Promise<Candidate> {
  return request<Candidate>(`/projects/${projectId}/candidates/${candidateId}`, {
    method: "PUT",
    body: candidate,
  });
}

/**
 * Moves someone along the line and touches nothing else — the status pill on the read-only profile
 * panel, which a researcher flicks while reading.
 *
 * <p>Deliberately not an `updateCandidate` with one field changed: a panel that has been open for a
 * while would re-submit a stale profile and quietly undo whatever was edited since.
 */
export function changeCandidateStatus(
  projectId: string,
  candidateId: string,
  status: CandidateStatus,
): Promise<Candidate> {
  return request<Candidate>(`/projects/${projectId}/candidates/${candidateId}`, {
    method: "PATCH",
    body: { status },
  });
}

export function deleteCandidate(projectId: string, candidateId: string): Promise<void> {
  return request<void>(`/projects/${projectId}/candidates/${candidateId}`, { method: "DELETE" });
}
