import { request } from "../../../lib/apiClient";
import type { TriageCompanyStatus } from "../../triage/api/types";
import type { TalentMapConfig, TalentMapLocations, TalentMapPage } from "./types";

/**
 * The map view's three reads: one stage as points, that stage's points on their own, and whether the
 * map is offered at all.
 *
 * <p>The stage read is unpaged — a globe with a page two is no globe — and the server caps it
 * instead, stating the totals so a mandate past the cap is told rather than shown a map that looks
 * complete and is not.
 */

export const TALENT_MAP_KEY_PREFIX = (projectId: string) => ["talentMap", projectId] as const;

export const TALENT_MAP_KEY = (projectId: string, status: TriageCompanyStatus, search = "") =>
  [...TALENT_MAP_KEY_PREFIX(projectId), status, search.trim()] as const;

/** `search` is the toolbar's, matched by the server as the grid's is, so both views show the same people. */
export function getTalentMap(
  projectId: string,
  status: TriageCompanyStatus,
  search: string,
  signal?: AbortSignal,
): Promise<TalentMapPage> {
  const params = new URLSearchParams({ status });
  if (search.trim()) params.set("search", search.trim());
  return request<TalentMapPage>(`/projects/${projectId}/talent-map?${params}`, { signal });
}

// A key of its own length, so a search for "locations" never lands on the poll's entry.
export const TALENT_MAP_LOCATIONS_KEY = (projectId: string, status: TriageCompanyStatus, search = "") =>
  [...TALENT_MAP_KEY_PREFIX(projectId), status, "locations", search.trim()] as const;

/**
 * The same stage with the companies and the people left off — what the screen polls while the server
 * is still placing rows. A poll is waiting on a handful of points; re-reading the whole mandate to
 * collect them would put thousands of full profiles back on the wire every few seconds.
 */
export function getTalentMapLocations(
  projectId: string,
  status: TriageCompanyStatus,
  search: string,
  signal?: AbortSignal,
): Promise<TalentMapLocations> {
  const params = new URLSearchParams({ status });
  if (search.trim()) params.set("search", search.trim());
  return request<TalentMapLocations>(`/projects/${projectId}/talent-map/locations?${params}`, { signal });
}

export const TALENT_MAP_CONFIG_KEY = ["talentMap", "config"] as const;

export function getTalentMapConfig(signal?: AbortSignal): Promise<TalentMapConfig> {
  return request<TalentMapConfig>("/talent-map/config", { signal });
}
