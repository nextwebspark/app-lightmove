/**
 * The New position modal's date arithmetic, over ISO yyyy-MM-dd strings as the API speaks them.
 * Days are counted in UTC so a daylight-saving change inside the window cannot shift a date.
 */

import type { Project } from "../api/types";

/** Share of the start → delivery window a search's universe should be mapped by. `ProjectTimeline` agrees. */
export const MAPPING_SHARE = 0.6;

const DAY_MS = 86_400_000;

function toUtcMs(isoDate: string): number {
  const [year, month, day] = isoDate.split("-").map(Number);
  return Date.UTC(year, month - 1, day);
}

function fromUtcMs(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10);
}

/** Today on the user's own calendar — not UTC's, which is already tomorrow east of Greenwich by evening. */
export function todayIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

export function daysBetween(fromIso: string, toIso: string): number {
  return Math.round((toUtcMs(toIso) - toUtcMs(fromIso)) / DAY_MS);
}

export function addDays(isoDate: string, days: number): string {
  return fromUtcMs(toUtcMs(isoDate) + days * DAY_MS);
}

/** Mirrors `ProjectTimeline`: after the start, and no later than delivery. */
export function mappingTargetFits(startIso: string, deliveryIso: string, targetIso: string): boolean {
  return daysBetween(startIso, targetIso) > 0 && daysBetween(targetIso, deliveryIso) >= 0;
}

/** The default mapping target, or null while the window is open or runs backwards. */
export function autoMappingTarget(startIso: string, deliveryIso: string): string | null {
  if (!startIso || !deliveryIso) return null;
  const window = daysBetween(startIso, deliveryIso);
  if (window <= 0) return null;
  return fromUtcMs(toUtcMs(startIso) + Math.round(window * MAPPING_SHARE) * DAY_MS);
}

/** When the business unit expects the work back, as the server's `Project.deadline()` reads it. */
export function deadlineOf(project: Pick<Project, "deliveryDate" | "targetDate">): string | null {
  return project.deliveryDate ?? project.targetDate;
}
