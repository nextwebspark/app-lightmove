import { datePartsOf, formatInstantTime, formatWeekdayDate } from "../../../lib/datetime";

/** How the Meetings section and the Book a call grid spell a time — in the person's zone and formats. */

/** The date tile's two lines: "OCT" over "7". */
export function dateTileOf(isoInstant: string): { month: string; day: string } {
  const parts = datePartsOf(isoInstant);
  return { month: (parts?.month ?? "").toUpperCase(), day: parts?.day ?? "" };
}

/** "Tue 7 Oct · 10:00–10:30". */
export function meetingWhenOf(startsAt: string, endsAt: string): string {
  return `${formatWeekdayDate(startsAt) ?? ""} · ${formatInstantTime(startsAt) ?? ""}–${formatInstantTime(endsAt) ?? ""}`;
}

/** "10:59". */
export function timeOfDayOf(isoInstant: string): string {
  return formatInstantTime(isoInstant) ?? "";
}

/** "Thu 2 Oct" for a `YYYY-MM-DD` day, which is already the consultant's own date. */
export function slotDayLabelOf(isoDate: string): string {
  return formatWeekdayDate(isoDate) ?? "";
}

/** A day tile's three lines for a `YYYY-MM-DD` day: "MON", "5", "Oct". */
export function slotDayPartsOf(isoDate: string): { weekday: string; day: string; month: string } {
  const parts = datePartsOf(isoDate) ?? { weekday: "", day: "", month: "" };
  return { ...parts, weekday: parts.weekday.toUpperCase() };
}

/** A `YYYY-MM-DD` day moved by whole days, calendar arithmetic with no zone in it. */
export function shiftDateOf(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

/** "11:00" in the consultant's zone — the grid reads their calendar, so it speaks their time. */
export function slotTimeOf(isoInstant: string, timeZone: string): string {
  return formatInstantTime(isoInstant, { timeZone }) ?? "";
}

/** "Tue 7 Oct · 10:00": when a recorded call began. */
export function meetingDayOf(isoInstant: string): string {
  return `${formatWeekdayDate(isoInstant) ?? ""} · ${formatInstantTime(isoInstant) ?? ""}`;
}

/** "04:31" into a call, or "1:04:31" past the hour. */
export function elapsedOf(fromIso: string, atIso: string): string {
  const seconds = Math.max(0, Math.round((new Date(atIso).getTime() - new Date(fromIso).getTime()) / 1000));
  const hours = Math.floor(seconds / 3600);
  const minutes = String(Math.floor((seconds % 3600) / 60)).padStart(2, "0");
  const rest = String(seconds % 60).padStart(2, "0");
  return hours > 0 ? `${hours}:${minutes}:${rest}` : `${minutes}:${rest}`;
}
