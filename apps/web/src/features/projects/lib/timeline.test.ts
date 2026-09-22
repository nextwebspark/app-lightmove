import { describe, expect, it } from "vitest";
import { autoMappingTarget, daysFromStart, timelineProblem } from "./timeline";

/**
 * The modal previews the same milestone the server will save, so these cases are pinned against the
 * worked examples in the API's own MandateTimelineTest.
 */
describe("autoMappingTarget", () => {
  it("lands at 60% of the window to the shortlist", () => {
    expect(autoMappingTarget("2026-09-21", "2026-12-30")).toBe("2026-11-20");
  });

  it("rounds half up, as the server does", () => {
    // 41 days × 0.6 = 24.6 → the 25th day, on both sides of the wire.
    expect(autoMappingTarget("2026-09-21", "2026-11-01")).toBe("2026-10-16");
  });
});

describe("daysFromStart", () => {
  it("counts the days a window runs for", () => {
    expect(daysFromStart("2026-09-21", "2026-11-15")).toBe(55);
  });

  it("has nothing to count without a date", () => {
    expect(daysFromStart("2026-09-21", null)).toBeNull();
  });
});

describe("timelineProblem", () => {
  it("a search must say when its shortlist is due", () => {
    expect(timelineProblem("EXECUTIVE_SEARCH", "2026-09-21", "2026-10-16", "")).toEqual({
      field: "shortlistTargetDate",
      message: expect.stringContaining("shortlist"),
    });
  });

  it("a mapping mandate needs no shortlist at all", () => {
    expect(timelineProblem("MAPPING", "2026-09-21", "2026-11-15", "")).toBeNull();
  });

  it("a milestone cannot fall before the mandate starts", () => {
    expect(timelineProblem("MAPPING", "2026-09-21", "2026-09-01", "")?.field)
      .toBe("mappingTargetDate");
  });

  it("a shortlist cannot be due before the mapping it draws on", () => {
    expect(timelineProblem("EXECUTIVE_SEARCH", "2026-09-21", "2026-11-15", "2026-10-01")?.field)
      .toBe("shortlistTargetDate");
  });
});
