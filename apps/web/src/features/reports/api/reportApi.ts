import { request } from "../../../lib/apiClient";
import type { Ranking, RankingScenario, Report, SaveRankingScenario, TeamPerformance } from "./types";

/** The report screen's reads. Everything on it is aggregated server-side at read time. */

export const REPORT_KEY = (projectId: string) => ["report", projectId] as const;

export function getReport(projectId: string, signal?: AbortSignal): Promise<Report> {
  return request<Report>(`/projects/${projectId}/report`, { signal });
}

export interface TeamRange {
  from?: string;
  to?: string;
}

export const TEAM_REPORT_KEY = (projectId: string, range: TeamRange) =>
  ["report", projectId, "team", range.from ?? null, range.to ?? null] as const;

/** Researcher performance — staff-only; a client seat is refused, and the screen never asks for one. */
export function getTeamPerformance(projectId: string, range: TeamRange, signal?: AbortSignal): Promise<TeamPerformance> {
  const params = new URLSearchParams();
  if (range.from) params.set("from", range.from);
  if (range.to) params.set("to", range.to);
  const query = params.toString();
  return request<TeamPerformance>(`/projects/${projectId}/report/team${query ? `?${query}` : ""}`, { signal });
}

export const RANKING_KEY = (projectId: string) => ["report", projectId, "ranking"] as const;

/** The Ranking chapter — staff-only; a client seat is refused, and the screen never asks for one. */
export function getRanking(projectId: string, signal?: AbortSignal): Promise<Ranking> {
  return request<Ranking>(`/projects/${projectId}/report/ranking`, { signal });
}

/** Keeps the scenario, or clears it (204, `null`) when it is the brief's own weighting. */
export async function saveRankingScenario(
  projectId: string,
  scenario: SaveRankingScenario,
): Promise<RankingScenario | null> {
  const saved = await request<RankingScenario | undefined>(`/projects/${projectId}/report/ranking/scenario`, {
    method: "PUT",
    body: scenario,
  });
  return saved ?? null;
}
