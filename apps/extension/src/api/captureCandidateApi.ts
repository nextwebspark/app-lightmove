import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { CapturedCandidate, SaveCandidateRequest } from "./types";

/**
 * Writes the person into the mandate's people, mapped to a triaged company where one matched.
 *
 * The API's own candidate endpoint, the same one the web app's Add-executive drawer posts to — the
 * extension adds nothing to the server and must not. `source: "extension"` is the provenance, and the
 * only difference between this row and one typed in by hand.
 *
 * Someone the mandate already maps under that name is refused with `CANDIDATE_ALREADY_MAPPED` rather
 * than merged: the popup says so instead of silently writing a second row.
 */
export function captureCandidate(
  api: LightMoveApiClient,
  projectId: string,
  candidate: SaveCandidateRequest,
): Promise<CapturedCandidate> {
  return api.request<CapturedCandidate>(`/projects/${projectId}/candidates`, {
    method: "POST",
    body: candidate,
  });
}

/** Undoes a capture: removes the person from the mandate. */
export function removeCandidate(
  api: LightMoveApiClient,
  projectId: string,
  candidateId: string,
): Promise<void> {
  return api.request<void>(`/projects/${projectId}/candidates/${candidateId}`, { method: "DELETE" });
}
