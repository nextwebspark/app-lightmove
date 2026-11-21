import { request } from "../../../lib/apiClient";
import type { ChartAnswer, ChartQueryDef, ChartResult, Dashboard, DashboardScope, DashboardWidget, SaveDashboard } from "./types";

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

/** One dashboard's charts, answered together: the key is the charts asked, so an edit asks again. */
export const CHART_ANSWERS_KEY = (scope: DashboardScope, widgets: readonly Pick<DashboardWidget, "queryId" | "options">[]) =>
  ["dashboards", scopeKey(scope), "answers", widgets.map((widget) => [widget.queryId, widget.options])] as const;

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

/** A private copy of a dashboard, without any chart whose query has left the library. */
export function duplicateDashboard(scope: DashboardScope, id: string): Promise<Dashboard> {
  return request<Dashboard>(`${basePath(scope)}/${id}/duplicate`, { method: "POST" });
}

/** Every chart of a dashboard in one request, over one read of the data they share. */
export function getChartAnswers(
  scope: DashboardScope,
  widgets: readonly Pick<DashboardWidget, "queryId" | "options">[],
  signal?: AbortSignal,
): Promise<ChartAnswer[]> {
  return request<ChartAnswer[]>(`${basePath(scope)}/answers`, {
    method: "POST",
    body: { charts: widgets.map((widget) => ({ queryId: widget.queryId, options: widget.options })) },
    signal,
  });
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
