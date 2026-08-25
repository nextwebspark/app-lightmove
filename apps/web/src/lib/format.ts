/** Tiny display helpers shared across features — the roster, drawers and tables all speak these. */

/** "ADMIN" → "Admin". */
export function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

/** "2026-09-15" → "15 Sep 2026", the mockups' date shape. */
export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return "—";
  return new Date(`${isoDate}T00:00:00`).toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

/**
 * An instant → "Mar 2026", the shape a "joined" line wants. Null when there is no instant, so the
 * caller can drop the clause rather than print a dash in the middle of a sentence.
 */
export function formatMonthYear(isoInstant: string | null | undefined): string | null {
  if (!isoInstant) return null;
  const moment = new Date(isoInstant);
  if (Number.isNaN(moment.getTime())) return null;
  return moment.toLocaleDateString("en-GB", { month: "short", year: "numeric" });
}

/**
 * An instant → "15 Sep 2026". The same shape {@link formatDate} produces, for the case where the
 * server sent a moment rather than a calendar date — a row's `addedAt`, where the time of day is
 * recorded but is not what the reader is being told.
 */
export function formatInstantDate(isoInstant: string | null | undefined): string | null {
  if (!isoInstant) return null;
  const moment = new Date(isoInstant);
  if (Number.isNaN(moment.getTime())) return null;
  return moment.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
}

/** 450000 → "450K", the compact money shape the hero and the report band both show. */
export function abbreviateAmount(value: number): string {
  return value >= 1000 ? `${Math.round(value / 1000)}K` : String(value);
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

function trimAmount(value: number): string {
  return value >= 10 ? String(Math.round(value)) : value.toFixed(1).replace(/\.0$/, "");
}
