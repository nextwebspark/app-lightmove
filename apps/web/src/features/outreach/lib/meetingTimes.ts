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

/** "22 Sep": a past meeting's mono date. */
export function shortDateOf(isoInstant: string): string {
  return new Date(isoInstant).toLocaleDateString("en-GB", { day: "numeric", month: "short" });
}

/** "Thu 2 Oct" for a `YYYY-MM-DD` day, which is already the consultant's own date. */
export function slotDayLabelOf(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  return weekdayDateOf(new Date(Date.UTC(year, month - 1, day)), "UTC");
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
