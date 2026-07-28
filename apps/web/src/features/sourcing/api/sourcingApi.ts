import { request } from "../../../lib/apiClient";
import type { SourcingResponse, SourcingRunResponse } from "./types";

/** The companies matching one project's saved Strategy scope, fetched a page at a time and
 *  accumulated as the user scrolls (see `SourcingPage`'s `useInfiniteQuery`). */

export const SOURCING_KEY_PREFIX = (projectId: string) => ["sourcing", projectId] as const;

export const SOURCING_KEY = (projectId: string, size: number) =>
  [...SOURCING_KEY_PREFIX(projectId), size] as const;

export function getSourcingCompanies(
  projectId: string,
  page: number,
  size: number,
): Promise<SourcingResponse> {
  return request<SourcingResponse>(
    `/projects/${projectId}/sourcing?page=${page}&size=${size}`,
  );
}

// ── CoreSignal run flow (POC) ────────────────────────────────────────────────
// Starting/extending spends provider credits (hence POSTs); polling is a plain read the page
// repeats while the run is active.

export const RUN_KEY = (projectId: string) => ["sourcing-run", projectId] as const;

export function getCurrentRun(projectId: string): Promise<SourcingRunResponse> {
  return request<SourcingRunResponse>(`/projects/${projectId}/sourcing/runs/current`);
}

export function startRun(projectId: string): Promise<SourcingRunResponse> {
  return request<SourcingRunResponse>(`/projects/${projectId}/sourcing/runs`, { method: "POST" });
}

export function extendRun(projectId: string): Promise<SourcingRunResponse> {
  return request<SourcingRunResponse>(`/projects/${projectId}/sourcing/runs/current/extend`, {
    method: "POST",
  });
}
