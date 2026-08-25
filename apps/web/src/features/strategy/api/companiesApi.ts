import { request } from "../../../lib/apiClient";
import type { CompanySuggestion, FacetCount, Facets } from "./types";

/**
 * The workspace-level reads over the company universe: what the filter sidebar can offer, and what a
 * company picker suggests. Shared reference data, so nothing here is scoped to a mandate.
 */

/**
 * One key for the whole sidebar. The counts are over the entire universe and change only when the
 * pipeline loads, so this outlives any filter edit — which is why the Strategy page's own
 * invalidations never touch it.
 */
export const FACETS_KEY = ["companyFacets"] as const;

export const COMPANY_SEARCH_KEY = (query: string) => ["companySearch", query] as const;

export const KEYWORD_SEARCH_KEY = (query: string) => ["companyKeywords", query] as const;

export function getFacets(): Promise<Facets> {
  return request<Facets>("/companies/facets");
}

export function searchCompanies(
  query: string,
  limit?: number,
  signal?: AbortSignal,
): Promise<{ companies: CompanySuggestion[] }> {
  const params = new URLSearchParams({ q: query });
  if (limit !== undefined) params.set("limit", String(limit));
  return request<{ companies: CompanySuggestion[] }>(`/companies/search?${params}`, { signal });
}

export function searchKeywords(
  query: string,
  signal?: AbortSignal,
): Promise<{ keywords: FacetCount[] }> {
  return request<{ keywords: FacetCount[] }>(
    `/companies/keywords?${new URLSearchParams({ q: query })}`,
    { signal },
  );
}
