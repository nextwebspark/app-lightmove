/** A due moment as the modal's two inputs hold it, in the viewer's own zone: `2026-10-05` and `09:00`. */
export interface LocalDue {
  date: string;
  time: string;
}

export type QuickDueKey = "today" | "tomorrow" | "in3" | "monday";

export const QUICK_DUES: readonly { key: QuickDueKey; label: string }[] = [
  { key: "today", label: "Today 17:00" },
  { key: "tomorrow", label: "Tomorrow 09:00" },
  { key: "in3", label: "In 3 days" },
  { key: "monday", label: "Next Monday" },
];

const MORNING = "09:00";
const END_OF_DAY = "17:00";

const pad = (value: number) => String(value).padStart(2, "0");

export function isoDateOf(moment: Date): string {
  return `${moment.getFullYear()}-${pad(moment.getMonth() + 1)}-${pad(moment.getDate())}`;
}

function daysAfter(now: Date, days: number): Date {
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + days);
}

export function localDueOf(isoInstant: string): LocalDue {
  const moment = new Date(isoInstant);
  return { date: isoDateOf(moment), time: `${pad(moment.getHours())}:${pad(moment.getMinutes())}` };
}

/** The instant the two inputs name, or null while either is blank or unreadable. */
export function dueInstantOf(due: LocalDue): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(due.date) || !/^\d{2}:\d{2}$/.test(due.time)) return null;
  const moment = new Date(`${due.date}T${due.time}:00`);
  return Number.isNaN(moment.getTime()) ? null : moment.toISOString();
}

/** A preset's due: so many days from today at 09:00, or 17:00 when it falls due the same day. */
export function presetDueOf(offsetDays: number, now: Date = new Date()): LocalDue {
  return { date: isoDateOf(daysAfter(now, offsetDays)), time: offsetDays === 0 ? END_OF_DAY : MORNING };
}

/** A new task's due until somebody says otherwise: tomorrow morning. */
export function defaultDueOf(now: Date = new Date()): LocalDue {
  return presetDueOf(1, now);
}

export function quickDueOf(key: QuickDueKey, now: Date = new Date()): LocalDue {
  switch (key) {
    case "today":
      return { date: isoDateOf(now), time: END_OF_DAY };
    case "tomorrow":
      return { date: isoDateOf(daysAfter(now, 1)), time: MORNING };
    case "in3":
      return { date: isoDateOf(daysAfter(now, 3)), time: MORNING };
    case "monday":
      return { date: isoDateOf(daysAfter(now, (8 - now.getDay()) % 7 || 7)), time: MORNING };
  }
}

/** "Same day", "+1 day", "+5 days"; null for a preset that leaves the due to the person. */
export function presetDueLabel(offsetDays: number | null): string | null {
  if (offsetDays === null) return null;
  if (offsetDays === 0) return "Same day";
  return `+${offsetDays} day${offsetDays === 1 ? "" : "s"}`;
}

/** The viewer's own zone, which every due here is read and written in. */
export function viewerTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
}
