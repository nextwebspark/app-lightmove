import {
  dayKeyOf,
  daysBetween,
  formatInstantDate,
  formatInstantDateTime,
  formatInstantTime,
} from "./datetime";

/** Tiny display helpers shared across features — the roster, drawers and tables all speak these. */

/** "ADMIN" → "Admin". */
export function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

const LOWER_CASE_WORDS = new Set(["&", "and", "of", "the", "for", "in"]);

/** "oil & energy" → "Oil & Energy" — the server's `Industries.displayNameOf`, for the universe's lower-case labels. */
export function industryDisplayName(label: string): string {
  if (label !== label.toLowerCase()) return label;
  return label
    .split(" ")
    .map((word, index) =>
      word === "" || (index > 0 && LOWER_CASE_WORDS.has(word)) ? word : word.charAt(0).toUpperCase() + word.slice(1),
    )
    .join(" ");
}

export {
  formatDate,
  formatInstantDate,
  formatInstantDateTime,
  formatInstantTime,
  formatMonthYear,
} from "./datetime";

/**
 * Whole days from today to an ISO date, both read in the signed-in person's zone: negative once it has passed. The
 * server counts in UTC, so near midnight the two can disagree by a day.
 */
export function daysUntil(isoDate: string, now: Date = new Date()): number {
  return daysBetween(dayKeyOf(now), isoDate);
}

/** How near a due moment is: overdue reads red, today amber, anything later plain. */
export type DueTone = "overdue" | "today" | "plain";

/**
 * A due moment as a chip says it — "Today · 16:00", "Tomorrow · 09:00", "Overdue · 03 Oct 2026", or else
 * "08 Oct 2026, 09:00" — and its tone. Days are the person's own.
 */
export function describeDue(
  isoInstant: string | null | undefined,
  now: Date = new Date(),
): { label: string; tone: DueTone } | null {
  if (!isoInstant) return null;
  const due = new Date(isoInstant);
  if (Number.isNaN(due.getTime())) return null;
  const days = daysBetween(dayKeyOf(now), dayKeyOf(due));
  const clock = formatInstantTime(due) ?? "";
  if (due.getTime() < now.getTime()) {
    return { label: days === 0 ? `Overdue · ${clock}` : `Overdue · ${formatInstantDate(due)}`, tone: "overdue" };
  }
  if (days === 0) return { label: `Today · ${clock}`, tone: "today" };
  if (days === 1) return { label: `Tomorrow · ${clock}`, tone: "plain" };
  return { label: formatInstantDateTime(due) ?? "", tone: "plain" };
}

/** 450000 → "450,000". The money shape a package reads in, where the exact figure is the point. */
export function formatNumber(value: number): string {
  return value.toLocaleString("en-GB");
}

/** "Sara Al-Mansour" → "SA". */
export function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  const first = parts[0][0];
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase();
}

/**
 * An instant → "active now" / "2 hours ago" / "3 days ago", the mockup's session-row wording.
 *
 * Coarse on purpose: this labels a refresh, which trails real activity by up to one access-token
 * lifetime, so minute-level precision would be a number we cannot honestly claim.
 */
export function formatRelativeTime(isoInstant: string): string {
  const elapsedMs = Date.now() - new Date(isoInstant).getTime();
  if (Number.isNaN(elapsedMs)) return "—";

  const minutes = Math.floor(elapsedMs / 60_000);
  if (minutes < 5) return "active now";
  if (minutes < 60) return `${minutes} minutes ago`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours === 1 ? "1 hour ago" : `${hours} hours ago`;

  const days = Math.floor(hours / 24);
  return days === 1 ? "1 day ago" : `${days} days ago`;
}

/** An instant → "just now" / "5 minutes ago" / "yesterday", for a key's or an AI app's last use. */
export function formatTimeAgo(isoInstant: string, now: Date = new Date()): string {
  const minutes = Math.floor((now.getTime() - new Date(isoInstant).getTime()) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return minutes === 1 ? "1 minute ago" : `${minutes} minutes ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours === 1 ? "1 hour ago" : `${hours} hours ago`;
  const days = Math.floor(hours / 24);
  return days === 1 ? "yesterday" : `${days} days ago`;
}

/**
 * Null is the common case — Apollo publishes a revenue figure on roughly one row in ten — and reads
 * as unknown, not zero. Shared by both company grids: a revenue that says "$1.2B" on Strategy and
 * "1200000000" under Companies is the same fact told twice.
 */
export function formatMoney(amount: number | null): string {
  if (amount === null) return "—";
  if (amount >= 1_000_000_000) return `$${trimAmount(amount / 1_000_000_000)}B`;
  if (amount >= 1_000_000) return `$${trimAmount(amount / 1_000_000)}M`;
  return `$${amount.toLocaleString()}`;
}

/** An empty list is nothing to show, not an empty string in a cell that looks like a blank value. */
export function joined(values: string[]): string | null {
  return values.length > 0 ? values.join(", ") : null;
}

/** "1 change", "3 changes": a count with its noun, the plural an `s`. */
export function countOf(count: number, noun: string): string {
  return count === 1 ? `1 ${noun}` : `${count} ${noun}s`;
}

function trimAmount(value: number): string {
  return value >= 10 ? String(Math.round(value)) : value.toFixed(1).replace(/\.0$/, "");
}
