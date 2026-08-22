import { request } from "../../../lib/apiClient";
import type {
  BulkAddResult,
  TriageCompaniesPage,
  TriageCompany,
  TriageCompanyStatus,
} from "./types";

/**
 * A mandate's triaged companies — what Strategy's "Add to Universe" wrote.
 *
 * <p>This queries no market at all. The screen it replaced ran the mandate's criteria against 54k
 * warehouse rows and presented the matches, which made it a second, slower discovery screen;
 * discovery lives in Strategy now, and what is left here is the standing record of what the team
 * decided about each company.
 */

export const TRIAGE_KEY_PREFIX = (projectId: string) => ["triage", projectId] as const;

/**
 * The key carries the status and the page: each tab is its own list, and a Strategy save still
 * invalidates every tab at once through the prefix.
 */
export const TRIAGE_KEY = (projectId: string, status: TriageCompanyStatus, page: number, size: number) =>
  [...TRIAGE_KEY_PREFIX(projectId), status, page, size] as const;

export function getTriageCompanies(
  projectId: string,
  status: TriageCompanyStatus,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<TriageCompaniesPage> {
  const params = new URLSearchParams({ status, page: String(page), size: String(size) });
  return request<TriageCompaniesPage>(`/projects/${projectId}/triage?${params}`, { signal });
}

export function updateTriageCompany(
  projectId: string,
  triageCompanyId: string,
  changes: { status?: TriageCompanyStatus; note?: string },
): Promise<TriageCompany> {
  return request<TriageCompany>(`/projects/${projectId}/triage/${triageCompanyId}`, {
    method: "PATCH",
    body: changes,
  });
}

/** Strategy's per-row "Add to Universe". Already held answers with the existing row, not an error. */
export function addToUniverse(projectId: string, apolloAccountId: string): Promise<TriageCompany> {
  return request<TriageCompany>(`/projects/${projectId}/triage`, {
    method: "POST",
    body: { apolloAccountId },
  });
}

export function addAllInScope(projectId: string): Promise<BulkAddResult> {
  // No body: the scope is the stored filter, so this cannot ask for a wider one than is on screen.
  return request<BulkAddResult>(`/projects/${projectId}/triage/from-filter`, { method: "POST" });
}
