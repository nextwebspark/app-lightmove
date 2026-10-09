import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DrawerSkeleton, LinesSkeleton, SKELETON_DELAY_MS } from "./Skeleton";

/** A fast read must not flash a placeholder; a slow one must say something is coming, and what shape. */
describe("delayed skeletons", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("announces the wait at once but draws nothing until the delay has passed", () => {
    const { container } = render(<LinesSkeleton lines={4} />);

    expect(screen.getByRole("status", { name: "Loading" })).toBeEmptyDOMElement();

    act(() => vi.advanceTimersByTime(SKELETON_DELAY_MS));

    expect(container.querySelectorAll(".animate-pulse")).toHaveLength(4);
  });

  it("draws a drawer's header and sections once the read is slow", () => {
    render(<DrawerSkeleton />);
    act(() => vi.advanceTimersByTime(SKELETON_DELAY_MS));

    expect(screen.getByRole("status", { name: "Loading" })).not.toBeEmptyDOMElement();
  });
});
