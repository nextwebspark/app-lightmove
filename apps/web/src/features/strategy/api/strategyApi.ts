import { request } from "../../../lib/apiClient";
import type {
  CompanyPage,
  CompanySort,
  FacetCounts,
  SavedSearch,
  Strategy,
  StrategyFilter,
} from "./types";

/** A mandate's own search: its filter, its off-limits list, its saved searches, and the results. */

export const STRATEGY_KEY = (projectId: string) => ["strategy", projectId] as const;

/** Every filter write shares this key, which is what names them together in the devtools. */
export const STRATEGY_WRITE_KEY = (projectId: string) => ["strategyWrite", projectId] as const;

export const STRATEGY_COMPANIES_KEY_PREFIX = (projectId: string) =>
  ["strategyCompanies", projectId] as const;

export const STRATEGY_COMPANIES_KEY = (
  projectId: string,
  page: number,
  size: number,
  query: string,
  sort: CompanySort,
) =>
  [...STRATEGY_COMPANIES_KEY_PREFIX(projectId), page, size, query, sort.field, sort.direction] as const;

/**
 * Every counts read for one mandate. The off-limits list is part of the scope the server applies but
 * not part of the key, so barring a company has to invalidate through this rather than be noticed.
 */
export const STRATEGY_FACET_COUNTS_KEY_PREFIX = (projectId: string) =>
  ["strategyFacetCounts", projectId] as const;

/**
 * Keyed on the filter itself: the counts are a pure function of the selection, so two paths back to
 * the same chips reuse one cached answer. React Query hashes the object deterministically, so it can
 * be the key as it stands.
 */
export const STRATEGY_FACET_COUNTS_KEY = (projectId: string, filter: StrategyFilter) =>
  [...STRATEGY_FACET_COUNTS_KEY_PREFIX(projectId), filter] as const;

export function getStrategy(projectId: string): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy`);
}

export function putFilter(projectId: string, filter: StrategyFilter): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy/filter`, {
    method: "PUT",
    body: { filter },
  });
}

export function putOffLimits(projectId: string, apolloAccountIds: string[]): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy/off-limits`, {
    method: "PUT",
    body: { apolloAccountIds },
  });
}

export function getCompanies(
  projectId: string,
  page: number,
  size: number,
  query: string,
  sort: CompanySort,
  signal?: AbortSignal,
): Promise<CompanyPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: sort.field,
    direction: sort.direction,
  });
  if (query) params.set("q", query);
  return request<CompanyPage>(`/projects/${projectId}/strategy/companies?${params}`, { signal });
}

/**
 * The sidebar's counts for the selection on screen.
 *
 * The draft goes in the body rather than being read back from the saved row: the filter autosaves on
 * a debounce, and counts resolved from the stored document would trail every chip click by the
 * better part of a second. A POST for a read, because a whole accordion selection does not belong in
 * a query string.
 */
export function getFacetCounts(
  projectId: string,
  filter: StrategyFilter,
  signal?: AbortSignal,
): Promise<FacetCounts> {
  return request<FacetCounts>(`/projects/${projectId}/strategy/facet-counts`, {
    method: "POST",
    body: { filter },
    signal,
  });
}

export function saveSearch(projectId: string, name: string): Promise<SavedSearch> {
  // No filter in the body: the server saves what the mandate has already autosaved, so what is
  // captured is exactly what is on screen and the two cannot drift.
  return request<SavedSearch>(`/projects/${projectId}/strategy/searches`, {
    method: "POST",
    body: { name },
  });
}

export function deleteSearch(projectId: string, searchId: string): Promise<void> {
  return request<void>(`/projects/${projectId}/strategy/searches/${searchId}`, { method: "DELETE" });
}

