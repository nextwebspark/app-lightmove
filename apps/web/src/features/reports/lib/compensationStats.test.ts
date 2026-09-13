import { describe, expect, it } from "vitest";
import { SAMPLE_REPORT } from "../mock/sampleReport";
import {
  ALL_COUNTRIES,
  ALL_NATIONALITIES,
  compensationStats,
  median,
  nationalityGap,
  percentileOf,
} from "./compensationStats";

/**
 * "Our ceiling sits at the Nth percentile" is the chapter's claim, so the rank, the count above the
 * band and the median are all derived from the disclosures — and withheld when there are too few.
 */
describe("compensationStats", () => {
  const remuneration = SAMPLE_REPORT.remuneration;
  const everyone = { country: ALL_COUNTRIES, nationality: ALL_NATIONALITIES };

  it("ranks the package ceiling against every disclosure", () => {
    const stats = compensationStats(remuneration, { measure: "package", ...everyone });

    expect(stats.disclosures).toHaveLength(16);
    expect(stats.isReliable).toBe(true);
    expect(stats.ceilingPercentile).toBe(38);
    expect(stats.aboveBand).toBe(9);
    expect(stats.median).toBe(1115);
    expect(stats.declined).toBe(6);
    expect(stats.declinedAboveBand).toBe(6);
  });

  it("looks far more competitive on fixed pay alone", () => {
    const stats = compensationStats(remuneration, { measure: "fixed", ...everyone });

    expect(stats.ceilingPercentile).toBe(69);
    expect(stats.aboveBand).toBe(5);
  });

  it("withholds the percentile below five disclosures rather than claim one", () => {
    const stats = compensationStats(remuneration, { measure: "package", country: "Kuwait", nationality: ALL_NATIONALITIES });

    expect(stats.disclosures).toHaveLength(1);
    expect(stats.isReliable).toBe(false);
    expect(stats.ceilingPercentile).toBeNull();
  });

  it("keeps the axis around both the band and the points", () => {
    const stats = compensationStats(remuneration, { measure: "package", ...everyone });

    expect(stats.axisLowK).toBeLessThan(620);
    expect(stats.axisHighK).toBeGreaterThan(1450);
    expect(stats.axisLowK % 50).toBe(0);
  });
});

describe("nationalityGap", () => {
  it("compares the largest group against the rest on total package", () => {
    const gap = nationalityGap(SAMPLE_REPORT.remuneration);

    expect(gap.largestNationality).toBe("Saudi");
    expect(gap.largestCount).toBe(9);
    expect(gap.restCount).toBe(7);
    expect(gap.largestMedian).toBe(980);
    expect(gap.restMedian).toBe(1190);
    expect(gap.higherSide).toBe("rest");
    expect(gap.gapPct).toBe(21);
    expect(gap.isReliable).toBe(true);
  });
});

describe("percentileOf and median", () => {
  it("rank by count below and split an even list", () => {
    expect(percentileOf([1, 2, 3, 4], 3)).toBe(50);
    expect(median([4, 1, 3, 2])).toBe(2.5);
    expect(median([3, 1, 2])).toBe(2);
  });
});
