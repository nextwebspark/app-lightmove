import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useCountUp } from "./useCountUp";

/** A figure that follows a filter: it eases to the new value, from wherever it is on screen. */
describe("useCountUp", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("eases to a new target rather than snapping to it", () => {
    const { result, rerender } = renderHook(({ target }) => useCountUp(target), { initialProps: { target: 0 } });

    rerender({ target: 100 });
    act(() => void vi.advanceTimersByTime(250));
    expect(result.current).toBeGreaterThan(0);
    expect(result.current).toBeLessThan(100);

    act(() => void vi.advanceTimersByTime(400));
    expect(result.current).toBe(100);
  });

  it("carries on from the number on screen when the target changes mid-run", () => {
    const { result, rerender } = renderHook(({ target }) => useCountUp(target), { initialProps: { target: 0 } });

    rerender({ target: 100 });
    act(() => void vi.advanceTimersByTime(250));
    const onScreen = result.current;

    // A second filter change inside the window used to restart from 0, so the figure jumped backwards.
    rerender({ target: 200 });
    act(() => void vi.advanceTimersByTime(34));
    expect(result.current).toBeGreaterThanOrEqual(onScreen);

    act(() => void vi.advanceTimersByTime(600));
    expect(result.current).toBe(200);
  });
});
