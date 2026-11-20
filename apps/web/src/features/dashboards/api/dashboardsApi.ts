import { request } from "../../../lib/apiClient";
import type { ChartQueryDef, ChartResult, Dashboard, DashboardScope, SaveDashboard } from "./types";

/** Custom dashboards and their chart library; every figure is answered live by the server. */

function basePath(scope: DashboardScope): string {
  return scope.kind === "project" ? `/projects/${scope.projectId}/dashboards` : "/dashboards";
}

function scopeKey(scope: DashboardScope): string {
  return scope.kind === "project" ? scope.projectId : "workspace";
}

export const DASHBOARDS_KEY = (scope: DashboardScope) => ["dashboards", scopeKey(scope)] as const;

export const CHART_LIBRARY_KEY = (scope: DashboardScope) => ["dashboards", scopeKey(scope), "library"] as const;

export const CHART_RESULT_KEY = (scope: DashboardScope, queryId: string, options: Record<string, string>) =>
  ["dashboards", scopeKey(scope), "query", queryId, options] as const;

export function listDashboards(scope: DashboardScope, signal?: AbortSignal): Promise<Dashboard[]> {
  return request<Dashboard[]>(basePath(scope), { signal });
}

export function createDashboard(scope: DashboardScope, body: SaveDashboard): Promise<Dashboard> {
  return request<Dashboard>(basePath(scope), { method: "POST", body });
}

export function updateDashboard(scope: DashboardScope, id: string, body: SaveDashboard): Promise<Dashboard> {
  return request<Dashboard>(`${basePath(scope)}/${id}`, { method: "PUT", body });
}

export async function deleteDashboard(scope: DashboardScope, id: string): Promise<void> {
  await request<void>(`${basePath(scope)}/${id}`, { method: "DELETE" });
}

export function getChartLibrary(scope: DashboardScope, signal?: AbortSignal): Promise<ChartQueryDef[]> {
  return request<ChartQueryDef[]>(`${basePath(scope)}/library`, { signal });
}

export function runChartQuery(
  scope: DashboardScope,
  queryId: string,
  options: Record<string, string>,
  signal?: AbortSignal,
): Promise<ChartResult> {
  return request<ChartResult>(`${basePath(scope)}/query/${queryId}`, { method: "POST", body: { options }, signal });
}
