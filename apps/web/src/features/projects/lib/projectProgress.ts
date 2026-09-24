import type { Project } from "../api/types";

export interface ProjectProgress {
  /** Universe companies with someone mapped, out of the universe; 0 when there is no universe yet. */
  coveragePercent: number;
  /** Whole days to the target date — negative once it has passed, null with no target. */
  daysRemaining: number | null;
  /** Executives mapped per week since the mandate was opened, one decimal. */
  weeklyVelocity: string;
}

const DAY_MS = 86_400_000;

/**
 * The side panel's progress figures, from the counts the list already carries. Coverage is mapping
 * progress — how much of the universe has anyone mapped at it — never elapsed time, which the date
 * line under the bar states on its own.
 */
export function projectProgress(project: Project, today: Date = new Date()): ProjectProgress {
  const universe = project.companies;
  const coveragePercent = universe > 0 ? Math.min(100, Math.round((project.mappedCompanies / universe) * 100)) : 0;

  const midnight = startOfDay(today);
  const daysRemaining = project.targetDate
    ? Math.round((startOfDay(new Date(`${project.targetDate}T00:00:00`)).getTime() - midnight.getTime()) / DAY_MS)
    : null;

  const weeksOpen = Math.max(1, (midnight.getTime() - startOfDay(new Date(project.createdAt)).getTime()) / (7 * DAY_MS));
  const weeklyVelocity = (project.candidates / weeksOpen).toFixed(1);

  return { coveragePercent, daysRemaining, weeklyVelocity };
}

function startOfDay(date: Date): Date {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
}
