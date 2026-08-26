import { request } from "../../../lib/apiClient";
import type {
  CompanyPage,
  CompanySort,
  SavedSearch,
  SearchVisibility,
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

export function saveSearch(
  projectId: string,
  name: string,
  visibility: SearchVisibility,
): Promise<SavedSearch> {
  // No filter in the body: the server saves what the mandate has already autosaved, so what is
  // captured is exactly what is on screen and the two cannot drift.
  return request<SavedSearch>(`/projects/${projectId}/strategy/searches`, {
    method: "POST",
    body: { name, visibility },
  });
}

/** The label and the tier. Never the filter — that is `overwriteSearch`. */
export function patchSearch(
  projectId: string,
  searchId: string,
  patch: { name: string; visibility?: SearchVisibility },
): Promise<SavedSearch> {
  return request<SavedSearch>(`/projects/${projectId}/strategy/searches/${searchId}`, {
    method: "PATCH",
    body: patch,
  });
}

/**
 * Re-capture the mandate's current filter onto an existing search. Bodyless for the same reason
 * `saveSearch` is: the server reads the stored filter, so the caller must flush its autosave first.
 */
export function overwriteSearch(projectId: string, searchId: string): Promise<SavedSearch> {
  return request<SavedSearch>(`/projects/${projectId}/strategy/searches/${searchId}/filter`, {
    method: "PUT",
  });
}

export function deleteSearch(projectId: string, searchId: string): Promise<void> {
  return request<void>(`/projects/${projectId}/strategy/searches/${searchId}`, { method: "DELETE" });
}

