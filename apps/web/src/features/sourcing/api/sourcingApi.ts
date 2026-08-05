import { request } from "../../../lib/apiClient";
import type { SourcingResponse, SourcingSort } from "./types";

/** The companies matching one project's saved Strategy scope, fetched a page at a time and
 *  accumulated as the user scrolls (see `SourcingPage`'s `useInfiniteQuery`). */

export const SOURCING_KEY_PREFIX = (projectId: string) => ["sourcing", projectId] as const;

/**
 * The query key carries the name filter and the sort, not just the page size: changing either changes
 * which companies exist and in what order, so it has to start a fresh infinite query at page 0 rather
 * than append a differently-ordered page onto the accumulated list. A Strategy save still invalidates
 * every variant at once through `SOURCING_KEY_PREFIX`.
 */
export const SOURCING_KEY = (
  projectId: string,
  size: number,
  query: string,
  sort: SourcingSort | null,
) =>
  [
    ...SOURCING_KEY_PREFIX(projectId),
    size,
    query,
    sort ? `${sort.field}:${sort.direction}` : "default",
  ] as const;

export function getSourcingCompanies(
  projectId: string,
  page: number,
  size: number,
  query: string,
  sort: SourcingSort | null,
): Promise<SourcingResponse> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (query) {
    params.set("q", query);
  }
  if (sort) {
    params.set("sort", sort.field);
    params.set("direction", sort.direction);
  }
  return request<SourcingResponse>(`/projects/${projectId}/sourcing?${params}`);
}
