import type { GenderLevelRow, NationalityRow, ReportDiversity, SeniorityLevel } from "../api/types";
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

/**
 * How many mapped executives a client's nationality requirement can actually draw from.
 *
 * <p>Only the levels somebody is mapped at are listed. A level with nobody on it at all says
 * nothing — five rows of "none mapped" read as a fault rather than as an answer. A level that holds
 * people but none of the required nationality stays, because that zero <i>is</i> the answer.
 */
export function feasibility(diversity: ReportDiversity, stats: DiversityStats, filter: FeasibilityFilter): Feasibility {
  const rows = diversity.nationalities.filter((row) =>
    filter.nationality === ALL_NATIONALITIES_FILTER
      ? true
      : filter.nationality === GCC_NATIONALS_FILTER
        ? row.gcc
        : row.nationality === filter.nationality,
  );
  const isInScope = (level: SeniorityLevel) => filter.level === ALL_LEVELS_FILTER || filter.level === level;
  const byLevel = mappedLevels(diversity, stats).map((level) => ({
    level,
    count: rows.reduce((sum, row) => sum + (row.byLevel.find((l) => l.level === level)?.count ?? 0), 0),
    isInScope: isInScope(level),
  }));
  return {
    qualifying: byLevel.filter((l) => l.isInScope).reduce((sum, l) => sum + l.count, 0),
    scope: mappedLevels(diversity, stats).filter(isInScope).reduce((sum, level) => sum + stats.levelTotals[level], 0),
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

/** The levels this mandate has somebody at — the checker's rows, and the levels it offers to filter by. */
function mappedLevels(diversity: ReportDiversity, stats: DiversityStats): SeniorityLevel[] {
  return diversity.levels.filter((level) => stats.levelTotals[level] > 0);
}

/** Offering a level nobody is mapped at would only ever answer zero. */
export function levelFilterOptions(diversity: ReportDiversity, stats: DiversityStats): string[] {
  return [ALL_LEVELS_FILTER, ...mappedLevels(diversity, stats)];
}

export interface GenderLevel {
  level: SeniorityLevel;
  female: number;
  male: number;
  other: number;
  /** Everyone at this level with a gender on file — the denominator `femalePct` uses. */
  recorded: number;
  femalePct: number;
}

export interface GenderStats {
  levels: GenderLevel[];
  female: number;
  male: number;
  other: number;
  /** Everyone with a gender on file, with or without a level. Zero means the chapter has nothing to report. */
  recorded: number;
  /** Those of `recorded` with no seniority on file: in the overall share, on no bar of the pyramid. */
  recordedWithoutLevel: number;
  unrecorded: number;
  femalePct: number;
  /** Null until at least one level has somebody recorded — there is no thinnest level of nothing. */
  thinnest: GenderLevel | null;
  /** The tallest bar the pyramid must draw, so both wings scale to headcount rather than to share. */
  widest: number;
}

/**
 * The gender split of the pool, over the rows that carry one.
 *
 * <p>Every share here divides by `recorded`, never by the level's headcount: a level where two people
 * of nine have a gender on file is 50% female of the two, and calling that 11% would report the
 * silence as men. `unrecorded` is what the screen shows beside it so the reader can weigh the figure.
 */
export function genderStats(diversity: ReportDiversity): GenderStats {
  const levels = diversity.genderByLevel.map(toGenderLevel);
  const unplaced = diversity.genderWithoutLevel;
  const female = sum(levels, (l) => l.female) + unplaced.female;
  const male = sum(levels, (l) => l.male) + unplaced.male;
  const other = sum(levels, (l) => l.other) + unplaced.other;
  const recorded = female + male + other;
  const measured = levels.filter((level) => level.recorded > 0);
  return {
    levels,
    female,
    male,
    other,
    recorded,
    recordedWithoutLevel: unplaced.female + unplaced.male + unplaced.other,
    unrecorded: diversity.genderUnrecorded,
    femalePct: percent(female, recorded),
    thinnest: measured.reduce<GenderLevel | null>(
      (thinnest, level) => (thinnest === null || level.femalePct < thinnest.femalePct ? level : thinnest),
      null,
    ),
    widest: Math.max(...levels.flatMap((level) => [level.female, level.male]), 1),
  };
}

function toGenderLevel(row: GenderLevelRow): GenderLevel {
  const recorded = row.female + row.male + row.other;
  return {
    level: row.level,
    female: row.female,
    male: row.male,
    other: row.other,
    recorded,
    femalePct: percent(row.female, recorded),
  };
}

function sum<T>(rows: T[], of: (row: T) => number): number {
  return rows.reduce((total, row) => total + of(row), 0);
}
