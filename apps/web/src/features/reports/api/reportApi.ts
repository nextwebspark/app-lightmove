import { request } from "../../../lib/apiClient";
import type { Report } from "./types";

/** The report screen's single read. Everything on it is derived server-side from the saved scope. */

export const REPORT_KEY = (projectId: string) => ["report", projectId] as const;

export function getReport(projectId: string): Promise<Report> {
  return request<Report>(`/projects/${projectId}/report`);
}
