import { formatInstantDate, formatInstantDateTime, formatInstantTime } from "../../../lib/format";
import type { DrawerSection } from "../../candidates/lib/drawerTabs";
import type { NotificationKind, NotificationResponse } from "../api/types";
import { dayKeyOf, daysBetween, instantOf, shiftDay, wallClockOf } from "../../../lib/datetime";
import { positionPipelinePath } from "../../pipeline/lib/pipelinePath";

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

/** Where in the drawer a row's work is: the run a reply stopped, the contact details a bounce calls to fix, the call. */
const OUTREACH_SECTION_OF: Partial<Record<NotificationKind, DrawerSection>> = {
  outreach_replied: "outreach",
  outreach_bounced: "contact",
  call_booked: "meetings",
};

/**
 * Where a row leads: the position's pipeline, on the executive's drawer when it is about one — on the note when it
 * names one, or on the part of it its work is in; an executive since unmapped from the position opens on the
 * Candidates page; else My positions.
 */
const PERSON_KINDS: ReadonlySet<NotificationKind> = new Set([
  "copy_requested",
  "deletion_requested",
  "person_objected",
  "candidate_opted_out",
]);

export function notificationHref(notification: NotificationResponse): string {
  // A privacy request (#923) or an opt-out is about the person, never a position.
  if (PERSON_KINDS.has(notification.kind) && notification.personId) {
    return `/candidates?person=${encodeURIComponent(notification.personId)}`;
  }
  if (!notification.projectId) return "/";
  if (!notification.candidateId) {
    if (!notification.personId) return positionPipelinePath(notification.projectId);
    const person = `/candidates?person=${encodeURIComponent(notification.personId)}`;
    if (notification.note) return `${person}&tab=notes`;
    return person;
  }
  const params: Record<string, string> = { candidate: notification.candidateId };
  const section = OUTREACH_SECTION_OF[notification.kind];
  if (notification.note) params.note = notification.note.id;
  else if (section) params.section = section;
  return positionPipelinePath(notification.projectId, params);
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
