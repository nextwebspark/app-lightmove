import { describe, expect, it } from "vitest";
import { SAMPLE_REPORT } from "../../../test/sampleReport";
import { projectCoverage, rollingAverage, weeklyPace } from "./projection";

/**
 * The projection is the chapter's finding, so it is computed, not typed: the recent basis must
 * expose a slowdown the full-mandate average would blend away, both must land on a date, and
 * neither may name one the rows do not support.
 */
describe("projectCoverage", () => {
  const progress = SAMPLE_REPORT.progress;

  it("projects from the last three complete weeks by default and lands later than the target", () => {
    const projection = projectCoverage(progress, "recent");

    expect(projection.status).toBe("projected");
    // Weeks 4-6 gained 6, 4 and 2. Week 7 is the week in progress and is not one of the three.
    expect(projection.pace).toBeCloseTo(4, 5);
    expect(projection.remaining).toBe(11);
    expect(projection.targetPace).toBeCloseTo(7, 5);
    expect(projection.daysLate).toBe(26);
    expect(projection.projectedDate).toBe("2026-09-27");
  });

  it("leaves the week in progress out of the pace, rather than dividing it as a whole one", () => {
    const projection = projectCoverage(progress, "recent");

    expect(projection.lastWeek).toBe(7);
    expect(projection.completeWeeks).toBe(7);
    expect(projection.paceWeeks).toBe(3);
    // Counting the partial week would read 8 companies over three weeks instead of 12, and put the
    // projection ten days later than the rows support.
    expect(projection.pace).not.toBeCloseTo(8 / 3, 5);
  });

  it("blends a slowdown away when the full mandate is averaged in", () => {
    const slowing = { ...progress, companiesCumulative: [0, 12, 24, 30, 32, 33, 34, 34] };
    const recent = projectCoverage(slowing, "recent");
    const full = projectCoverage(slowing, "full");

    expect(recent.pace).toBeCloseTo(4 / 3, 5);
    expect(full.pace).toBeCloseTo(34 / 7, 5);
    expect(recent.daysLate).toBe(49);
    expect(full.daysLate).toBe(19);
  });

  it("reads an empty series as a kickoff week with nothing covered, never as NaN", () => {
    const projection = projectCoverage({ ...progress, companiesCumulative: [] }, "recent");

    expect(projection.coverage).toEqual([0]);
    expect(projection.covered).toBe(0);
    expect(projection.lastWeek).toBe(0);
    expect(projection.remaining).toBe(42);
    expect(projection.projectedDate).toBeNull();
  });

  it("has nothing to measure before a full week has passed, and says so rather than reading zero", () => {
    const fresh = { ...progress, asOf: "2026-07-22", companiesCumulative: [2] };

    const projection = projectCoverage(fresh, "recent");

    expect(projection.status).toBe("insufficient");
    expect(projection.completeWeeks).toBe(0);
    // The old reading was 0, indistinguishable from a mandate that stalled for a month.
    expect(projection.pace).toBeNull();
    expect(projection.projectedWeek).toBeNull();
    expect(projection.projectedDate).toBeNull();
    expect(projection.daysLate).toBeNull();
  });

  it("projects off a single complete week, which is the bar", () => {
    const week = { ...progress, asOf: "2026-07-27", companiesCumulative: [4] };

    const projection = projectCoverage(week, "recent");

    expect(projection.status).toBe("projected");
    expect(projection.completeWeeks).toBe(1);
    expect(projection.pace).toBeCloseTo(4, 5);
    expect(projection.projectedDate).not.toBeNull();
  });

  it("is stalled, not unmeasured, once there is history and still no movement", () => {
    const projection = projectCoverage({ ...progress, companiesCumulative: [0, 10, 10, 10, 10, 10, 10, 10] }, "recent");

    expect(projection.status).toBe("stalled");
    expect(projection.pace).toBe(0);
    expect(projection.projectedDate).toBeNull();
    expect(projection.daysLate).toBeNull();
  });

  it("names no date without a pace, and no slip without a target", () => {
    const untargeted = projectCoverage({ ...progress, targetDate: null }, "recent");

    expect(untargeted.projectedDate).toBe("2026-09-27");
    expect(untargeted.daysLate).toBeNull();
    expect(untargeted.targetPace).toBeNull();
  });

  it("asks no pace of a target date at or before kickoff", () => {
    const backdated = projectCoverage({ ...progress, targetDate: "2026-07-14" }, "recent");

    // A negative span used to yield a negative "needed" pace and print it beside the real one.
    expect(backdated.targetPace).toBeNull();
    expect(backdated.daysLate).toBe(75);
  });

  it("is complete, not projected, once every company is covered", () => {
    const done = projectCoverage({ ...progress, companiesCumulative: [0, 20, 42] }, "recent");

    expect(done.status).toBe("complete");
    expect(done.remaining).toBe(0);
    // Completion is not a forecast: the old reading named the kickoff date as a projected one.
    expect(done.projectedWeek).toBeNull();
    expect(done.projectedDate).toBeNull();
  });

  it("counts a universe of two, covered on day one, as covered rather than as no history", () => {
    const tiny = { ...progress, asOf: "2026-07-21", targetCompanies: 2, companiesCumulative: [2] };

    expect(projectCoverage(tiny, "recent").status).toBe("complete");
  });

  it("does not read an empty universe as full coverage", () => {
    const empty = { ...progress, asOf: "2026-07-21", targetCompanies: 0, companiesCumulative: [0] };

    const projection = projectCoverage(empty, "recent");

    // Nothing scoped leaves nothing remaining, which the chapter used to report as every company
    // mapped — to a mandate holding no companies at all.
    expect(projection.status).toBe("no-universe");
    expect(projection.projectedDate).toBeNull();
  });
});

describe("weeklyPace", () => {
  it("splits the complete weeks into the first month and the last three", () => {
    const pace = weeklyPace(SAMPLE_REPORT.progress, 7);

    // The total is a tally of every week, the week in progress included; the averages are rates and
    // read complete weeks only.
    expect(pace.total).toBe(116);
    expect(pace.firstMonth).toBeCloseTo(18, 5);
    expect(pace.recent).toBeCloseTo(40 / 3, 5);
    expect(pace.average).toBeCloseTo(16, 5);
  });

  it("claims no average at all before a week is complete", () => {
    const pace = weeklyPace(SAMPLE_REPORT.progress, 0);

    expect(pace.firstMonth).toBe(0);
    expect(pace.recent).toBe(0);
    expect(pace.total).toBe(116);
  });
});

describe("rollingAverage", () => {
  it("expands over the first points and trails from the window on", () => {
    expect(rollingAverage([2, 4, 6, 8], 3)).toEqual([2, 3, 4, 6]);
  });
});
