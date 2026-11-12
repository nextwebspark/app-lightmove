import { getDateTimePreferences, type DateFormatStyle, type TimeFormatStyle } from "./preferences";
import { dayKeyOf, offsetOf, wallClockOf, type Weekday } from "./zone";

/**
 * Every date and time the app draws, in the signed-in person's zone and formats (Settings → General, overridden in
 * Settings → Profile). A `YYYY-MM-DD` value is a calendar date and reads as the day it names in any zone; anything
 * else is an instant.
 */

/** An instant as an ISO string or a `Date`, or a `YYYY-MM-DD` calendar date. */
export type DateValue = string | Date;

/** Formats a caller may pin instead of the person's own — a mailbox's zone, a preview of an unsaved choice. */
export interface FormatOptions {
  timeZone?: string;
  dateFormat?: DateFormatStyle;
  timeFormat?: TimeFormatStyle;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const WEEKDAY_LABELS: Record<Weekday, string> = {
  MONDAY: "Mon",
  TUESDAY: "Tue",
  WEDNESDAY: "Wed",
  THURSDAY: "Thu",
  FRIDAY: "Fri",
  SATURDAY: "Sat",
  SUNDAY: "Sun",
};
const CALENDAR_DATE = /^\d{4}-\d{2}-\d{2}$/;

interface Day {
  year: number;
  month: number;
  day: number;
  weekday: Weekday;
}

interface Moment extends Day {
  hour: number;
  minute: number;
}

/** "04 Dec 2026", "Dec 4, 2026", "04/12/2026", "12/04/2026" or "2026-12-04". */
export function formatDate(value: DateValue | null | undefined, options?: FormatOptions): string {
  const day = dayOf(value, options);
  return day ? dateOf(day, dateFormatOf(options)) : "—";
}

/** {@link formatDate} for an instant, null where there is none — so a caller can drop the clause. */
export function formatInstantDate(value: DateValue | null | undefined, options?: FormatOptions): string | null {
  const day = dayOf(value, options);
  return day ? dateOf(day, dateFormatOf(options)) : null;
}

/** "04 Dec 2026, 14:30" — the one shape a date with a time reads in. */
export function formatInstantDateTime(value: DateValue | null | undefined, options?: FormatOptions): string | null {
  const moment = momentOf(value, options);
  return moment ? `${dateOf(moment, dateFormatOf(options))}, ${timeOf(moment, timeFormatOf(options))}` : null;
}

/** "14:30" or "2:30 PM": a time of day where the day goes without saying. */
export function formatInstantTime(value: DateValue | null | undefined, options?: FormatOptions): string | null {
  const moment = momentOf(value, options);
  return moment ? timeOf(moment, timeFormatOf(options)) : null;
}

/** "4 Dec" or "Dec 4": a date this year's reader places without the year. */
export function formatShortDate(value: DateValue | null | undefined, options?: FormatOptions): string | null {
  const day = dayOf(value, options);
  if (!day) return null;
  return isMonthFirst(dateFormatOf(options))
    ? `${MONTHS[day.month - 1]} ${day.day}`
    : `${day.day} ${MONTHS[day.month - 1]}`;
}

/** "Tue 4 Dec" or "Tue, Dec 4". */
export function formatWeekdayDate(value: DateValue | null | undefined, options?: FormatOptions): string | null {
  const day = dayOf(value, options);
  if (!day) return null;
  const weekday = WEEKDAY_LABELS[day.weekday];
  return isMonthFirst(dateFormatOf(options))
    ? `${weekday}, ${MONTHS[day.month - 1]} ${day.day}`
    : `${weekday} ${day.day} ${MONTHS[day.month - 1]}`;
}

/** "Tue 4 Dec, 14:30" or "Tue, Dec 4, 2:30 PM". */
export function formatWeekdayDateTime(value: DateValue | null | undefined, options?: FormatOptions): string | null {
  const moment = momentOf(value, options);
  if (!moment) return null;
  return `${formatWeekdayDate(value, options)}, ${timeOf(moment, timeFormatOf(options))}`;
}

/** A date tile's parts, in the person's zone (a calendar date as it names itself): "Mon", "5", "Oct". */
export function datePartsOf(
  value: DateValue | null | undefined,
  options?: FormatOptions,
): { weekday: string; day: string; month: string } | null {
  const day = dayOf(value, options);
  return day ? { weekday: WEEKDAY_LABELS[day.weekday], day: String(day.day), month: MONTHS[day.month - 1] } : null;
}

/** "Mar 2026". */
export function formatMonthYear(value: DateValue | null | undefined, options?: FormatOptions): string | null {
  const day = dayOf(value, options);
  return day ? `${MONTHS[day.month - 1]} ${day.year}` : null;
}

const LONG_MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

/** "December 2026" — a calendar's own heading. */
export function formatLongMonthYear(value: DateValue | null | undefined, options?: FormatOptions): string | null {
  const day = dayOf(value, options);
  return day ? `${LONG_MONTHS[day.month - 1]} ${day.year}` : null;
}

/** "Tue, 04 Dec 2026, 14:30 GMT+4" — everything, for a timestamp's hover title. */
export function formatFull(value: DateValue | null | undefined, options?: FormatOptions): string | null {
  const moment = momentOf(value, options);
  if (!value || !moment || (typeof value === "string" && CALENDAR_DATE.test(value))) {
    return formatInstantDate(value, options);
  }
  const instant = value instanceof Date ? value : new Date(value);
  return `${WEEKDAY_LABELS[moment.weekday]}, ${dateOf(moment, dateFormatOf(options))}, ${timeOf(
    moment,
    timeFormatOf(options),
  )} ${offsetLabelOf(instant, zoneOf(options))}`;
}

/** A wall-clock "14:30" (or the server's "14:30:00") as the person's clock reads it: "14:30" or "2:30 PM". */
export function formatClock(time: string, options?: FormatOptions): string {
  const [hour, minute] = time.split(":").map(Number);
  if (Number.isNaN(hour) || Number.isNaN(minute)) return time;
  return timeOf({ hour, minute }, timeFormatOf(options));
}

/** "GMT+4", "GMT+5:30" or "GMT" — how far a zone's clock is from UTC at that instant. */
export function offsetLabelOf(instant: Date, timeZone: string): string {
  const minutes = Math.round(offsetOf(instant, timeZone) / 60_000);
  if (minutes === 0) return "GMT";
  const sign = minutes > 0 ? "+" : "-";
  const hours = Math.floor(Math.abs(minutes) / 60);
  const rest = Math.abs(minutes) % 60;
  return `GMT${sign}${hours}${rest === 0 ? "" : `:${String(rest).padStart(2, "0")}`}`;
}

/** Whether a date shape reads its month before its day: what every shorter form of it follows. */
export function isMonthFirst(format: DateFormatStyle): boolean {
  return format === "MMM_D_YYYY" || format === "MM_DD_YYYY";
}

function dateOf(day: Day, format: DateFormatStyle): string {
  const dd = String(day.day).padStart(2, "0");
  const mm = String(day.month).padStart(2, "0");
  const month = MONTHS[day.month - 1];
  switch (format) {
    case "MMM_D_YYYY":
      return `${month} ${day.day}, ${day.year}`;
    case "DD_MM_YYYY":
      return `${dd}/${mm}/${day.year}`;
    case "MM_DD_YYYY":
      return `${mm}/${dd}/${day.year}`;
    case "YYYY_MM_DD":
      return `${day.year}-${mm}-${dd}`;
    default:
      return `${dd} ${month} ${day.year}`;
  }
}

function timeOf(moment: { hour: number; minute: number }, format: TimeFormatStyle): string {
  const minute = String(moment.minute).padStart(2, "0");
  if (format === "H12") {
    const hour = moment.hour % 12 === 0 ? 12 : moment.hour % 12;
    return `${hour}:${minute} ${moment.hour < 12 ? "AM" : "PM"}`;
  }
  return `${String(moment.hour).padStart(2, "0")}:${minute}`;
}

function dayOf(value: DateValue | null | undefined, options?: FormatOptions): Day | null {
  if (typeof value === "string" && CALENDAR_DATE.test(value)) {
    const [year, month, day] = value.split("-").map(Number);
    const weekdayIndex = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
    return { year, month, day, weekday: WEEKDAYS_FROM_SUNDAY[weekdayIndex] };
  }
  return momentOf(value, options);
}

const WEEKDAYS_FROM_SUNDAY: Weekday[] = [
  "SUNDAY",
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
];

function momentOf(value: DateValue | null | undefined, options?: FormatOptions): Moment | null {
  if (value === null || value === undefined || value === "") return null;
  const instant = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(instant.getTime())) return null;
  const clock = wallClockOf(instant, zoneOf(options));
  const [year, month, day] = clock.date.split("-").map(Number);
  const [hour, minute] = clock.time.split(":").map(Number);
  return { year, month, day, hour, minute, weekday: clock.weekday };
}

function zoneOf(options?: FormatOptions): string {
  return options?.timeZone ?? getDateTimePreferences().timeZone;
}

function dateFormatOf(options?: FormatOptions): DateFormatStyle {
  return options?.dateFormat ?? getDateTimePreferences().dateFormat;
}

function timeFormatOf(options?: FormatOptions): TimeFormatStyle {
  return options?.timeFormat ?? getDateTimePreferences().timeFormat;
}

/** The person's own `YYYY-MM-DD` today. */
export function todayKey(now: Date = new Date(), timeZone?: string): string {
  return dayKeyOf(now, timeZone);
}
