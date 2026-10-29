import { formatInstantDate, formatInstantDateTime, formatInstantTime } from "../../../lib/format";
import type { DrawerSection } from "../../candidates/lib/drawerTabs";
import type { NotificationKind, NotificationResponse } from "../api/types";
import { dayKeyOf, daysBetween, instantOf, shiftDay, wallClockOf } from "../../../lib/datetime";

/** The bell's count: hidden at none, and never wider than "9+". */
export function unreadBadgeOf(unreadCount: number): string | null {
  if (unreadCount <= 0) return null;
  return unreadCount > 9 ? "9+" : String(unreadCount);
}

export interface NotificationDay {
  label: string;
  items: NotificationResponse[];
}

/** Rows under "Today", "Yesterday" or "08 Oct 2026", in the order they came; days are the viewer's own. */
export function groupByDay(items: readonly NotificationResponse[], now: Date = new Date()): NotificationDay[] {
  const days: NotificationDay[] = [];
  for (const item of items) {
    const label = dayLabelOf(item.createdAt, now);
    const day = days.at(-1)?.label === label ? days.at(-1) : undefined;
    if (day) day.items.push(item);
    else days.push({ label, items: [item] });
  }
  return days;
}

export function dayLabelOf(isoInstant: string, now: Date = new Date()): string {
  const days = daysBefore(isoInstant, now);
  if (days === 0) return "Today";
  if (days === 1) return "Yesterday";
  return formatInstantDate(isoInstant) ?? "";
}

/** "16:05" for a row from today, otherwise its date. */
export function timeLabelOf(isoInstant: string, now: Date = new Date()): string {
  return (daysBefore(isoInstant, now) === 0 ? formatInstantTime(isoInstant) : formatInstantDate(isoInstant)) ?? "";
}

/** Where in the drawer's Contact tab a row's work is: the thread a reply came on, the address that bounced, the call. */
const CONTACT_SECTION_OF: Partial<Record<NotificationKind, DrawerSection>> = {
  outreach_replied: "outreach",
  outreach_bounced: "contact",
  call_booked: "meetings",
};

/**
 * Where a row leads: the position's pipeline, on the executive's drawer when it is about one — on the note when it
 * names one, or on the Contact section its work is in; an executive since unmapped from the position opens on the
 * Candidates page; else My positions.
 */
export function notificationHref(notification: NotificationResponse): string {
  if (!notification.projectId) return "/";
  const pipeline = `/projects/${notification.projectId}/pipeline`;
  if (!notification.candidateId) {
    if (!notification.personId) return pipeline;
    const person = `/candidates?person=${encodeURIComponent(notification.personId)}`;
    if (notification.note) return `${person}&tab=notes`;
    return CONTACT_SECTION_OF[notification.kind] ? `${person}&tab=contact` : person;
  }
  const candidate = `${pipeline}?candidate=${encodeURIComponent(notification.candidateId)}`;
  if (notification.note) return `${candidate}&note=${encodeURIComponent(notification.note.id)}`;
  const section = CONTACT_SECTION_OF[notification.kind];
  return section ? `${candidate}&section=${section}` : candidate;
}

export type SnoozeChoice = "hour" | "tomorrow" | "week";

export interface SnoozeOption {
  key: SnoozeChoice;
  label: string;
  until: Date;
  /** "15 Oct 2026, 09:00" — when it comes back. */
  hint: string;
}

/** An hour from now, tomorrow at 09:00, or next Monday at 09:00. */
export function snoozeOptionsOf(now: Date = new Date()): SnoozeOption[] {
  const choices: [SnoozeChoice, string, Date][] = [
    ["hour", "In 1 hour", new Date(now.getTime() + 3_600_000)],
    ["tomorrow", "Tomorrow", atNine(now, 1)],
    ["week", "Next week", atNine(now, (8 - WEEKDAY_INDEX[wallClockOf(now).weekday]) % 7 || 7)],
  ];
  return choices.map(([key, label, until]) => ({
    key,
    label,
    until,
    hint: formatInstantDateTime(until.toISOString()) ?? "",
  }));
}

const WEEKDAY_INDEX = { SUNDAY: 0, MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6 };

/** 09:00 so many days from today, on the person's own clock. */
function atNine(now: Date, daysAhead: number): Date {
  return instantOf(shiftDay(dayKeyOf(now), daysAhead), "09:00");
}

function daysBefore(isoInstant: string, now: Date): number {
  return daysBetween(dayKeyOf(new Date(isoInstant)), dayKeyOf(now));
}
