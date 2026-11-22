import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { useAddressedGridPaging, useGridPaging } from "./useGridPaging";

describe("useGridPaging", () => {
  it("starts on the first page at the default size, in both shapes", () => {
    const { result } = renderHook(() => useGridPaging());
    expect(result.current.page).toBe(0);
    expect(result.current.size).toBe(50);
    expect(result.current.pagination).toEqual({ pageIndex: 0, pageSize: 50 });
  });

  it("moves page and size independently", () => {
    const { result } = renderHook(() => useGridPaging(25));
    act(() => result.current.setPage(3));
    act(() => result.current.setSize(100));
    expect(result.current.pagination).toEqual({ pageIndex: 3, pageSize: 100 });
  });

  it("takes the table's own updater, so the grid can page itself", () => {
    const { result } = renderHook(() => useGridPaging());
    act(() => result.current.onPaginationChange((current) => ({ ...current, pageIndex: 2 })));
    expect(result.current.page).toBe(2);
  });

  it("clamps onto the last page that still exists, and leaves a page that does when nothing shrank", () => {
    const { result } = renderHook(() => useGridPaging(25));
    act(() => result.current.setPage(3));
    const before = result.current.pagination;
    act(() => result.current.clampTo(100));
    expect(result.current.pagination).toBe(before);

    act(() => result.current.clampTo(30));
    expect(result.current.page).toBe(1);
    act(() => result.current.clampTo(0));
    expect(result.current.page).toBe(0);
  });

  it("resets to the first page without a new state object when it is already there", () => {
    const { result } = renderHook(() => useGridPaging());
    const before = result.current.pagination;
    act(() => result.current.reset());
    expect(result.current.pagination).toBe(before);

    act(() => result.current.setPage(4));
    act(() => result.current.reset());
    expect(result.current.page).toBe(0);
  });
});

describe("useAddressedGridPaging", () => {
  const renderAt = (start: string) =>
    renderHook(() => ({ paging: useAddressedGridPaging("page", 25), location: useLocation() }), {
      wrapper: ({ children }: { children: ReactNode }) => (
        <MemoryRouter initialEntries={[start]}>{children}</MemoryRouter>
      ),
    });

  it("reads and writes the page in the address, one-based and absent on the first", () => {
    const { result } = renderAt("/clients?page=3");
    expect(result.current.paging.page).toBe(2);

    act(() => result.current.paging.setPage(0));
    expect(result.current.location.search).toBe("");
    act(() => result.current.paging.setPage(4));
    expect(result.current.location.search).toBe("?page=5");
  });

  it("keeps a returned page through the reset every list runs on mount, and resets once settled", async () => {
    const { result } = renderAt("/clients?page=3");
    act(() => result.current.paging.reset());
    expect(result.current.paging.page).toBe(2);

    await act(() => new Promise((settle) => setTimeout(settle)));
    act(() => result.current.paging.reset());
    expect(result.current.paging.page).toBe(0);
  });

  it("clamps onto the last page that still exists", () => {
    const { result } = renderAt("/clients?page=4");
    act(() => result.current.paging.clampTo(30));
    expect(result.current.paging.page).toBe(1);
  });
});
