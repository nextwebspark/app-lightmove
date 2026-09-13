import { SAMPLE_REPORT } from "../mock/sampleReport";
import type { Report } from "./types";

/**
 * The report screen's single read. There is no endpoint behind it yet — the screen is being built
 * ahead of its backend, so this resolves the sample mandate for every project. When the read exists
 * this becomes `request<Report>(`/projects/${projectId}/report`)` and nothing above it changes.
 */

export const REPORT_KEY = (projectId: string) => ["report", projectId] as const;

export function getReport(_projectId: string): Promise<Report> {
  return Promise.resolve(SAMPLE_REPORT);
}
