import { MutationCache, QueryClient, QueryObserver } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { markOverviewsStale, OVERVIEW_QUERY } from "./overviewQueries";

describe("overview queries", () => {
  // Completing a task elsewhere left Home showing it until a hard reload: no write named Home's queue.
  it("are read again after any successful write, while other reads keep their cache", async () => {
    const queryClient: QueryClient = new QueryClient({
      mutationCache: new MutationCache({ onSuccess: () => markOverviewsStale(queryClient) }),
      defaultOptions: { queries: { staleTime: 60_000, retry: false } },
    });
    const overview = vi.fn(async () => "queue");
    const other = vi.fn(async () => "list");
    const observer = new QueryObserver(queryClient, { queryKey: ["home"], queryFn: overview, meta: OVERVIEW_QUERY });
    const unsubscribe = observer.subscribe(() => {});
    await queryClient.fetchQuery({ queryKey: ["other"], queryFn: other });
    await vi.waitFor(() => expect(overview).toHaveBeenCalledTimes(1));

    await queryClient.getMutationCache().build(queryClient, { mutationFn: async () => "done" }).execute(undefined);

    await vi.waitFor(() => expect(overview).toHaveBeenCalledTimes(2));
    expect(queryClient.getQueryState(["other"])?.isInvalidated).toBe(false);
    unsubscribe();
  });
});
