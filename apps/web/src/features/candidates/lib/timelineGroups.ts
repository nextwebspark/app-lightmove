import type { PersonTimelineEntry, TimelineGroup } from "../api/types";

/** The timeline's filter chips, in the mockup's order; "Everything" asks for no group. */
export const TIMELINE_GROUPS: { value: TimelineGroup | ""; label: string }[] = [
  { value: "", label: "Everything" },
  { value: "positions", label: "Positions & status" },
  { value: "notes", label: "Notes" },
  { value: "contacts", label: "Contacts" },
  { value: "profile", label: "Profile & AI" },
  { value: "tags", label: "Tags & owner" },
];

/** "Today", "Yesterday", or "Mon 14 Sep" — with the year once it is not this one. */
function dayLabelOf(isoInstant: string, now: Date = new Date()): string {
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return "—";
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (at.toDateString() === now.toDateString()) return "Today";
  if (at.toDateString() === yesterday.toDateString()) return "Yesterday";
  return at.toLocaleDateString("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    ...(at.getFullYear() === now.getFullYear() ? {} : { year: "numeric" }),
  });
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
