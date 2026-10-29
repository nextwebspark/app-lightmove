import { getDateTimePreferences } from "./preferences";

export type Weekday = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export interface WallClock {
  /** `YYYY-MM-DD`. */
  date: string;
  /** `HH:mm`. */
  time: string;
  weekday: Weekday;
}

const WEEKDAY_BY_SHORT: Record<string, Weekday> = {
  Mon: "MONDAY",
  Tue: "TUESDAY",
  Wed: "WEDNESDAY",
  Thu: "THURSDAY",
  Fri: "FRIDAY",
  Sat: "SATURDAY",
  Sun: "SUNDAY",
};

// Building an Intl formatter costs far more than using one, and a grid formats hundreds of cells.
const wallClockFormatters = new Map<string, Intl.DateTimeFormat>();

function wallClockFormatterOf(timeZone: string): Intl.DateTimeFormat {
  let formatter = wallClockFormatters.get(timeZone);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat("en-GB", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      weekday: "short",
      hourCycle: "h23",
    });
    wallClockFormatters.set(timeZone, formatter);
  }
  return formatter;
}

/** The date, time and weekday an instant reads as on a clock in `timeZone` — by default the signed-in person's. */
export function wallClockOf(instant: Date, timeZone: string = getDateTimePreferences().timeZone): WallClock {
  const parts = Object.fromEntries(
    wallClockFormatterOf(timeZone)
      .formatToParts(instant)
      .map((part) => [part.type, part.value]),
  );
  return {
    date: `${parts.year}-${parts.month}-${parts.day}`,
    time: `${parts.hour}:${parts.minute}`,
    weekday: WEEKDAY_BY_SHORT[parts.weekday],
  };
}

/** The instant a wall clock in `timeZone` shows `date` at `time`. */
export function instantOf(date: string, time: string, timeZone: string = getDateTimePreferences().timeZone): Date {
  const [year, month, day] = date.split("-").map(Number);
  const [hour, minute] = time.split(":").map(Number);
  const asIfUtc = Date.UTC(year, month - 1, day, hour, minute);
  let guess = asIfUtc - offsetOf(new Date(asIfUtc), timeZone);
  guess = asIfUtc - offsetOf(new Date(guess), timeZone);
  return new Date(guess);
}

/** How far `timeZone`'s clock runs ahead of UTC at `instant`, in milliseconds. */
export function offsetOf(instant: Date, timeZone: string): number {
  const clock = wallClockOf(instant, timeZone);
  const [year, month, day] = clock.date.split("-").map(Number);
  const [hour, minute] = clock.time.split(":").map(Number);
  const seconds = instant.getUTCSeconds() * 1000 + instant.getUTCMilliseconds();
  return Date.UTC(year, month - 1, day, hour, minute) - (instant.getTime() - seconds);
}

/** The `YYYY-MM-DD` day an instant falls on in the signed-in person's zone (or `timeZone`). */
export function dayKeyOf(instant: Date, timeZone?: string): string {
  return wallClockOf(instant, timeZone).date;
}

/** Whole days from one `YYYY-MM-DD` day to another: negative when `to` is earlier. */
export function daysBetween(from: string, to: string): number {
  return Math.round((utcMidnightOf(to) - utcMidnightOf(from)) / 86_400_000);
}

/** A `YYYY-MM-DD` day moved by whole days. */
export function shiftDay(day: string, days: number): string {
  return new Date(utcMidnightOf(day) + days * 86_400_000).toISOString().slice(0, 10);
}

function utcMidnightOf(day: string): number {
  const [year, month, date] = day.split("-").map(Number);
  return Date.UTC(year, month - 1, date);
}
