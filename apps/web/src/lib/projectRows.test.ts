import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { useProjectRowsChanged } from "./projectRows";

/**
 * The one place that knows what filing companies into a mandate makes stale.
 *
 * <p>Both assertions are about the part a page-local refresher gets to take for granted: that it is
 * refreshing the project on screen, and that a read still in flight cannot undo the write.
 */
function mount() {
  const queryClient = new QueryClient();
  const order: string[] = [];
  const cancelled: unknown[] = [];
  const invalidated: unknown[] = [];

  // Settles a tick late, deliberately: a cancel that records synchronously would keep its place in
  // the order whether or not the caller waited for it, and the test would pass without the await.
  vi.spyOn(queryClient, "cancelQueries").mockImplementation(async (filters) => {
    await Promise.resolve();
    order.push("cancel");
    cancelled.push(filters?.queryKey);
  });
  vi.spyOn(queryClient, "invalidateQueries").mockImplementation(async (filters) => {
    order.push("invalidate");
    invalidated.push(filters?.queryKey);
  });

  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  const { result } = renderHook(() => useProjectRowsChanged(), { wrapper });
  return { rowsChanged: result.current, order, cancelled, invalidated };
}

describe("useProjectRowsChanged", () => {
  it("refreshes the grid, its counts, the people and the map for that mandate", async () => {
    const { rowsChanged, invalidated } = mount();

    await rowsChanged("p1");

    expect(invalidated).toEqual([
      // The stage counts hang under the triage prefix, so the sidebar's badges move with the grid.
      ["triage", "p1"],
      ["candidates", "p1"],
      ["talentMap", "p1"],
      ["strategyCompanies", "p1"],
    ]);
  });

  // A read left running would resolve after the invalidation and reinstate the pre-write rows as
  // fresh for the whole staleTime — the company filed would vanish again in front of the user.
  it("cancels every read before invalidating any of them", async () => {
    const { rowsChanged, order } = mount();

    await rowsChanged("p1");

    expect(order.lastIndexOf("cancel")).toBeLessThan(order.indexOf("invalidate"));
  });

  it("refreshes the mandate it was given, not some other one", async () => {
    const { rowsChanged, cancelled } = mount();

    await rowsChanged("p2");

    expect(cancelled.every((key) => (key as string[])[1] === "p2")).toBe(true);
  });
});
