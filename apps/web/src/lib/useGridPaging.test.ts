import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useGridPaging } from "./useGridPaging";

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
