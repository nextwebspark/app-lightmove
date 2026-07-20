import { request } from "../../../lib/apiClient";
import type { SourcingResponse } from "./types";

/** The companies matching one project's saved Strategy scope, one page at a time. */

export const SOURCING_KEY = (projectId: string, page: number, size: number) =>
  ["sourcing", projectId, page, size] as const;

export function getSourcingCompanies(
  projectId: string,
  page: number,
  size: number,
): Promise<SourcingResponse> {
  return request<SourcingResponse>(
    `/projects/${projectId}/sourcing?page=${page}&size=${size}`,
  );
}
