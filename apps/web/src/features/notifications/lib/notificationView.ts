import { formatInstantDate, formatInstantDateTime, formatInstantTime } from "../../../lib/format";
import type { NotificationResponse } from "../api/types";

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

/**
 * Where a row leads: the position's pipeline, on the executive's drawer when it is about one and on the note when
 * it names one; an executive since unmapped from the position opens on the Candidates page; else My positions.
 */
export function notificationHref(notification: NotificationResponse): string {
  if (!notification.projectId) return "/";
  const pipeline = `/projects/${notification.projectId}/pipeline`;
  if (!notification.candidateId) {
    if (!notification.personId) return pipeline;
    const person = `/candidates?person=${encodeURIComponent(notification.personId)}`;
    return notification.note ? `${person}&tab=notes` : person;
  }
  const candidate = `${pipeline}?candidate=${encodeURIComponent(notification.candidateId)}`;
  return notification.note ? `${candidate}&note=${encodeURIComponent(notification.note.id)}` : candidate;
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
    ["week", "Next week", atNine(now, (8 - now.getDay()) % 7 || 7)],
  ];
  return choices.map(([key, label, until]) => ({
    key,
    label,
    until,
    hint: formatInstantDateTime(until.toISOString()) ?? "",
  }));
}

function atNine(now: Date, daysAhead: number): Date {
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + daysAhead, 9, 0, 0, 0);
}

function daysBefore(isoInstant: string, now: Date): number {
  const moment = new Date(isoInstant);
  const startOf = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  return Math.round((startOf(now) - startOf(moment)) / 86_400_000);
}
