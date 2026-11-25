import type { QueryClient } from "@tanstack/react-query";

/**
 * An overview's read gathers what many screens write — Home's queue, tasks and calendar — so no one write's own
 * invalidation names it. Any successful write anywhere marks every query carrying this meta stale: a mounted one
 * reads again at once, the rest the next time their screen opens.
 */
export const OVERVIEW_QUERY = { staleAfterAnyWrite: true } as const;

/** While an overview is on screen it also reads again on its own: replies and calendar changes arrive by webhook. */
export const OVERVIEW_REFRESH_MS = 120_000;

export function markOverviewsStale(queryClient: QueryClient): void {
  void queryClient.invalidateQueries({ predicate: (query) => query.meta?.staleAfterAnyWrite === true });
}
