import type { ReportProgress } from "../api/types";
import { addDays, daysBetween } from "./figures";

export type ProjectionBasis = "recent" | "full";

export const PROJECTION_BASES: readonly ProjectionBasis[] = ["recent", "full"];

export interface Projection {
  basis: ProjectionBasis;
  /** Companies newly covered per week on the chosen basis. */
  pace: number;
  /** The pace the plan needed from day one to land on the target date. */
  targetPace: number;
  remaining: number;
  /** Week index (kickoff = 0) at which full coverage is projected; fractional. */
  projectedWeek: number;
  targetWeek: number;
  projectedDate: string;
  daysLate: number;
  /** Week index of the latest data point. */
  lastWeek: number;
}

/**
 * When the remaining companies clear at the current pace. Two bases, because the honest one depends
 * on what happened: a full-mandate average blends the fast early weeks into a recent slowdown and
 * quietly hides it, which is exactly the failure this chapter exists to catch — so "recent" is the
 * default and "full" is offered as the comparison.
 */
export function projectCoverage(progress: ReportProgress, basis: ProjectionBasis): Projection {
  const cum = progress.companiesCumulative;
  const lastWeek = cum.length - 1;
  const pace =
    basis === "recent"
      ? recentPace(cum)
      : cum[lastWeek] / lastWeek;
  const targetWeek = daysBetween(progress.kickoff, progress.targetDate) / 7;
  const remaining = progress.targetCompanies - cum[lastWeek];
  const projectedWeek = pace > 0 ? lastWeek + remaining / pace : Number.POSITIVE_INFINITY;
  const daysToProjected = Math.round(projectedWeek * 7);
  return {
    basis,
    pace,
    targetPace: progress.targetCompanies / targetWeek,
    remaining,
    projectedWeek,
    targetWeek,
    projectedDate: Number.isFinite(projectedWeek) ? addDays(progress.kickoff, daysToProjected) : progress.targetDate,
    daysLate: Number.isFinite(projectedWeek) ? Math.round((projectedWeek - targetWeek) * 7) : 0,
    lastWeek,
  };
}

function recentPace(cum: number[]): number {
  const last = cum.length - 1;
  const window = Math.min(3, last);
  let total = 0;
  for (let i = last - window + 1; i <= last; i += 1) total += cum[i] - cum[i - 1];
  return total / window;
}

export interface WeeklyPace {
  average: number;
  firstMonth: number;
  recent: number;
  total: number;
}

/** Executives identified per week: the whole run, the first four weeks, and the last three. */
export function weeklyPace(progress: ReportProgress): WeeklyPace {
  const counts = progress.weekly.map((w) => w.identified);
  const mean = (xs: number[]) => (xs.length === 0 ? 0 : xs.reduce((a, b) => a + b, 0) / xs.length);
  return {
    average: mean(counts),
    firstMonth: mean(counts.slice(0, 4)),
    recent: mean(counts.slice(-3)),
    total: counts.reduce((a, b) => a + b, 0),
  };
}

/**
 * Expanding-then-trailing rolling mean: whatever history exists for the first `window - 1` points,
 * a true trailing window from there on. Daily counts carry a weekend hole every week, so the raw
 * bars mislead and the mean is the series that shows the trend.
 */
export function rollingAverage(series: number[], window: number): number[] {
  return series.map((_, i) => {
    const from = Math.max(0, i - window + 1);
    const slice = series.slice(from, i + 1);
    return slice.reduce((a, b) => a + b, 0) / slice.length;
  });
}
