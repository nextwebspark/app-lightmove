import type { Weekday } from "../../outreach/api/sequenceApi";
import { clockOf, daysLabelOf, WEEKDAYS } from "../../outreach/lib/sendSchedule";
import type { WorkingDay } from "../api/availabilityApi";

/** A working week as the Settings pages edit it: every day in week order, its hours or none. */

export type WeekDraft = Record<Weekday, { start: string; end: string } | null>;

export const DEFAULT_DAY_HOURS = { start: "08:00", end: "18:00" };

export function draftOf(days: readonly WorkingDay[]): WeekDraft {
  const draft = Object.fromEntries(WEEKDAYS.map(({ day }) => [day, null])) as WeekDraft;
  days.forEach((day) => {
    draft[day.day] = { start: clockOf(day.start), end: clockOf(day.end) };
  });
  return draft;
}

export function workingDaysOf(draft: WeekDraft): WorkingDay[] {
  return WEEKDAYS.flatMap(({ day }) => {
    const hours = draft[day];
    return hours ? [{ day, start: hours.start, end: hours.end }] : [];
  });
}

/** The same hours on each of `days` and none on the rest; the hours of the first open day where any is open. */
export function presetOf(draft: WeekDraft, days: readonly Weekday[]): WeekDraft {
  const hours = WEEKDAYS.map(({ day }) => draft[day]).find((held) => held !== null) ?? DEFAULT_DAY_HOURS;
  return Object.fromEntries(WEEKDAYS.map(({ day }) => [day, days.includes(day) ? { ...hours } : null])) as WeekDraft;
}

/** Why the week cannot be saved, or null: what the server refuses, said before the round trip. */
export function weekProblemOf(draft: WeekDraft): string | null {
  const open = WEEKDAYS.map(({ day }) => draft[day]).filter((hours) => hours !== null);
  if (open.length === 0) return "Choose at least one working day.";
  if (open.some((hours) => hours.start >= hours.end)) return "A working day has to end after it starts.";
  return null;
}

/** "Sun–Thu, 08:00–18:00", or each run of days with its own hours: "Sun–Wed 09:00–18:00 · Thu 09:00–14:00". */
export function weekLabelOf(days: readonly WorkingDay[]): string {
  if (days.length === 0) return "No working days";
  const runs = new Map<string, Weekday[]>();
  days.forEach((day) => {
    const key = `${clockOf(day.start)}–${clockOf(day.end)}`;
    runs.set(key, [...(runs.get(key) ?? []), day.day]);
  });
  if (runs.size === 1) {
    const [[hours, runDays]] = runs;
    return `${daysLabelOf(runDays)}, ${hours}`;
  }
  return [...runs].map(([hours, runDays]) => `${daysLabelOf(runDays)} ${hours}`).join(" · ");
}
