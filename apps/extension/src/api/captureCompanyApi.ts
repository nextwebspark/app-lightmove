import type { UncavaApiClient } from "./uncavaApiClient";
import type { CaptureCompanyRequest, TriagedCompany } from "./types";

/**
 * Writes the company into a mandate's triage, at the stage the footer button named. The API's own
 * endpoint; `source: "extension"` is the only difference from a company typed in by hand, and a name
 * the mandate already holds is refused rather than merged.
 */
export function captureCompany(
  api: UncavaApiClient,
  projectId: string,
  capture: CaptureCompanyRequest,
): Promise<TriagedCompany> {
  return api.request<TriagedCompany>(`/projects/${projectId}/triage/capture`, {
    method: "POST",
    body: capture,
  });
}

/** Undoes a capture: drops the project↔company row, which is all the mandate ever held. */
export function removeTriageCompany(
  api: UncavaApiClient,
  projectId: string,
  triageCompanyId: string,
): Promise<void> {
  return api.request<void>(`/projects/${projectId}/triage/${triageCompanyId}`, { method: "DELETE" });
}
