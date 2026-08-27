import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { useGridLayout } from "./useGridLayout";

// Deliberately no project id: a column layout is a working habit, not a property of one mandate.
const KEY = "lm.strategy.layout";

const COLUMNS = [
  { id: "name", min: 230 },
  { id: "revenue", min: 120 },
  { id: "employees", min: 110 },
];

describe("remembered column layout", () => {
  beforeEach(() => localStorage.clear());

  it("keeps the whole layout under one key per grid, not per project", () => {
    const { result } = renderHook(() => useGridLayout("strategy", COLUMNS));

    act(() => result.current[1]({ order: ["name", "employees"], widths: { revenue: 200 } }));

    expect(JSON.parse(localStorage.getItem(KEY) ?? "{}")).toEqual({
      order: ["name", "employees"],
      widths: { revenue: 200 },
    });
  });

  it("drops an order entry for a column the grid no longer declares", () => {
    // A drop persists every column, so a real record names them all — plus, here, one since retired.
    localStorage.setItem(
      KEY,
      JSON.stringify({ order: ["revenue", "keywords", "name", "employees"], widths: {} }),
    );

    const { result } = renderHook(() => useGridLayout("strategy", COLUMNS));

    expect(result.current[0].order).toEqual(["revenue", "name", "employees"]);
  });

  it("splices a column the record predates back where it was declared", () => {
    // TanStack appends what it is not told about to the end, and a drop persists the whole list —
    // so leaving the new column out would put it off the right edge for everyone who ever dragged.
    localStorage.setItem(KEY, JSON.stringify({ order: ["revenue", "name"], widths: {} }));

    const { result } = renderHook(() => useGridLayout("strategy", COLUMNS));

    expect(result.current[0].order).toEqual(["revenue", "name", "employees"]);
  });

  it("leaves an order it was given none of alone, so a fresh grid keeps its declared one", () => {
    localStorage.setItem(KEY, JSON.stringify({ order: [], widths: { revenue: 200 } }));

    const { result } = renderHook(() => useGridLayout("strategy", COLUMNS));

    expect(result.current[0].order).toEqual([]);
  });

  it("does not write on mount, so a record it could not parse survives for a later release", () => {
    localStorage.setItem(KEY, "{ truncated");

    renderHook(() => useGridLayout("strategy", COLUMNS));

    expect(localStorage.getItem(KEY)).toBe("{ truncated");
  });

  it("drops a width for a column that no longer exists", () => {
    localStorage.setItem(KEY, JSON.stringify({ order: [], widths: { revenue: 200, gone: 400 } }));

    const { result } = renderHook(() => useGridLayout("strategy", COLUMNS));

    expect(result.current[0].widths).toEqual({ revenue: 200 });
  });

  it("raises a stored width that sits below the column's floor", () => {
    // A width under the floor would break the layout's `minmax` and clip the column away.
    localStorage.setItem(KEY, JSON.stringify({ order: [], widths: { name: 40 } }));

    const { result } = renderHook(() => useGridLayout("strategy", COLUMNS));

    expect(result.current[0].widths.name).toBe(230);
  });

  it("falls back to the declared layout when the record is not a layout at all", () => {
    localStorage.setItem(KEY, '{"order":"revenue","widths"');

    const { result } = renderHook(() => useGridLayout("strategy", COLUMNS));

    expect(result.current[0]).toEqual({ order: [], widths: {} });
  });

  it("ignores a width that is not a finite number", () => {
    localStorage.setItem(
      KEY,
      JSON.stringify({ order: [], widths: { name: "wide", revenue: null, employees: 150 } }),
    );

    const { result } = renderHook(() => useGridLayout("strategy", COLUMNS));

    expect(result.current[0].widths).toEqual({ employees: 150 });
  });
});
