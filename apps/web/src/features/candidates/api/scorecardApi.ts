import { request } from "../../../lib/apiClient";
import { CANDIDATES_KEY_PREFIX } from "./candidatesApi";
import type { CandidateScorecard, SaveScorecardPayload } from "./scorecardTypes";

export const SCORECARD_KEY = (projectId: string, candidateId: string) =>
  [...CANDIDATES_KEY_PREFIX(projectId), "assessment", candidateId] as const;

export function getScorecard(projectId: string, candidateId: string, signal?: AbortSignal): Promise<CandidateScorecard> {
  return request<CandidateScorecard>(`/projects/${projectId}/candidates/${candidateId}/assessment`, { signal });
}

export function saveScorecard(
  projectId: string,
  candidateId: string,
  payload: SaveScorecardPayload,
): Promise<CandidateScorecard> {
  return request<CandidateScorecard>(`/projects/${projectId}/candidates/${candidateId}/assessment`, {
    method: "PUT",
    body: payload,
  });
}
