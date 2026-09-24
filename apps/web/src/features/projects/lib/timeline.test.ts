import { describe, expect, it } from "vitest";
import { autoMappingTarget, daysBetween, todayIso } from "./timeline";

describe("timeline", () => {
  it("counts whole days across a month end", () => {
    expect(daysBetween("2026-09-24", "2026-10-15")).toBe(21);
  });

  it("puts the mapping target at 60% of the window, rounded to the day — as ProjectTimeline does", () => {
    expect(autoMappingTarget("2026-09-24", "2026-11-08")).toBe("2026-10-21");
    expect(autoMappingTarget("2026-01-01", "2026-02-20")).toBe("2026-01-31");
  });

  it("has no target while either end is missing or the window runs backwards", () => {
    expect(autoMappingTarget("2026-09-24", "")).toBeNull();
    expect(autoMappingTarget("2026-09-24", "2026-09-24")).toBeNull();
    expect(autoMappingTarget("2026-09-24", "2026-09-01")).toBeNull();
  });

  it("reads today from the local calendar", () => {
    expect(todayIso(new Date(2026, 8, 24, 23, 30))).toBe("2026-09-24");
  });
});
