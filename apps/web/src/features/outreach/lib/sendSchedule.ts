import type { SequenceSchedule, SequenceStep, StartMode, Weekday } from "../api/sequenceApi";

/**
 * A sequence's sending schedule as the editor and Start show it: the days and hours in the sender's
 * mailbox zone, and the dates its emails would go. Mirrors the server's `SendingWindow`, which decides.
 */

export const WEEKDAYS: readonly { day: Weekday; label: string }[] = [
  { day: "MONDAY", label: "Mon" },
  { day: "TUESDAY", label: "Tue" },
  { day: "WEDNESDAY", label: "Wed" },
  { day: "THURSDAY", label: "Thu" },
  { day: "FRIDAY", label: "Fri" },
  { day: "SATURDAY", label: "Sat" },
  { day: "SUNDAY", label: "Sun" },
];

export const MONDAY_TO_FRIDAY: Weekday[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];
export const SUNDAY_TO_THURSDAY: Weekday[] = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY"];

export const DEFAULT_SCHEDULE: SequenceSchedule = {
  days: MONDAY_TO_FRIDAY,
  windowStart: "08:00",
  windowEnd: "18:00",
};

/** Every half hour of the day, "00:00" to "23:30". */
export const HALF_HOURS: readonly string[] = Array.from(
  { length: 48 },
  (_, index) => `${String(Math.floor(index / 2)).padStart(2, "0")}:${index % 2 === 0 ? "00" : "30"}`,
);

/** The server writes a time as "08:00:00"; the screens speak "08:00". */
export function clockOf(time: string): string {
  return time.slice(0, 5);
}

/** "Mon–Fri", "Sun–Thu", or the days listed when they do not run on. */
export function daysLabelOf(days: readonly Weekday[]): string {
  const ordered = WEEKDAYS.filter((weekday) => days.includes(weekday.day));
  if (ordered.length === 0) return "No days";
  if (ordered.length === 7) return "Every day";
  const indexes = ordered.map((weekday) => WEEKDAYS.indexOf(weekday));
  for (let shift = 0; shift < 7; shift++) {
    const rotated = indexes.map((index) => (index - shift + 7) % 7).sort((a, b) => a - b);
    if (rotated[rotated.length - 1] - rotated[0] === rotated.length - 1) {
      return `${WEEKDAYS[(rotated[0] + shift) % 7].label}–${WEEKDAYS[(rotated[rotated.length - 1] + shift) % 7].label}`;
    }
  }
  return ordered.map((weekday) => weekday.label).join(", ");
}

/** "Mon–Fri, 08:00–18:00". */
export function scheduleLabelOf(schedule: SequenceSchedule): string {
  return `${daysLabelOf(schedule.days)}, ${clockOf(schedule.windowStart)}–${clockOf(schedule.windowEnd)}`;
}

/** "Dubai" for "Asia/Dubai": how a zone is named in a sentence. */
export function zoneCityOf(timeZone: string): string {
  return (timeZone.split("/").pop() ?? timeZone).replace(/_/g, " ");
}

interface WallClock {
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

/** The date, time and weekday an instant reads as on a clock in `timeZone`. */
export function wallClockOf(instant: Date, timeZone: string): WallClock {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-GB", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      weekday: "short",
      hourCycle: "h23",
    })
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
export function instantOf(date: string, time: string, timeZone: string): Date {
  const [year, month, day] = date.split("-").map(Number);
  const [hour, minute] = time.split(":").map(Number);
  const asIfUtc = Date.UTC(year, month - 1, day, hour, minute);
  let guess = asIfUtc - offsetOf(new Date(asIfUtc), timeZone);
  guess = asIfUtc - offsetOf(new Date(guess), timeZone);
  return new Date(guess);
}

function offsetOf(instant: Date, timeZone: string): number {
  const clock = wallClockOf(instant, timeZone);
  const [year, month, day] = clock.date.split("-").map(Number);
  const [hour, minute] = clock.time.split(":").map(Number);
  const seconds = instant.getUTCSeconds() * 1000 + instant.getUTCMilliseconds();
  return Date.UTC(year, month - 1, day, hour, minute) - (instant.getTime() - seconds);
}

/** A `YYYY-MM-DD` day moved by whole days. */
function shiftDate(date: string, days: number): string {
  const [year, month, day] = date.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

function weekdayOfDate(date: string): Weekday {
  const [year, month, day] = date.split("-").map(Number);
  return WEEKDAYS[(new Date(Date.UTC(year, month - 1, day)).getUTCDay() + 6) % 7].day;
}

function isOpen(schedule: SequenceSchedule, clock: WallClock): boolean {
  return (
    schedule.days.includes(clock.weekday) &&
    clock.time >= clockOf(schedule.windowStart) &&
    clock.time < clockOf(schedule.windowEnd)
  );
}

/** `now` when the window is open, else when it next opens. */
export function nextOpeningOf(schedule: SequenceSchedule, now: Date, timeZone: string): Date {
  const clock = wallClockOf(now, timeZone);
  if (isOpen(schedule, clock)) return now;
  let date = clock.time < clockOf(schedule.windowStart) ? clock.date : shiftDate(clock.date, 1);
  while (!schedule.days.includes(weekdayOfDate(date))) date = shiftDate(date, 1);
  return instantOf(date, clockOf(schedule.windowStart), timeZone);
}

/** `days` of the schedule's days after `from`, at the step's own time or `from`'s. */
export function followUpDueOf(
  schedule: SequenceSchedule,
  from: Date,
  timeZone: string,
  days: number,
  sendTime: string | null | undefined,
): Date {
  const clock = wallClockOf(from, timeZone);
  let date = clock.date;
  let remaining = days;
  while (remaining > 0) {
    date = shiftDate(date, 1);
    if (schedule.days.includes(weekdayOfDate(date))) remaining--;
  }
  const due = instantOf(date, sendTime ? clockOf(sendTime) : clock.time, timeZone);
  return isOpen(schedule, wallClockOf(due, timeZone)) ? due : nextOpeningOf(schedule, due, timeZone);
}

/** When the first email would go for each mode; null for a date and time not chosen yet. */
export function firstSendOf(
  mode: StartMode,
  schedule: SequenceSchedule,
  now: Date,
  timeZone: string,
  chosen: Date | null,
): Date | null {
  if (mode === "NOW") return now;
  if (mode === "AT") return chosen;
  return nextOpeningOf(schedule, now, timeZone);
}

/** When each follow-up would go if nobody replies, counted from the first email. */
export function followUpDatesOf(
  schedule: SequenceSchedule,
  steps: readonly SequenceStep[],
  firstSend: Date,
  timeZone: string,
): Date[] {
  const dates: Date[] = [];
  let previous = firstSend;
  for (const step of steps.slice(1)) {
    previous = followUpDueOf(schedule, previous, timeZone, step.delayWorkingDays, step.sendTime);
    dates.push(previous);
  }
  return dates;
}

/** Whether a chosen first send falls outside the schedule's days or hours. */
export function isOutsideSchedule(schedule: SequenceSchedule, at: Date, timeZone: string): boolean {
  return !isOpen(schedule, wallClockOf(at, timeZone));
}

/** "Tue 7 Oct" in `timeZone`. */
export function dayLabelOf(at: Date, timeZone: string): string {
  return at
    .toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short", timeZone })
    .replace(",", "");
}

/** "Tue 7 Oct, 09:30" in `timeZone`. */
export function whenLabelOf(at: Date, timeZone: string): string {
  return `${dayLabelOf(at, timeZone)}, ${wallClockOf(at, timeZone).time}`;
}
