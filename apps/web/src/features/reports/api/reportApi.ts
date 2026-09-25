import { request } from "../../../lib/apiClient";
import type { Report, TeamPerformance } from "./types";

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
