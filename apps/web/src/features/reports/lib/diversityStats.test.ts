import { describe, expect, it } from "vitest";
import { SAMPLE_REPORT } from "../mock/sampleReport";
import {
  ALL_LEVELS_FILTER,
  ALL_NATIONALITIES_FILTER,
  diversityStats,
  feasibility,
  GCC_NATIONALS_FILTER,
  nationalityFilterOptions,
} from "./diversityStats";

/** The pool's shape by nationality, and how many of it a client's requirement can actually draw from. */
describe("diversityStats", () => {
  const stats = diversityStats(SAMPLE_REPORT.diversity);

  it("totals the pool by level and names the largest group", () => {
    expect(stats.total).toBe(116);
    expect(stats.levelTotals).toEqual({ Board: 5, "C-Suite": 51, "N-1": 38, "N-2": 22, "N-3": 0 });
    expect(stats.nationalityCount).toBe(6);
    expect(stats.largest?.nationality).toBe("Saudi");
    expect(stats.largestPct).toBe(26);
    expect(stats.gccPct).toBe(42);
  });

  it("offers every named group as a requirement, never the Other bucket", () => {
    const options = nationalityFilterOptions(SAMPLE_REPORT.diversity);

    expect(options.slice(0, 2)).toEqual([ALL_NATIONALITIES_FILTER, GCC_NATIONALS_FILTER]);
    expect(options).toContain("Saudi");
    expect(options).not.toContain("Other");
  });

  it("has no largest group when nobody has a nationality on file", () => {
    const empty = diversityStats({ ...SAMPLE_REPORT.diversity, nationalities: [], gccNationals: 0 });

    expect(empty.largest).toBeNull();
    expect(empty.total).toBe(0);
  });
});

describe("feasibility", () => {
  const diversity = SAMPLE_REPORT.diversity;
  const stats = diversityStats(diversity);

  it("is the whole pool when nothing is required", () => {
    const fit = feasibility(diversity, stats, { nationality: ALL_NATIONALITIES_FILTER, level: ALL_LEVELS_FILTER });

    expect(fit.qualifying).toBe(116);
    expect(fit.scope).toBe(116);
  });

  it("narrows to GCC nationals at one level", () => {
    const fit = feasibility(diversity, stats, { nationality: GCC_NATIONALS_FILTER, level: "C-Suite" });

    expect(fit.qualifying).toBe(23);
    expect(fit.scope).toBe(51);
    expect(fit.byLevel.find((l) => l.level === "C-Suite")?.isInScope).toBe(true);
    expect(fit.byLevel.find((l) => l.level === "Board")?.isInScope).toBe(false);
  });

  it("says none when a nationality has nobody at a level", () => {
    const fit = feasibility(diversity, stats, { nationality: "Indian", level: "Board" });

    expect(fit.qualifying).toBe(0);
    expect(fit.scope).toBe(5);
  });
});
