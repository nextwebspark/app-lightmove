import { request } from "../../../lib/apiClient";
import type { SourcingResponse } from "./types";

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
