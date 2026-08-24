import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { useColumnVisibility } from "./useColumnVisibility";

const KEY = "lm.strategy.columns.p1";

describe("remembered column visibility", () => {
  beforeEach(() => localStorage.clear());

  it("keeps a column a record written before it existed says nothing about hidden", () => {
    // The shipped record from an earlier release. Every column added since is absent from it, and
    // reading it verbatim turned every one of them on for everybody who had used the screen.
    localStorage.setItem(KEY, JSON.stringify({ founded: false }));

    const { result } = renderHook(() =>
      useColumnVisibility("p1", { founded: false, keywords: false }),
    );

    expect(result.current[0]).toEqual({ founded: false, keywords: false });
  });

  it("lets a stored tick override the default", () => {
    localStorage.setItem(KEY, JSON.stringify({ keywords: true }));

    const { result } = renderHook(() => useColumnVisibility("p1", { keywords: false }));

    expect(result.current[0].keywords).toBe(true);
  });
});
