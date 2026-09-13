import { SENIORITY_LEVELS, type ReportDiversity, type SeniorityLevel } from "../api/types";
import { percent } from "./figures";

export const ALL_NATIONALITIES_FILTER = "All nationalities";
export const GCC_NATIONALS_FILTER = "GCC nationals";
export const ALL_LEVELS_FILTER = "All levels";

export interface DiversityStats {
  levelTotals: Record<SeniorityLevel, number>;
  total: number;
  nationalityTotals: { nationality: string; count: number; isGcc: boolean }[];
  nationalityCount: number;
  largestNationality: string;
  largestPct: number;
  gccTotal: number;
  femaleTotal: number;
  femalePctByLevel: Record<SeniorityLevel, number>;
  /** The level where women are scarcest — the finding the chapter leads with. */
  thinnestLevel: SeniorityLevel;
  thinnestPct: number;
}

export function diversityStats(diversity: ReportDiversity): DiversityStats {
  const levelTotals = levelRecord((level) =>
    diversity.nationalities.reduce((sum, row) => sum + row.counts[level], 0),
  );
  const total = SENIORITY_LEVELS.reduce((sum, level) => sum + levelTotals[level], 0);
  const nationalityTotals = diversity.nationalities.map((row) => ({
    nationality: row.nationality,
    count: SENIORITY_LEVELS.reduce((sum, level) => sum + row.counts[level], 0),
    isGcc: diversity.gccNationalities.includes(row.nationality),
  }));
  const largest = nationalityTotals.reduce((a, b) => (b.count > a.count ? b : a));
  const femalePctByLevel = levelRecord((level) => percent(diversity.femaleByLevel[level], levelTotals[level]));
  const thinnestLevel = SENIORITY_LEVELS.reduce((a, b) => (femalePctByLevel[b] < femalePctByLevel[a] ? b : a));
  return {
    levelTotals,
    total,
    nationalityTotals,
    nationalityCount: nationalityTotals.filter((n) => n.count > 0).length,
    largestNationality: largest.nationality,
    largestPct: percent(largest.count, total),
    gccTotal: nationalityTotals.filter((n) => n.isGcc).reduce((sum, n) => sum + n.count, 0),
    femaleTotal: SENIORITY_LEVELS.reduce((sum, level) => sum + diversity.femaleByLevel[level], 0),
    femalePctByLevel,
    thinnestLevel,
    thinnestPct: femalePctByLevel[thinnestLevel],
  };
}

export interface FeasibilityFilter {
  nationality: string;
  level: string;
}

export interface Feasibility {
  qualifying: number;
  scope: number;
  byLevel: { level: SeniorityLevel; count: number; isInScope: boolean }[];
}

/** How many mapped executives a client's nationality requirement can actually draw from. */
export function feasibility(
  diversity: ReportDiversity,
  stats: DiversityStats,
  filter: FeasibilityFilter,
): Feasibility {
  const rows = diversity.nationalities.filter((row) =>
    filter.nationality === ALL_NATIONALITIES_FILTER
      ? true
      : filter.nationality === GCC_NATIONALS_FILTER
        ? diversity.gccNationalities.includes(row.nationality)
        : row.nationality === filter.nationality,
  );
  const byLevel = SENIORITY_LEVELS.map((level) => ({
    level,
    count: rows.reduce((sum, row) => sum + row.counts[level], 0),
    isInScope: filter.level === ALL_LEVELS_FILTER || filter.level === level,
  }));
  return {
    qualifying: byLevel.filter((l) => l.isInScope).reduce((sum, l) => sum + l.count, 0),
    scope: SENIORITY_LEVELS.filter((level) => filter.level === ALL_LEVELS_FILTER || filter.level === level).reduce(
      (sum, level) => sum + stats.levelTotals[level],
      0,
    ),
    byLevel,
  };
}

export function nationalityFilterOptions(diversity: ReportDiversity): string[] {
  return [ALL_NATIONALITIES_FILTER, GCC_NATIONALS_FILTER, ...diversity.nationalities.map((row) => row.nationality)];
}

export function levelFilterOptions(): string[] {
  return [ALL_LEVELS_FILTER, ...SENIORITY_LEVELS];
}

function levelRecord(value: (level: SeniorityLevel) => number): Record<SeniorityLevel, number> {
  return Object.fromEntries(SENIORITY_LEVELS.map((level) => [level, value(level)])) as Record<SeniorityLevel, number>;
}
