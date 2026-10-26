import type { QueryClient } from "@tanstack/react-query";

/**
 * Refetches what a pushed outreach or task change can move: every open outreach read but Book a call's calendar, which
 * asks the mail provider and would shift under the consultant's cursor. Without a position, every one's.
 */
export function refreshOutreachActivity(queryClient: QueryClient, projectId?: string): void {
  void queryClient.invalidateQueries({
    queryKey: projectId ? ["outreach", projectId] : ["outreach"],
    predicate: (query) => !query.queryKey.includes("calendar"),
  });
}
