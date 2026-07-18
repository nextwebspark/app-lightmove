import { request } from "../../../lib/apiClient";
import type { Estimate, SectorCount, Suggestions } from "./types";

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
