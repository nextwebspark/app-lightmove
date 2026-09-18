import type { ReportProgress } from "../api/types";
import { addDays, daysBetween } from "./figures";

export type ProjectionBasis = "recent" | "full";

/**
 * What the rows support saying. A pace of zero and a mandate with no history are different facts,
 * and so are a covered universe and an empty one — collapsing either pair is how the chapter came
 * to tell a one-day-old mandate that its coverage was complete.
 */
export type ProjectionStatus = "no-universe" | "complete" | "insufficient" | "stalled" | "projected";

interface ProjectionBase {
  basis: ProjectionBasis;
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
  targetWeek: number | null;
  /** Week index of the latest data point. */
  lastWeek: number;
  /** Companies covered by the end of each week, never empty: the series every reader draws from. */
  coverage: number[];
  /** Companies covered so far. */
  covered: number;
}

/**
 * Discriminated on status, so a reader that has checked for "projected" is handed a pace and a date
 * rather than having to assert them back. The union is the invariant `statusOf` establishes; a cast
 * at a call site would let that invariant move without anything failing to compile.
 */
export type Projection = ProjectionBase & { daysLate: number | null } &
  (
    | (Extract<Reading, { status: "projected" }> & { projectedWeek: number; projectedDate: string })
    | (Exclude<Reading, { status: "projected" }> & { projectedWeek: null; projectedDate: null })
  );

/** What the rows said, with the pace the status was decided from still attached to it. */
type Reading =
  | { status: "projected"; pace: number }
  | {
      status: Exclude<ProjectionStatus, "projected">;
      /** Companies newly covered per week on the chosen basis; null before a week has completed. */
      pace: number | null;
    };

const DAYS_PER_WEEK = 7;
/** The trailing window the "recent" basis and the momentum comparison both read. */
export const RECENT_WEEKS = 3;
/** The first four complete weeks, where a chapter says "the first month". */
export const FIRST_MONTH_WEEKS = 4;

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
  const completeWeeks = completeWeeksOf(progress, cum.length);
  const paceWeeks = basis === "recent" ? Math.min(RECENT_WEEKS, completeWeeks) : completeWeeks;
  // The increments over those weeks telescope: coverage at the last one, less coverage going in.
  const pace = paceWeeks === 0 ? null : (cum[completeWeeks - 1] - coverageBefore(cum, completeWeeks - paceWeeks)) / paceWeeks;

  const targetWeek =
    progress.targetDate === null ? null : daysBetween(progress.kickoff, progress.targetDate) / DAYS_PER_WEEK;
  const remaining = Math.max(progress.targetCompanies - covered, 0);
  const reading = read(progress.targetCompanies, remaining, pace);
  const base: ProjectionBase = {
    basis,
    paceWeeks,
    completeWeeks,
    elapsedDays,
    nowWeek,
    // A target date at or before kickoff names no span to have paced against.
    targetPace: targetWeek === null || targetWeek <= 0 ? null : progress.targetCompanies / targetWeek,
    remaining,
    targetWeek,
    lastWeek,
    coverage: cum,
    covered,
  };

  if (reading.status !== "projected") return { ...base, ...reading, projectedWeek: null, projectedDate: null, daysLate: null };

  const projectedWeek = nowWeek + remaining / reading.pace;
  return {
    ...base,
    ...reading,
    projectedWeek,
    projectedDate: addDays(progress.kickoff, Math.round(projectedWeek * DAYS_PER_WEEK)),
    daysLate: targetWeek === null ? null : Math.round((projectedWeek - targetWeek) * DAYS_PER_WEEK),
  };
}

/**
 * The status and the pace together, so "projected" carries its own non-null pace out of the one
 * place that establishes it. Returning the status alone would leave every reader to assert the pace
 * back, and a cast survives a change to the rules below in silence.
 *
 * <p>An empty universe is checked before completion: nothing scoped leaves nothing remaining, and
 * the chapter used to read that as every company covered. Completion is checked before history,
 * because a two-company mandate covered on day one really is covered — it just has no date to name.
 */
function read(targetCompanies: number, remaining: number, pace: number | null): Reading {
  if (targetCompanies === 0) return { status: "no-universe", pace };
  if (remaining === 0) return { status: "complete", pace };
  if (pace === null) return { status: "insufficient", pace };
  return pace > 0 ? { status: "projected", pace } : { status: "stalled", pace };
}

/**
 * Complete 7-day blocks since kickoff — a fact about elapsed time, not about how long the server's
 * array happens to be, which is why both series can be measured by it. Clamped to what the caller
 * actually holds, so a short series is read as less history rather than indexed past its end.
 */
export function completeWeeksOf(progress: ReportProgress, buckets: number): number {
  const elapsedDays = Math.max(daysBetween(progress.kickoff, progress.asOf), 0);
  return Math.min(Math.floor((elapsedDays + 1) / DAYS_PER_WEEK), buckets);
}

/** Coverage going into `week`; week 0 starts from nothing, so its own increment is counted too. */
function coverageBefore(cum: number[], week: number): number {
  return week <= 0 ? 0 : cum[week - 1];
}

export interface WeeklyPace {
  /** Null where no week has completed — a rate nobody can state, not a rate of zero. */
  average: number | null;
  firstMonth: number | null;
  recent: number | null;
  total: number;
  /** Complete weeks behind these averages, so a reader can say how far back they reach. */
  completeWeeks: number;
}

/**
 * Executives identified per week: the whole run, the first four weeks, and the last three.
 *
 * <p>`total` counts every week including the one in progress — it is a tally, not a rate. The three
 * averages read complete weeks only, for the reason `projectCoverage` does, and answer null rather
 * than zero where there is no complete week to read: those are different facts.
 */
export function weeklyPace(progress: ReportProgress): WeeklyPace {
  const counts = progress.weekly.map((w) => w.identified);
  const completeWeeks = completeWeeksOf(progress, counts.length);
  const whole = counts.slice(0, completeWeeks);
  const mean = (xs: number[]) => (xs.length === 0 ? null : xs.reduce((a, b) => a + b, 0) / xs.length);
  return {
    average: mean(whole),
    firstMonth: mean(whole.slice(0, FIRST_MONTH_WEEKS)),
    recent: mean(whole.slice(-RECENT_WEEKS)),
    total: counts.reduce((a, b) => a + b, 0),
    completeWeeks,
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
