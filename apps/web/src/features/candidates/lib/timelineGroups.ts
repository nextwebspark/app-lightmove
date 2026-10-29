import { dayKeyOf, formatDate, formatWeekdayDate, shiftDay } from "../../../lib/datetime";
import type { PersonTimelineEntry, TimelineGroup } from "../api/types";

/** The timeline's filter chips, in the mockup's order; "Everything" asks for no group. */
export const TIMELINE_GROUPS: { value: TimelineGroup | ""; label: string }[] = [
  { value: "", label: "Everything" },
  { value: "positions", label: "Positions & status" },
  { value: "notes", label: "Notes" },
  { value: "documents", label: "Documents" },
  { value: "contacts", label: "Contacts" },
  { value: "profile", label: "Profile & AI" },
  { value: "tags", label: "Tags & owner" },
];

/** "Today", "Yesterday", or "Mon 14 Sep" — with the year once it is not this one. */
function dayLabelOf(isoInstant: string, now: Date = new Date()): string {
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return "—";
  const day = dayKeyOf(at);
  const today = dayKeyOf(now);
  if (day === today) return "Today";
  if (day === shiftDay(today, -1)) return "Yesterday";
  return day.slice(0, 4) === today.slice(0, 4) ? (formatWeekdayDate(at) ?? "—") : formatDate(at);
}

/** Lines in the order given, gathered under their day's label. */
export function groupByDay(entries: readonly PersonTimelineEntry[]): [string, PersonTimelineEntry[]][] {
  const days = new Map<string, PersonTimelineEntry[]>();
  for (const entry of entries) {
    const day = dayLabelOf(entry.occurredAt);
    days.set(day, [...(days.get(day) ?? []), entry]);
  }
  return [...days.entries()];
}
