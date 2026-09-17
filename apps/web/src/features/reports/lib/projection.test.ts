import { describe, expect, it } from "vitest";
import { SAMPLE_REPORT } from "../../../test/sampleReport";
import { projectCoverage, rollingAverage, weeklyPace } from "./projection";

/**
 * The projection is the chapter's finding, so it is computed, not typed: the recent basis must
 * expose a slowdown the full-mandate average would blend away, and both must land on a date.
 */
describe("projectCoverage", () => {
  const progress = SAMPLE_REPORT.progress;

  it("projects from the last three weeks by default and lands later than the target", () => {
    const projection = projectCoverage(progress, "recent");

    expect(projection.pace).toBeCloseTo(8 / 3, 5);
    expect(projection.remaining).toBe(11);
    expect(projection.targetPace).toBeCloseTo(7, 5);
    expect(projection.daysLate).toBe(36);
    expect(projection.projectedDate).toBe("2026-10-07");
  });

  it("understates the slip when the faster early weeks are blended in", () => {
    const recent = projectCoverage(progress, "recent");
    const full = projectCoverage(progress, "full");

    expect(full.pace).toBeGreaterThan(recent.pace);
    expect(full.daysLate).toBe(24);
  });

  it("reads an empty series as a kickoff week with nothing covered, never as NaN", () => {
    const projection = projectCoverage({ ...progress, companiesCumulative: [] }, "recent");

    expect(projection.coverage).toEqual([0]);
    expect(projection.covered).toBe(0);
    expect(projection.lastWeek).toBe(0);
    expect(projection.remaining).toBe(42);
    expect(projection.projectedDate).toBeNull();
  });

  it("has no date to name at zero pace, and no slip without a target", () => {
    const stalled = projectCoverage({ ...progress, companiesCumulative: [0, 10, 10, 10, 10] }, "recent");
    expect(stalled.projectedDate).toBeNull();
    expect(stalled.daysLate).toBeNull();

    const untargeted = projectCoverage({ ...progress, targetDate: null }, "recent");
    expect(untargeted.projectedDate).toBe("2026-10-07");
    expect(untargeted.daysLate).toBeNull();
    expect(untargeted.targetPace).toBeNull();
  });

  it("is complete, not projected, once every company is covered", () => {
    const done = projectCoverage({ ...progress, companiesCumulative: [0, 20, 42] }, "recent");

    expect(done.remaining).toBe(0);
    expect(done.projectedWeek).toBe(2);
  });
});

describe("weeklyPace", () => {
  it("splits the run into its first month and its last three weeks", () => {
    const pace = weeklyPace(SAMPLE_REPORT.progress);

    expect(pace.total).toBe(116);
    expect(pace.firstMonth).toBeCloseTo(18, 5);
    expect(pace.recent).toBeCloseTo(8, 5);
  });
});

describe("rollingAverage", () => {
  it("expands over the first points and trails from the window on", () => {
    expect(rollingAverage([2, 4, 6, 8], 3)).toEqual([2, 3, 4, 6]);
  });
});
