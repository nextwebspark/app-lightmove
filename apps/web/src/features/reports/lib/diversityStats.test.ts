import { describe, expect, it } from "vitest";
import { SAMPLE_REPORT } from "../../../test/sampleReport";
import {
  ALL_LEVELS_FILTER,
  ALL_NATIONALITIES_FILTER,
  diversityStats,
  feasibility,
  GCC_NATIONALS_FILTER,
  genderStats,
  levelFilterOptions,
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
    expect(stats.gccPct).toBe(50);
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

    expect(fit.qualifying).toBe(27);
    expect(fit.scope).toBe(51);
    expect(fit.byLevel.find((l) => l.level === "C-Suite")?.isInScope).toBe(true);
    expect(fit.byLevel.find((l) => l.level === "Board")?.isInScope).toBe(false);
  });

  it("says none when a group has nobody at a level", () => {
    const fit = feasibility(diversity, stats, { nationality: "South Asian", level: "Board" });

    expect(fit.qualifying).toBe(0);
    expect(fit.scope).toBe(5);
    // The level holds five people, so the zero is the requirement's answer and the row stays.
    expect(fit.byLevel.find((l) => l.level === "Board")?.count).toBe(0);
  });

  it("leaves out a level nobody is mapped at, rather than listing it as none", () => {
    const fit = feasibility(diversity, stats, { nationality: ALL_NATIONALITIES_FILTER, level: ALL_LEVELS_FILTER });

    // Nobody is at N-3 on this mandate, so the checker does not draw it or offer it.
    expect(fit.byLevel.map((l) => l.level)).toEqual(["Board", "C-Suite", "N-1", "N-2"]);
    expect(levelFilterOptions(diversity, stats)).toEqual([ALL_LEVELS_FILTER, "Board", "C-Suite", "N-1", "N-2"]);
  });

  it("has no levels to list when nobody with a nationality carries one", () => {
    const unplaced = { ...diversity, nationalities: diversity.nationalities.map((row) => ({ ...row, byLevel: [] })) };
    const fit = feasibility(unplaced, diversityStats(unplaced), {
      nationality: ALL_NATIONALITIES_FILTER,
      level: ALL_LEVELS_FILTER,
    });

    expect(fit.byLevel).toEqual([]);
    expect(fit.scope).toBe(0);
  });
});

/** Gender, counted only where it was recorded — the shares must never divide by the headcount. */
describe("genderStats", () => {
  const stats = genderStats(SAMPLE_REPORT.diversity);

  it("divides every share by the executives actually recorded, not by the headcount", () => {
    // 114 of the 116 mapped executives have a gender on file; 37 of those 114 are women.
    expect(stats.recorded).toBe(114);
    expect(stats.unrecorded).toBe(2);
    expect(stats.female).toBe(37);
    expect(stats.femalePct).toBe(32);

    const cSuite = stats.levels.find((level) => level.level === "C-Suite");
    // 50 recorded at C-Suite, not the level's 51: the one nobody recorded is not counted as a man.
    expect(cSuite).toMatchObject({ female: 14, male: 36, recorded: 50, femalePct: 28 });
  });

  it("names the thinnest level by share, ignoring levels nobody has recorded", () => {
    expect(stats.thinnest?.level).toBe("Board");
    expect(stats.thinnest?.femalePct).toBe(20);
    // N-3 has nobody at all, so it is not the thinnest at 0%.
    expect(stats.levels.find((level) => level.level === "N-3")?.recorded).toBe(0);
  });

  it("scales the pyramid to the widest wing so bars carry headcount, not share", () => {
    expect(stats.widest).toBe(36);
  });

  it("counts a third gender without putting it in either wing", () => {
    const n1 = stats.levels.find((level) => level.level === "N-1");

    expect(n1).toMatchObject({ female: 13, male: 24, other: 1, recorded: 38 });
    expect(stats.other).toBe(1);
    expect(stats.female + stats.male + stats.other).toBe(stats.recorded);
  });

  it("counts a gender recorded on an executive with no level in the overall share, on no bar", () => {
    const withUnplaced = genderStats({
      ...SAMPLE_REPORT.diversity,
      genderWithoutLevel: { female: 3, male: 1, other: 0 },
    });

    expect(withUnplaced.recorded).toBe(stats.recorded + 4);
    expect(withUnplaced.female).toBe(stats.female + 3);
    expect(withUnplaced.recordedWithoutLevel).toBe(4);
    expect(withUnplaced.levels).toEqual(stats.levels);
    expect(withUnplaced.thinnest).toEqual(stats.thinnest);
  });

  it("reports a mandate nobody has recorded as unmeasured rather than as all-male", () => {
    const untouched = genderStats({
      ...SAMPLE_REPORT.diversity,
      genderByLevel: SAMPLE_REPORT.diversity.genderByLevel.map((row) => ({
        ...row,
        female: 0,
        male: 0,
        other: 0,
      })),
      genderUnrecorded: 116,
    });

    expect(untouched.recorded).toBe(0);
    expect(untouched.thinnest).toBeNull();
    expect(untouched.femalePct).toBe(0);
  });
});
