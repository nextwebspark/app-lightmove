import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { CaptureCompanyRequest, TriagedCompany } from "./types";

/**
 * Writes the company into a mandate's triage, at the stage the footer button named.
 *
 * The API's own capture endpoint, which already existed for this plugin — the extension adds nothing
 * to the server and must not. `source: "extension"` is the provenance the Companies screen shows, and
 * it is what separates a page-read headcount from one the Apollo pipeline exported.
 *
 * A company the mandate already holds under that name is refused with `TRIAGE_COMPANY_ALREADY_HELD`
 * rather than merged: the popup says so instead of silently writing a second row.
 */
export function captureCompany(
  api: LightMoveApiClient,
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
  api: LightMoveApiClient,
  projectId: string,
  triageCompanyId: string,
): Promise<void> {
  return api.request<void>(`/projects/${projectId}/triage/${triageCompanyId}`, { method: "DELETE" });
}
