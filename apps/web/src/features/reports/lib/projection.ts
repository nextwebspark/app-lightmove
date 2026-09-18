import type { ReportProgress } from "../api/types";
import { addDays, daysBetween } from "./figures";

export type ProjectionBasis = "recent" | "full";

/**
 * What the rows support saying. A pace of zero and a mandate with no history are different facts,
 * and so are a covered universe and an empty one — collapsing either pair is how the chapter came
 * to tell a one-day-old mandate that its coverage was complete.
 */
export type ProjectionStatus = "no-universe" | "complete" | "insufficient" | "stalled" | "projected";

export interface Projection {
  status: ProjectionStatus;
  basis: ProjectionBasis;
  /** Companies newly covered per week on the chosen basis; null where no complete week has passed. */
  pace: number | null;
  /** How many complete weeks the pace averaged over — what the copy may honestly claim it read. */
  paceWeeks: number;
  /** Complete 7-day blocks since kickoff. The current, partial week is not one of them. */
  completeWeeks: number;
  elapsedDays: number;
  /** Today as a fractional week index. Every forward line starts here, not at the week's start. */
  nowWeek: number;
  /** The pace the plan needed from kickoff to land on the target date; null without a target. */
  targetPace: number | null;
  remaining: number;
  /** Week index at which full coverage is projected; null unless the status is "projected". */
  projectedWeek: number | null;
  targetWeek: number | null;
  /** Null unless the status is "projected" — otherwise there is no date to name. */
  projectedDate: string | null;
  /** Positive is late. Null without a target or a projection. */
  daysLate: number | null;
  /** Week index of the latest data point. */
  lastWeek: number;
  /** Companies covered by the end of each week, never empty: the series every reader draws from. */
  coverage: number[];
  /** Companies covered so far. */
  covered: number;
}

const DAYS_PER_WEEK = 7;
const RECENT_WEEKS = 3;

/**
 * When the remaining companies clear at the current pace. Two bases, because the honest one depends
 * on what happened: a full-mandate average blends the fast early weeks into a recent slowdown and
 * quietly hides it, which is exactly the failure this chapter exists to catch — so "recent" is the
 * default and "full" is offered as the comparison.
 *
 * <p>The pace reads complete weeks only. The last bucket is the week in progress, and dividing two
 * days of work by a whole week understates every pace and pushes every projection late.
 */
export function projectCoverage(progress: ReportProgress, basis: ProjectionBasis): Projection {
  // The server answers at least the kickoff week. An empty series is read as that week with nothing
  // covered, here and once, so no figure or chart downstream indexes past the end into NaN.
  const cum = progress.companiesCumulative.length > 0 ? progress.companiesCumulative : [0];
  const lastWeek = cum.length - 1;
  const covered = cum[lastWeek];
  const elapsedDays = Math.max(daysBetween(progress.kickoff, progress.asOf), 0);
  const nowWeek = elapsedDays / DAYS_PER_WEEK;
  // The final bucket is complete only on the last of its seven days; until then it is in progress.
  const completeWeeks = Math.min(
    elapsedDays % DAYS_PER_WEEK === DAYS_PER_WEEK - 1 ? lastWeek + 1 : lastWeek,
    cum.length,
  );
  const paceWeeks = basis === "recent" ? Math.min(RECENT_WEEKS, completeWeeks) : completeWeeks;
  // The increments over those weeks telescope: coverage at the last one, less coverage going in.
  const pace = paceWeeks === 0 ? null : (cum[completeWeeks - 1] - coverageBefore(cum, completeWeeks - paceWeeks)) / paceWeeks;

  const targetWeek =
    progress.targetDate === null ? null : daysBetween(progress.kickoff, progress.targetDate) / DAYS_PER_WEEK;
  const remaining = Math.max(progress.targetCompanies - covered, 0);
  const status = statusOf(progress.targetCompanies, remaining, completeWeeks, pace);
  const projectedWeek = status === "projected" ? nowWeek + remaining / (pace as number) : null;

  return {
    status,
    basis,
    pace,
    paceWeeks,
    completeWeeks,
    elapsedDays,
    nowWeek,
    // A target date at or before kickoff names no span to have paced against.
    targetPace: targetWeek === null || targetWeek <= 0 ? null : progress.targetCompanies / targetWeek,
    remaining,
    projectedWeek,
    targetWeek,
    projectedDate: projectedWeek === null ? null : addDays(progress.kickoff, Math.round(projectedWeek * DAYS_PER_WEEK)),
    daysLate:
      projectedWeek === null || targetWeek === null
        ? null
        : Math.round((projectedWeek - targetWeek) * DAYS_PER_WEEK),
    lastWeek,
    coverage: cum,
    covered,
  };
}

/**
 * An empty universe is checked before completion: nothing scoped leaves nothing remaining, and the
 * chapter used to read that as every company covered. Completion is checked before history, because
 * a two-company mandate covered on day one really is covered — it just has no date to project.
 */
function statusOf(
  targetCompanies: number,
  remaining: number,
  completeWeeks: number,
  pace: number | null,
): ProjectionStatus {
  if (targetCompanies === 0) return "no-universe";
  if (remaining === 0) return "complete";
  if (pace === null || completeWeeks === 0) return "insufficient";
  return pace > 0 ? "projected" : "stalled";
}

/** Coverage going into `week`; week 0 starts from nothing, so its own increment is counted too. */
function coverageBefore(cum: number[], week: number): number {
  return week <= 0 ? 0 : cum[week - 1];
}

export interface WeeklyPace {
  average: number;
  firstMonth: number;
  recent: number;
  total: number;
}

/**
 * Executives identified per week: the whole run, the first four weeks, and the last three.
 *
 * <p>`total` counts every week including the one in progress — it is a tally, not a rate. The three
 * averages read complete weeks only, for the reason `projectCoverage` does.
 */
export function weeklyPace(progress: ReportProgress, completeWeeks: number): WeeklyPace {
  const counts = progress.weekly.map((w) => w.identified);
  const whole = counts.slice(0, completeWeeks);
  const mean = (xs: number[]) => (xs.length === 0 ? 0 : xs.reduce((a, b) => a + b, 0) / xs.length);
  return {
    average: mean(whole),
    firstMonth: mean(whole.slice(0, 4)),
    recent: mean(whole.slice(-RECENT_WEEKS)),
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
