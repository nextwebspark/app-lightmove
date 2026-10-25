import type { TimeSpan } from "../api/meetingApi";
import { instantOf, wallClockOf } from "./sendSchedule";
import { shiftDateOf } from "./meetingTimes";

/**
 * Book a call's week as the grid draws it: days in the consultant's own zone, every stretch placed by its minutes
 * from that day's midnight. Pure, so the grid's arithmetic is tested without a pointer.
 */

export const MINUTES_IN_DAY = 24 * 60;

/** The finest a call's start or end lands on, as the server books it. */
export const SNAP_MINUTES = 5;

/** Where a click alone lands: on the quarter hour, the way a diary is read. */
export const CLICK_SNAP_MINUTES = 15;

export interface CallChoice {
  /** ISO instant. */
  start: string;
  minutes: number;
}

/** A stretch of one day, in minutes from its midnight. */
export interface DayStretch {
  top: number;
  bottom: number;
}

export function viewDaysOf(from: string, days: number): string[] {
  return Array.from({ length: days }, (_, index) => shiftDateOf(from, index));
}

export function dayStartOf(date: string, timeZone: string): Date {
  return instantOf(date, "00:00", timeZone);
}

/**
 * The instant the clock shows `minutes` past midnight on `date`. Read off the wall clock, never as minutes elapsed
 * since midnight: on a day the clocks change those differ by an hour after the change, and a drag at the 14:00 row
 * would book 13:00 or 15:00.
 */
export function instantAt(date: string, minutes: number, timeZone: string): Date {
  if (minutes >= MINUTES_IN_DAY) return dayStartOf(shiftDateOf(date, 1), timeZone);
  const clock = `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
  return instantOf(date, clock, timeZone);
}

/** Where each span falls on `date`'s wall clock, clipped to the day; a span on no part of it is left out. */
export function stretchesOn(spans: readonly TimeSpan[], date: string, timeZone: string): DayStretch[] {
  const opens = dayStartOf(date, timeZone).getTime();
  const closes = dayStartOf(shiftDateOf(date, 1), timeZone).getTime();
  const clockMinutesOf = (instant: number) => {
    const [hour, minute] = wallClockOf(new Date(instant), timeZone).time.split(":").map(Number);
    return hour * 60 + minute;
  };
  return spans
    .map((span) => ({ start: Date.parse(span.start), end: Date.parse(span.end) }))
    .filter((span) => span.start < closes && span.end > opens)
    .map((span) => ({
      top: span.start <= opens ? 0 : clockMinutesOf(span.start),
      bottom: span.end >= closes ? MINUTES_IN_DAY : clockMinutesOf(span.end),
    }));
}

export function snapTo(minutes: number, step: number): number {
  return Math.round(minutes / step) * step;
}

/**
 * The call a drag from `anchor` to `current` minutes picks, snapped to five minutes, at least `minMinutes` long and
 * at most `maxMinutes`; a press that barely moved is a click, which takes `clickMinutes` from the quarter hour it fell in.
 */
export function choiceOfDrag(
  date: string,
  anchor: number,
  current: number,
  timeZone: string,
  { minMinutes, maxMinutes, clickMinutes }: { minMinutes: number; maxMinutes: number; clickMinutes: number },
): CallChoice {
  const isClick = Math.abs(current - anchor) < SNAP_MINUTES;
  if (isClick) {
    const start = Math.min(Math.floor(anchor / CLICK_SNAP_MINUTES) * CLICK_SNAP_MINUTES, MINUTES_IN_DAY - clickMinutes);
    return { start: instantAt(date, Math.max(0, start), timeZone).toISOString(), minutes: clickMinutes };
  }
  const from = snapTo(Math.min(anchor, current), SNAP_MINUTES);
  const to = snapTo(Math.max(anchor, current), SNAP_MINUTES);
  const start = instantAt(date, from, timeZone);
  const elapsed = Math.round((instantAt(date, to, timeZone).getTime() - start.getTime()) / 60_000);
  const minutes = Math.min(maxMinutes, Math.max(minMinutes, elapsed));
  return { start: start.toISOString(), minutes };
}

export function endOf(choice: CallChoice): string {
  return new Date(Date.parse(choice.start) + choice.minutes * 60_000).toISOString();
}

export function overlapsAny(choice: CallChoice, spans: readonly TimeSpan[]): boolean {
  const start = Date.parse(choice.start);
  const end = start + choice.minutes * 60_000;
  return spans.some((span) => Date.parse(span.start) < end && Date.parse(span.end) > start);
}

/** Whether the call sits wholly inside one stretch of working hours. */
export function isWithinWorkingHours(choice: CallChoice, workingHours: readonly TimeSpan[]): boolean {
  const start = Date.parse(choice.start);
  const end = start + choice.minutes * 60_000;
  return workingHours.some((span) => Date.parse(span.start) <= start && Date.parse(span.end) >= end);
}

/** The call's day and its start and end on the consultant's clock: what the "Pick a time" fields show. */
export function fieldsOf(choice: CallChoice, timeZone: string): { date: string; from: string; to: string } {
  const start = wallClockOf(new Date(choice.start), timeZone);
  const end = wallClockOf(new Date(endOf(choice)), timeZone);
  return { date: start.date, from: start.time, to: end.date === start.date ? end.time : "24:00" };
}

/** A call from the "Pick a time" fields, or null where they do not make one. A call ending at midnight may say 00:00. */
export function choiceOfFields(date: string, from: string, to: string, timeZone: string): CallChoice | null {
  if (!date || !from || !to) return null;
  const start = instantOf(date, from, timeZone);
  const endDate = to === "00:00" || to === "24:00" ? shiftDateOf(date, 1) : date;
  const end = instantOf(endDate, to === "24:00" ? "00:00" : to, timeZone);
  const minutes = Math.round((end.getTime() - start.getTime()) / 60_000);
  return minutes > 0 ? { start: start.toISOString(), minutes } : null;
}

/** Why a call cannot be booked, or null: the server decides, this only spares a round trip. */
export function refusalOf(
  choice: CallChoice,
  { now, minMinutes, maxMinutes, latestDate, timeZone, busy }: {
    now: Date;
    minMinutes: number;
    maxMinutes: number;
    latestDate: string;
    timeZone: string;
    busy: readonly TimeSpan[];
  },
): string | null {
  if (Date.parse(choice.start) % (SNAP_MINUTES * 60_000) !== 0 || choice.minutes % SNAP_MINUTES !== 0) {
    return "Pick times on a five-minute mark.";
  }
  if (choice.minutes < minMinutes || choice.minutes > maxMinutes) {
    return `A call runs ${minMinutes} min to ${maxMinutes / 60} hr.`;
  }
  if (Date.parse(choice.start) < now.getTime() + SNAP_MINUTES * 60_000) {
    return "That time has passed. Pick a later one.";
  }
  if (wallClockOf(new Date(choice.start), timeZone).date > latestDate) {
    return "That's further ahead than calls can be booked.";
  }
  if (overlapsAny(choice, busy)) {
    return "Your calendar is busy then.";
  }
  return null;
}
