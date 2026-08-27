import { request } from "../../../lib/apiClient";
import type { GridSort } from "../../../lib/useGridSort";
import type {
  BulkAddResult,
  CaptureCompanyPayload,
  EditCompanyPayload,
  TriageCompaniesPage,
  TriageCompany,
  TriageCompanyStatus,
  TriageCounts,
  TriageSortField,
} from "./types";

/**
 * A mandate's triaged companies — what Strategy's "Add to Universe" wrote, plus the companies the
 * mandate supplied itself.
 *
 * <p>This queries no market at all. The screen it replaced ran the mandate's criteria against 54k
 * warehouse rows and presented the matches, which made it a second, slower discovery screen;
 * discovery lives in Strategy now, and what is left here is the standing record of what the team
 * decided about each company.
 */

export const TRIAGE_KEY_PREFIX = (projectId: string) => ["triage", projectId] as const;

/**
 * The key carries everything the request varies by, so each stage, page, search and ordering is its
 * own cache entry — and a write still invalidates all of them at once through the prefix.
 */
export const TRIAGE_KEY = (
  projectId: string,
  status: TriageCompanyStatus,
  page: number,
  size: number,
  query: string,
  sort: GridSort<TriageSortField>,
) => [...TRIAGE_KEY_PREFIX(projectId), status, page, size, query, sort.field, sort.direction] as const;

export function getTriageCompanies(
  projectId: string,
  status: TriageCompanyStatus,
  page: number,
  size: number,
  query: string,
  sort: GridSort<TriageSortField>,
  signal?: AbortSignal,
): Promise<TriageCompaniesPage> {
  const params = new URLSearchParams({
    status,
    page: String(page),
    size: String(size),
    sort: sort.field,
    direction: sort.direction,
  });
  // Omitted rather than sent empty: the server reads a blank `q` as no search, and leaving it out
  // keeps the two states from being one request apart in the network log.
  if (query) params.set("q", query);
  return request<TriageCompaniesPage>(`/projects/${projectId}/triage?${params}`, { signal });
}

export const TRIAGE_COUNTS_KEY = (projectId: string) =>
  [...TRIAGE_KEY_PREFIX(projectId), "counts"] as const;

/**
 * The three stage counts on their own, for the sidebar — which carries them on every tab of a
 * mandate, not just the Companies grids.
 *
 * <p>It asks the list endpoint for a single row rather than a counts route of its own: the counts
 * already travel with every page because the switcher is always on screen, and the key sits under
 * {@link TRIAGE_KEY_PREFIX} so a move invalidates the rail's numbers with the grid's.
 */
export function getTriageCounts(projectId: string, signal?: AbortSignal): Promise<TriageCounts> {
  return getTriageCompanies(
    projectId,
    "inUniverse",
    0,
    1,
    "",
    { field: "added", direction: "desc" },
    signal,
  ).then((page) => page.counts);
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

/**
 * Drops this mandate's decision about a company. The company itself is untouched — the Apollo
 * universe is read-only to the API — so it stays on Strategy and stays available to every other
 * mandate.
 */
export function deleteTriageCompany(projectId: string, triageCompanyId: string): Promise<void> {
  return request<void>(`/projects/${projectId}/triage/${triageCompanyId}`, { method: "DELETE" });
}

/**
 * Replaces a hand-typed company's own facts. A PUT beside the PATCH above because the two are
 * different acts: that one is a triage change where an omitted half is left alone, this is the panel's
 * whole form where an omitted field is a cleared one. Refused by the server for a company taken from
 * the market — the panel hides Edit on those, but the endpoint is what actually holds the rule.
 */
export function editTriageCompany(
  projectId: string,
  triageCompanyId: string,
  company: EditCompanyPayload,
): Promise<TriageCompany> {
  return request<TriageCompany>(`/projects/${projectId}/triage/${triageCompanyId}`, {
    method: "PUT",
    body: company,
  });
}

/** A company the market does not carry: typed into the Add company form, or sent by the plugin. */
export function captureCompany(
  projectId: string,
  company: CaptureCompanyPayload,
): Promise<TriageCompany> {
  return request<TriageCompany>(`/projects/${projectId}/triage/capture`, {
    method: "POST",
    body: company,
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
