import { request } from "../../../lib/apiClient";
import type { Report } from "./types";

/** The report screen's single read. Everything on it is aggregated server-side at read time. */

export const REPORT_KEY = (projectId: string) => ["report", projectId] as const;

export function getReport(projectId: string, signal?: AbortSignal): Promise<Report> {
  return request<Report>(`/projects/${projectId}/report`, { signal });
}
