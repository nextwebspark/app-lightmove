import { request } from "../../../lib/apiClient";
import type {
  CompanyResult,
  CompanySuggestion,
  DiscoverCompaniesPayload,
  DiscoveryAnswer,
  DiscoveryConfig,
  FacetCount,
  Facets,
} from "./types";

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

export const COMPANY_KEY = (apolloAccountId: string) => ["company", apolloAccountId] as const;

/**
 * One company of the universe, whole — what a picker shows once a suggestion is chosen. The
 * typeahead answers a name and a line of context on purpose; this is the record behind the one that
 * was picked, so a consultant can read it before taking it into a mandate.
 */
export function getCompany(apolloAccountId: string, signal?: AbortSignal): Promise<CompanyResult> {
  return request<CompanyResult>(`/companies/${encodeURIComponent(apolloAccountId)}`, { signal });
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

/**
 * AI Research. A mutation rather than a query everywhere it is called, because it spends the
 * workspace's daily budget — a key-driven refetch on window focus would bill a firm for a resize.
 * Only the config read below is cached.
 */
export function discoverCompanies(
  payload: DiscoverCompaniesPayload,
  signal?: AbortSignal,
): Promise<DiscoveryAnswer> {
  return request<DiscoveryAnswer>("/companies/discover", {
    method: "POST",
    body: payload,
    signal,
  });
}

/** Read before the CTA is drawn, so an unconfigured deployment disables it rather than failing. */
export const DISCOVERY_CONFIG_KEY = ["companyDiscoveryConfig"] as const;

export function getDiscoveryConfig(signal?: AbortSignal): Promise<DiscoveryConfig> {
  return request<DiscoveryConfig>("/companies/discover/config", { signal });
}
