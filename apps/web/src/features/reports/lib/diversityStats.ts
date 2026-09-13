import type { NationalityRow, ReportDiversity, SeniorityLevel } from "../api/types";
import { percent } from "./figures";

export const ALL_NATIONALITIES_FILTER = "All nationalities";
export const GCC_NATIONALS_FILTER = "GCC nationals";
export const ALL_LEVELS_FILTER = "All levels";

/** The server folds the tail into this row; it is a bucket, not a nationality a client can require. */
const OTHER_ROW = "Other";

export interface DiversityStats {
  /** Executives with a seniority on file, per level, across every nationality row. */
  levelTotals: Record<SeniorityLevel, number>;
  /** Everyone with a nationality on file. */
  total: number;
  /** Null while nobody has a nationality on file. */
  largest: NationalityRow | null;
  largestPct: number;
  gccPct: number;
  /** Named groups — the "Other" bucket is not one. */
  nationalityCount: number;
}

export function diversityStats(diversity: ReportDiversity): DiversityStats {
  const levelTotals = Object.fromEntries(
    diversity.levels.map((level) => [
      level,
      diversity.nationalities.reduce((sum, row) => sum + (row.byLevel.find((l) => l.level === level)?.count ?? 0), 0),
    ]),
  ) as Record<SeniorityLevel, number>;
  const total = diversity.nationalities.reduce((sum, row) => sum + row.total, 0);
  const named = diversity.nationalities.filter((row) => row.nationality !== OTHER_ROW);
  const largest = named.reduce<NationalityRow | null>((best, row) => (row.total > (best?.total ?? 0) ? row : best), null);
  return {
    levelTotals,
    total,
    largest,
    largestPct: largest ? percent(largest.total, total) : 0,
    gccPct: percent(diversity.gccNationals, total),
    nationalityCount: named.length,
  };
}

export interface FeasibilityFilter {
  nationality: string;
  level: string;
}

export interface Feasibility {
  qualifying: number;
  /** Everyone the filter's levels hold, whatever their nationality. */
  scope: number;
  byLevel: { level: SeniorityLevel; count: number; isInScope: boolean }[];
}

/** How many mapped executives a client's nationality requirement can actually draw from. */
export function feasibility(diversity: ReportDiversity, stats: DiversityStats, filter: FeasibilityFilter): Feasibility {
  const rows = diversity.nationalities.filter((row) =>
    filter.nationality === ALL_NATIONALITIES_FILTER
      ? true
      : filter.nationality === GCC_NATIONALS_FILTER
        ? row.gcc
        : row.nationality === filter.nationality,
  );
  const isInScope = (level: SeniorityLevel) => filter.level === ALL_LEVELS_FILTER || filter.level === level;
  const byLevel = diversity.levels.map((level) => ({
    level,
    count: rows.reduce((sum, row) => sum + (row.byLevel.find((l) => l.level === level)?.count ?? 0), 0),
    isInScope: isInScope(level),
  }));
  return {
    qualifying: byLevel.filter((l) => l.isInScope).reduce((sum, l) => sum + l.count, 0),
    scope: diversity.levels.filter(isInScope).reduce((sum, level) => sum + stats.levelTotals[level], 0),
    byLevel,
  };
}

export function nationalityFilterOptions(diversity: ReportDiversity): string[] {
  return [
    ALL_NATIONALITIES_FILTER,
    GCC_NATIONALS_FILTER,
    ...diversity.nationalities.filter((row) => row.nationality !== OTHER_ROW).map((row) => row.nationality),
  ];
}

export function levelFilterOptions(diversity: ReportDiversity): string[] {
  return [ALL_LEVELS_FILTER, ...diversity.levels];
}
