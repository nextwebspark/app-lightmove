/** How the Meetings section and the Book a call grid spell a time. */

/** The date tile's two lines, in the viewer's zone: "OCT" over "7". */
export function dateTileOf(isoInstant: string): { month: string; day: string } {
  const at = new Date(isoInstant);
  return {
    month: at.toLocaleDateString("en-GB", { month: "short" }).toUpperCase(),
    day: String(at.getDate()),
  };
}

/** "Tue 7 Oct · 10:00–10:30", in the viewer's zone. */
export function meetingWhenOf(startsAt: string, endsAt: string): string {
  const start = new Date(startsAt);
  const end = new Date(endsAt);
  return `${weekdayDateOf(start)} · ${clockOf(start)}–${clockOf(end)}`;
}

/** "10:59", in the viewer's zone. */
export function timeOfDayOf(isoInstant: string): string {
  return clockOf(new Date(isoInstant));
}

/** "Thu 2 Oct" for a `YYYY-MM-DD` day, which is already the consultant's own date. */
export function slotDayLabelOf(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  return weekdayDateOf(new Date(Date.UTC(year, month - 1, day)), "UTC");
}

/** A day tile's three lines for a `YYYY-MM-DD` day: "MON", "5", "Oct". */
export function slotDayPartsOf(isoDate: string): { weekday: string; day: string; month: string } {
  const [year, month, day] = isoDate.split("-").map(Number);
  const at = new Date(Date.UTC(year, month - 1, day));
  return {
    weekday: at.toLocaleDateString("en-GB", { weekday: "short", timeZone: "UTC" }).toUpperCase(),
    day: String(day),
    month: at.toLocaleDateString("en-GB", { month: "short", timeZone: "UTC" }),
  };
}

/** A `YYYY-MM-DD` day moved by whole days, calendar arithmetic with no zone in it. */
export function shiftDateOf(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

/** "11:00" in the consultant's zone — the grid reads their calendar, so it speaks their time. */
export function slotTimeOf(isoInstant: string, timeZone: string): string {
  return clockOf(new Date(isoInstant), timeZone);
}

function weekdayDateOf(at: Date, timeZone?: string): string {
  return at
    .toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short", timeZone })
    .replace(",", "");
}

function clockOf(at: Date, timeZone?: string): string {
  return at.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", timeZone });
}

/** "Tue 7 Oct · 10:00", in the viewer's zone: when a recorded call began. */
export function meetingDayOf(isoInstant: string): string {
  const at = new Date(isoInstant);
  return `${weekdayDateOf(at)} · ${clockOf(at)}`;
}

/** "04:31" into a call, or "1:04:31" past the hour. */
export function elapsedOf(fromIso: string, atIso: string): string {
  const seconds = Math.max(0, Math.round((new Date(atIso).getTime() - new Date(fromIso).getTime()) / 1000));
  const hours = Math.floor(seconds / 3600);
  const minutes = String(Math.floor((seconds % 3600) / 60)).padStart(2, "0");
  const rest = String(seconds % 60).padStart(2, "0");
  return hours > 0 ? `${hours}:${minutes}:${rest}` : `${minutes}:${rest}`;
}
