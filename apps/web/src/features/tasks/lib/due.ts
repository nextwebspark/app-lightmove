import {
  dayKeyOf,
  formatClock,
  getDateTimePreferences,
  instantOf,
  shiftDay,
  wallClockOf,
} from "../../../lib/datetime";

/** A due moment as the modal's two inputs hold it, in the person's own zone: `2026-10-05` and `09:00`. */
export interface LocalDue {
  date: string;
  time: string;
}

export type QuickDueKey = "today" | "tomorrow" | "in3" | "monday";

export const QUICK_DUES: readonly { key: QuickDueKey; readonly label: string }[] = [
  {
    key: "today",
    get label() {
      return `Today ${formatClock("17:00")}`;
    },
  },
  {
    key: "tomorrow",
    get label() {
      return `Tomorrow ${formatClock("09:00")}`;
    },
  },
  { key: "in3", label: "In 3 days" },
  { key: "monday", label: "Next Monday" },
];

const MORNING = "09:00";
const END_OF_DAY = "17:00";

const WEEKDAY_INDEX = { SUNDAY: 0, MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6 };

/** The day an instant falls on in the person's zone. */
export function isoDateOf(moment: Date): string {
  return dayKeyOf(moment);
}

function daysAfter(now: Date, days: number): string {
  return shiftDay(dayKeyOf(now), days);
}

export function localDueOf(isoInstant: string): LocalDue {
  const { date, time } = wallClockOf(new Date(isoInstant));
  return { date, time };
}

/** The instant the two inputs name in the person's zone, or null while either is blank or unreadable. */
export function dueInstantOf(due: LocalDue): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(due.date) || !/^\d{2}:\d{2}$/.test(due.time)) return null;
  const moment = instantOf(due.date, due.time);
  return Number.isNaN(moment.getTime()) ? null : moment.toISOString();
}

/** A preset's due: so many days from today at 09:00, or 17:00 when it falls due the same day. */
export function presetDueOf(offsetDays: number, now: Date = new Date()): LocalDue {
  return { date: daysAfter(now, offsetDays), time: offsetDays === 0 ? END_OF_DAY : MORNING };
}

/** A new task's due until somebody says otherwise: tomorrow morning. */
export function defaultDueOf(now: Date = new Date()): LocalDue {
  return presetDueOf(1, now);
}

export function quickDueOf(key: QuickDueKey, now: Date = new Date()): LocalDue {
  switch (key) {
    case "today":
      return { date: isoDateOf(now), time: END_OF_DAY };
    case "tomorrow":
      return { date: daysAfter(now, 1), time: MORNING };
    case "in3":
      return { date: daysAfter(now, 3), time: MORNING };
    case "monday":
      return { date: daysAfter(now, (8 - WEEKDAY_INDEX[wallClockOf(now).weekday]) % 7 || 7), time: MORNING };
  }
}

/** "Same day", "+1 day", "+5 days"; null for a preset that leaves the due to the person. */
export function presetDueLabel(offsetDays: number | null): string | null {
  if (offsetDays === null) return null;
  if (offsetDays === 0) return "Same day";
  return `+${offsetDays} day${offsetDays === 1 ? "" : "s"}`;
}

/** The person's own zone, which every due here is read and written in. */
export function viewerTimeZone(): string {
  return getDateTimePreferences().timeZone;
}
