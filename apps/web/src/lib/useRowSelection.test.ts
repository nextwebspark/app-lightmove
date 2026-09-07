import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useRowSelection } from "./useRowSelection";

const PAGE = ["a", "b", "c", "d"];

describe("useRowSelection", () => {
  it("ticks and unticks one row at a time", () => {
    const { result } = renderHook(() => useRowSelection(PAGE));

    act(() => result.current.toggle("b"));
    expect(result.current.count).toBe(1);
    expect(result.current.has("b")).toBe(true);
    expect(result.current.someOnPage).toBe(true);
    expect(result.current.allOnPage).toBe(false);

    act(() => result.current.toggle("b"));
    expect(result.current.count).toBe(0);
  });

  it("extends from the last box touched on a shift-click, in either direction", () => {
    const { result } = renderHook(() => useRowSelection(PAGE));

    act(() => result.current.toggle("b"));
    act(() => result.current.toggle("d", true));
    expect([...result.current.ids]).toEqual(["b", "c", "d"]);

    // Backwards from the new anchor, and unticking, because the box being clicked was already ticked.
    act(() => result.current.toggle("b", true));
    expect(result.current.count).toBe(0);
  });

  it("treats a shift-click as an ordinary click when the anchor is not on this page", () => {
    // The anchor was ticked, then the user paged. Painting rows they cannot see is worse than
    // ignoring the modifier.
    const { result, rerender } = renderHook(({ ids }) => useRowSelection(ids), {
      initialProps: { ids: PAGE },
    });

    act(() => result.current.toggle("a"));
    rerender({ ids: ["x", "y", "z"] });
    act(() => result.current.toggle("z", true));

    expect([...result.current.ids]).toEqual(["a", "z"]);
  });

  it("select-all covers the page, and a second press clears it", () => {
    const { result } = renderHook(() => useRowSelection(PAGE));

    act(() => result.current.toggleAllOnPage());
    expect(result.current.allOnPage).toBe(true);
    expect(result.current.count).toBe(4);

    act(() => result.current.toggleAllOnPage());
    expect(result.current.count).toBe(0);
  });

  it("select-all over a partial page fills it in rather than emptying it", () => {
    const { result } = renderHook(() => useRowSelection(PAGE));

    act(() => result.current.toggle("c"));
    act(() => result.current.toggleAllOnPage());

    expect(result.current.allOnPage).toBe(true);
  });

  it("keeps ticks made on another page — the case bulk actions exist for", () => {
    const { result, rerender } = renderHook(({ ids }) => useRowSelection(ids), {
      initialProps: { ids: PAGE },
    });

    act(() => result.current.toggle("a"));
    rerender({ ids: ["x", "y"] });

    expect(result.current.count).toBe(1);
    // Nothing on this page is ticked, so the select-all box reads unchecked rather than mixed.
    expect(result.current.someOnPage).toBe(false);

    act(() => result.current.toggleAllOnPage());
    expect([...result.current.ids]).toEqual(["a", "x", "y"]);
  });

  it("reports an empty page as neither all nor partly selected", () => {
    // `every` over an empty array is true, which would tick the select-all box of a table with no
    // rows in it.
    const { result } = renderHook(() => useRowSelection([]));

    expect(result.current.allOnPage).toBe(false);
    expect(result.current.someOnPage).toBe(false);
  });
});
