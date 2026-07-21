import { request } from "../../../lib/apiClient";
import type { CompanySearchResult, Estimate, SectorCount, Suggestions } from "./types";

/**
 * Reads over the shared company universe. Query keys sort their inputs so that the same selection in
 * a different order is one cache entry, not many.
 */

export const SECTORS_KEY = ["companySectors"] as const;

export const SUGGESTIONS_KEY = (directLabels: string[]) =>
  ["companySuggestions", [...directLabels].sort()] as const;

export const ESTIMATE_KEY = (sectors: string[], tags: string[]) =>
  ["companyEstimate", [...sectors].sort(), [...tags].sort()] as const;

/** Repeated query params — `sector=a&sector=b` — the way the backend binds a List. */
function repeated(name: string, values: string[]): string {
  return values.map((value) => `${name}=${encodeURIComponent(value)}`).join("&");
}

export function getSectors(): Promise<{ sectors: SectorCount[] }> {
  return request<{ sectors: SectorCount[] }>(`/companies/sectors`);
}

export function getSuggestions(directLabels: string[]): Promise<Suggestions> {
  return request<Suggestions>(`/companies/sectors/suggestions?${repeated("sector", directLabels)}`);
}

export function getEstimate(sectors: string[], tags: string[]): Promise<Estimate> {
  const query = [repeated("sector", sectors), repeated("tag", tags)].filter(Boolean).join("&");
  return request<Estimate>(`/companies/estimate${query ? `?${query}` : ""}`);
}

/** How a zero-query browse orders the universe — mirror of the backend CompanySearchOrder enum. */
export type CompanySearchOrder = "revenue_desc" | "revenue_asc";

/** The dropdown asks for more than it shows so already-picked companies can't empty the page. */
const SEARCH_LIMIT = 25;

export const SEARCH_KEY = (query: string, sectors: string[], order: CompanySearchOrder) =>
  ["companySearch", query.toLowerCase(), [...sectors].sort(), order] as const;

/** A blank query browses by revenue within the sectors; typed text name-matches instead. */
export function searchCompanies(
  query: string,
  sectors: string[],
  order: CompanySearchOrder,
): Promise<{ companies: CompanySearchResult[] }> {
  const params = [
    `q=${encodeURIComponent(query)}`,
    query.trim() === "" ? repeated("sector", sectors) : "",
    `order=${order}`,
    `limit=${SEARCH_LIMIT}`,
  ]
    .filter(Boolean)
    .join("&");
  return request<{ companies: CompanySearchResult[] }>(`/companies/search?${params}`);
}
