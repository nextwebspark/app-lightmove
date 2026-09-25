import { request } from "../../../lib/apiClient";
import type {
  Candidate,
  CandidateAiAssessment,
  CandidatesPage,
  CandidateStatus,
  SaveCandidatePayload,
  SaveContactsPayload,
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

/**
 * `size` is deliberately optional, and the Companies grid names none.
 *
 * <p>A client that computes its own size has to know the server's ceiling to stay under it, and the
 * grid's first attempt at that — a multiple of its own page size — landed exactly on
 * `company.list.max-page-size`. Lowering that deployment knob would have made every Companies page
 * 400 on this read. Omitted, the server sizes a company-filtered read at its own maximum and says in
 * `totalCount` whether that was enough.
 */
export function getCandidates(
  projectId: string,
  scope: CandidateQuery,
  signal?: AbortSignal,
): Promise<CandidatesPage> {
  const params = new URLSearchParams();
  // Repeated rather than comma-joined: Spring binds a repeated parameter to a List without anyone
  // having to agree on a separator that a UUID could never contain but a future id might.
  scope.triageCompanyIds?.forEach((id) => params.append("triageCompanyId", id));
  if (scope.unmapped) params.set("unmapped", "true");
  if (scope.query) params.set("q", scope.query);
  return request<CandidatesPage>(`/projects/${projectId}/candidates?${params}`, { signal });
}

/** Under the list's prefix, so every write that refreshes the grid refreshes this read too. */
export const CANDIDATE_KEY = (projectId: string, candidateId: string) =>
  [...CANDIDATES_KEY_PREFIX(projectId), "one", candidateId] as const;

export function getCandidate(
  projectId: string,
  candidateId: string,
  signal?: AbortSignal,
): Promise<Candidate> {
  return request<Candidate>(`/projects/${projectId}/candidates/${candidateId}`, { signal });
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
 * The Contact section's save. Its own write rather than a field of `updateCandidate`, because it
 * replaces a list and the profile's other sections must not be able to touch it by replaying.
 */
export function replaceContacts(
  projectId: string,
  candidateId: string,
  contacts: SaveContactsPayload,
): Promise<Candidate> {
  return request<Candidate>(`/projects/${projectId}/candidates/${candidateId}/contacts`, {
    method: "PUT",
    body: contacts,
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

/** Under the list's prefix, so the stream's refresh after an enrichment re-reads it too. */
export const AI_ASSESSMENT_KEY = (projectId: string, candidateId: string) =>
  [...CANDIDATES_KEY_PREFIX(projectId), "ai-assessment", candidateId] as const;

/** Null until a first AI enrichment has run (the server answers 204). */
export async function getAiAssessment(
  projectId: string,
  candidateId: string,
  signal?: AbortSignal,
): Promise<CandidateAiAssessment | null> {
  const assessment = await request<CandidateAiAssessment | undefined>(
    `/projects/${projectId}/candidates/${candidateId}/ai-assessment`,
    { signal },
  );
  return assessment ?? null;
}

/** Queues an AI deep enrichment; the result lands later, through the stream and the read above. */
export function requestAiEnrich(projectId: string, candidateId: string): Promise<void> {
  return request<void>(`/projects/${projectId}/candidates/${candidateId}/ai-enrich`, { method: "POST" });
}
