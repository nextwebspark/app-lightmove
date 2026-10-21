import type { QueryClient } from "@tanstack/react-query";

/**
 * Refetches what a pushed outreach or task change can move: every open outreach read but a call's free slots, which
 * ask the mail provider and would shift under the consultant's cursor in Book a call. Without a position, every one's.
 */
export function refreshOutreachActivity(queryClient: QueryClient, projectId?: string): void {
  void queryClient.invalidateQueries({
    queryKey: projectId ? ["outreach", projectId] : ["outreach"],
    predicate: (query) => !query.queryKey.includes("slots"),
  });
}
