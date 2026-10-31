import { request } from "../../../lib/apiClient";
import type {
  Candidate,
  CandidatesPage,
  CandidateStatus,
  NationalityReading,
  SaveCandidatePayload,
  SaveContactsPayload,
} from "./types";
import type { TriageCompanyStatus } from "../../triage/api/types";

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
  /** The toolbar's search: someone kept when they or their company match it. */
  search?: string;
  /** The Companies stage the people are listed at — their own, not their company's (#903). */
  triageStatus?: TriageCompanyStatus;
}

export const CANDIDATES_KEY = (projectId: string, scope: CandidateQuery) =>
  [
    ...CANDIDATES_KEY_PREFIX(projectId),
    scope.triageCompanyIds ?? null,
    scope.unmapped ?? false,
    scope.query ?? "",
    scope.triageStatus ?? null,
    scope.search ?? "",
  ] as const;

/**
 * `size` is deliberately optional, and the Companies grid names none.
 *
 * <p>A client that computes its own size has to know the server's ceiling to stay under it, and the
 * grid's first attempt at that — a multiple of its own page size — landed exactly on
 * `company.list.max-page-size`. Lowering that deployment knob would have made every Companies page
 * 400 on this read. Omitted, the server sizes a company-filtered read at its own maximum and says in
 * `totalCount` whether that was enough — and, for a company-scoped or unmapped read, this pages on
 * until it was.
 */
export async function getCandidates(
  projectId: string,
  scope: CandidateQuery,
  signal?: AbortSignal,
): Promise<CandidatesPage> {
  const first = await getCandidatesPage(projectId, scope, 0, signal);
  if (!readsEveryone(scope)) return first;
  // A page of 100 companies easily maps more than one ceiling of people, and the read is ordered by
  // when each was filed — so a company's later executives were simply missing from the grid at 100
  // per page while 50 per page showed them all. A company-scoped read is everyone at those companies.
  const candidates = [...first.candidates];
  const seen = new Set(candidates.map((candidate) => candidate.id));
  let addedBy = first.addedBy;
  let last = first;
  for (let page = 1; page < MAX_PAGES_PER_SCOPE && seen.size < last.totalCount; page++) {
    last = await getCandidatesPage(projectId, scope, page, signal);
    if (last.candidates.length === 0) break;
    for (const candidate of last.candidates) {
      if (seen.has(candidate.id)) continue;
      seen.add(candidate.id);
      candidates.push(candidate);
    }
    if (last.addedBy) addedBy = { ...(addedBy ?? {}), ...last.addedBy };
  }
  return { ...last, candidates, page: 0, addedBy };
}

/** Past this many pages the read stops and `totalCount` says what was left out. */
const MAX_PAGES_PER_SCOPE = 50;

function readsEveryone(scope: CandidateQuery): boolean {
  return scope.triageCompanyIds !== undefined || scope.unmapped === true;
}

function getCandidatesPage(
  projectId: string,
  scope: CandidateQuery,
  page: number,
  signal?: AbortSignal,
): Promise<CandidatesPage> {
  const params = new URLSearchParams();
  // Repeated rather than comma-joined: Spring binds a repeated parameter to a List without anyone
  // having to agree on a separator that a UUID could never contain but a future id might.
  scope.triageCompanyIds?.forEach((id) => params.append("triageCompanyId", id));
  if (scope.unmapped) params.set("unmapped", "true");
  if (scope.query) params.set("q", scope.query);
  if (scope.search?.trim()) params.set("search", scope.search.trim());
  if (scope.triageStatus) params.set("triageStatus", scope.triageStatus);
  if (page > 0) params.set("page", String(page));
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

/** Moves one executive to another Companies stage; their company and its other people stay where they are. */
export function moveCandidateToStage(
  projectId: string,
  candidateId: string,
  status: TriageCompanyStatus,
): Promise<Candidate> {
  return request<Candidate>(`/projects/${projectId}/candidates/${candidateId}/triage-status`, {
    method: "PUT",
    body: { status },
  });
}

/** `withCompany` also removes the company they were the last one at; the server refuses if anyone else is there. */
export function deleteCandidate(
  projectId: string,
  candidateId: string,
  options?: { withCompany: boolean },
): Promise<void> {
  const query = options?.withCompany ? "?withCompany=true" : "";
  return request<void>(`/projects/${projectId}/candidates/${candidateId}${query}`, { method: "DELETE" });
}

/** Under the list's prefix, so the stream's refresh after an enrichment re-reads it too. */
export const NATIONALITY_READING_KEY = (projectId: string, candidateId: string) =>
  [...CANDIDATES_KEY_PREFIX(projectId), "nationality-reading", candidateId] as const;

/** Null until the classifier has answered (the server answers 204). Staff-only. */
export async function getNationalityReading(
  projectId: string,
  candidateId: string,
  signal?: AbortSignal,
): Promise<NationalityReading | null> {
  const reading = await request<NationalityReading | undefined>(
    `/projects/${projectId}/candidates/${candidateId}/nationality-reading`,
    { signal },
  );
  return reading ?? null;
}

/** Suggest with AI: proposes whatever background is still missing, and answers the executive as it now stands. */
export function suggestBackground(projectId: string, candidateId: string): Promise<Candidate> {
  return request<Candidate>(`/projects/${projectId}/candidates/${candidateId}/background/ai`, { method: "POST" });
}
