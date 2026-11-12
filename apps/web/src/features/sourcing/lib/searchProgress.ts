import type { CompanyProgress, SourcingRun } from "../api/sourcingApi";

/** What a company in the running search is doing, in plain words that never name where people are read from. */
export function stepLineOf(step: CompanyProgress | undefined): string {
  switch (step?.stage) {
    case "CHECKING":
      return step.total > 0 ? `Checking titles · ${step.done} of ${step.total}` : "Checking titles…";
    case "CONFIRMING":
      return step.total > 0 ? `Reading confirmed profiles · ${step.done} of ${step.total}` : "Reading confirmed profiles…";
    case "RANKING":
      return "Ranking who fits…";
    default:
      return "Looking for people in the role…";
  }
}

/** The companies the run has started and not finished: every one of them is being searched now, side by side. */
export function searchingIdsOf(run: SourcingRun): ReadonlySet<string> {
  const finished = new Set(run.outcomes.map((outcome) => outcome.triageCompanyId));
  return new Set(
    (run.progress ?? [])
      .filter((step) => !finished.has(step.triageCompanyId))
      .map((step) => step.triageCompanyId),
  );
}

export function stepOf(run: SourcingRun | null, triageCompanyId: string): CompanyProgress | undefined {
  return run?.progress?.find((step) => step.triageCompanyId === triageCompanyId);
}

/** Everyone looked at so far across the companies. */
export function consideredOf(run: SourcingRun): number {
  return (run.progress ?? []).reduce((total, step) => total + step.considered, 0);
}

/**
 * Seconds left, judged from how long the companies finished so far took; null until one has finished, since a
 * guess before then would be made up. The companies running now finish in what is left of that time, and the
 * ones waiting follow in rounds of `parallelism`.
 */
export function secondsLeftOf(run: SourcingRun, parallelism: number, now: number): number | null {
  const took = (run.progress ?? [])
    .map((step) => millisOf(step.finishedAt) - millisOf(step.startedAt))
    .filter((ms) => Number.isFinite(ms) && ms >= 0);
  if (took.length === 0) return null;
  const perCompany = took.reduce((total, ms) => total + ms, 0) / took.length;
  const searching = searchingIdsOf(run);
  const started = new Set((run.progress ?? []).map((step) => step.triageCompanyId));
  const finished = new Set(run.outcomes.map((outcome) => outcome.triageCompanyId));
  const waiting = run.companies.filter(
    (company) => !started.has(company.triageCompanyId) && !finished.has(company.triageCompanyId),
  ).length;
  const runningLeft = Math.max(
    0,
    ...(run.progress ?? [])
      .filter((step) => searching.has(step.triageCompanyId))
      .map((step) => perCompany - (now - millisOf(step.startedAt)))
      .filter(Number.isFinite),
  );
  const rounds = Math.ceil(waiting / Math.max(1, parallelism));
  return Math.round((runningLeft + rounds * perCompany) / 1000);
}

const millisOf = (instant: string | null) => (instant === null ? Number.NaN : Date.parse(instant));

/** "About 40 s left": rounded so the line does not flicker, and never a precise-looking promise. */
export function timeLeftLine(seconds: number | null): string {
  if (seconds === null) return "Usually a minute or two";
  if (seconds < 15) return "Almost done";
  if (seconds < 60) return `About ${Math.round(seconds / 10) * 10} s left`;
  const minutes = Math.round(seconds / 60);
  return `About ${minutes} min left`;
}

/** What the AI is doing while it drafts the search from the brief, one line at a time. */
export const DRAFTING_STEPS = [
  "Reading the brief…",
  "Choosing the levels and title words…",
  "Adding the local words for each title…",
  "Picking words to leave out…",
] as const;
