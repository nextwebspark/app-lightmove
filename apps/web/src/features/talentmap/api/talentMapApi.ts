import { request } from "../../../lib/apiClient";
import type { TriageCompanyStatus } from "../../triage/api/types";
import type { TalentMapConfig, TalentMapPage } from "./types";

/**
 * The map view's two reads: one stage as points, and whether the map is offered at all.
 *
 * <p>The stage read is unpaged — a globe with a page two is no globe — and the server caps it
 * instead, stating the totals so a mandate past the cap is told rather than shown a map that looks
 * complete and is not.
 */

export const TALENT_MAP_KEY_PREFIX = (projectId: string) => ["talentMap", projectId] as const;

export const TALENT_MAP_KEY = (projectId: string, status: TriageCompanyStatus) =>
  [...TALENT_MAP_KEY_PREFIX(projectId), status] as const;

export function getTalentMap(
  projectId: string,
  status: TriageCompanyStatus,
  signal?: AbortSignal,
): Promise<TalentMapPage> {
  return request<TalentMapPage>(`/projects/${projectId}/talent-map?status=${status}`, { signal });
}

export const TALENT_MAP_CONFIG_KEY = ["talentMap", "config"] as const;

export function getTalentMapConfig(signal?: AbortSignal): Promise<TalentMapConfig> {
  return request<TalentMapConfig>("/talent-map/config", { signal });
}
