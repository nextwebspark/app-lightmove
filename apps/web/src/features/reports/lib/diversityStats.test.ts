import { describe, expect, it } from "vitest";
import { SAMPLE_REPORT } from "../mock/sampleReport";
import {
  ALL_LEVELS_FILTER,
  ALL_NATIONALITIES_FILTER,
  diversityStats,
  feasibility,
  GCC_NATIONALS_FILTER,
} from "./diversityStats";

/** The pool's shape, and how many of it a client's nationality requirement can actually draw from. */
describe("diversityStats", () => {
  const stats = diversityStats(SAMPLE_REPORT.diversity);

  it("totals the pool by level and by nationality", () => {
    expect(stats.total).toBe(116);
    expect(stats.levelTotals).toEqual({ Board: 5, "C-Suite": 51, "N-1": 38, "N-2": 22 });
    expect(stats.nationalityCount).toBe(7);
    expect(stats.largestNationality).toBe("Saudi");
    expect(stats.largestPct).toBe(26);
    expect(stats.gccTotal).toBe(49);
  });

  it("finds the level where women are scarcest", () => {
    expect(stats.femaleTotal).toBe(37);
    expect(stats.femalePctByLevel["N-2"]).toBe(41);
    expect(stats.thinnestLevel).toBe("Board");
    expect(stats.thinnestPct).toBe(20);
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
