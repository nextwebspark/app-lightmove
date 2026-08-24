import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { CaptureCompanyRequest, TriagedCompany } from "./types";

/**
 * Writes the company into a mandate's triage, at the stage the footer button named.
 *
 * Idempotent and promotion-only on the server: capturing a company the mandate already holds moves it
 * up to the shortlist or leaves it where it is, and never demotes it. A company the mandate has
 * declined is refused with `TRIAGE_COMPANY_DECLINED` rather than quietly revived.
 */
export function captureCompany(
  api: LightMoveApiClient,
  projectId: string,
  capture: CaptureCompanyRequest,
): Promise<TriagedCompany> {
  return api.request<TriagedCompany>(`/projects/${projectId}/triage/captures`, {
    method: "POST",
    body: capture,
  });
}
