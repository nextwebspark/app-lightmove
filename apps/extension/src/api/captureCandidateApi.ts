import type { UncavaApiClient } from "./uncavaApiClient";
import type { CapturedCandidate, SaveCandidateRequest } from "./types";

/**
 * Writes the person into the mandate's people. The same endpoint the Add-executive drawer posts to;
 * `source: "extension"` is the only difference, and a name the mandate already maps is refused rather
 * than merged.
 */
export function captureCandidate(
  api: UncavaApiClient,
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
  api: UncavaApiClient,
  projectId: string,
  candidateId: string,
): Promise<void> {
  return api.request<void>(`/projects/${projectId}/candidates/${candidateId}`, { method: "DELETE" });
}
