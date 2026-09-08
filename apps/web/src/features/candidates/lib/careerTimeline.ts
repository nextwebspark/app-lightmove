import type { CandidateCareerEntry } from "../api/types";

/**
 * Reading a career history the way a consultant does, out of the free text a source published.
 *
 * <p>A period is stored as text on purpose (see the server's `CandidateCareerEntry`), so everything
 * here is a best effort over the shapes enrichment and researchers actually write — and every
 * function answers null rather than guessing when the text is not one of them. The raw text is
 * always shown; what these add is only ever beside it.
 */

export interface PeriodPoint {
  year: number;
  /** 1–12, absent when the source gave a year alone. */
  month?: number;
}

export interface ParsedPeriod {
  start: PeriodPoint;
  /** Null for a lone date — "2021" is a point, not a range, and has no tenure. */
  end: PeriodPoint | "present" | null;
}

export interface CareerGroup {
  company: string | null;
  posts: CandidateCareerEntry[];
}

const MONTHS = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];
const RANGE_SEPARATOR = /\s*(?:[–—-]|\bto\b|\bbis\b)\s*/i;
const OPEN_ENDED = /^(present|current|now|today|ongoing)$/i;
const ENDS_OPEN = /\b(present|current|now|today|ongoing)\s*$/i;

export function parsePeriod(period: string | null | undefined): ParsedPeriod | null {
  if (!period) return null;
  const parts = period.trim().split(RANGE_SEPARATOR);
  if (parts.length > 2) return null;
  const start = pointOf(parts[0]);
  if (!start) return null;
  if (parts.length === 1) return { start, end: null };
  const endText = parts[1].trim();
  // "2020 –" is how one provider writes an open tenure: a dash with nothing after it.
  if (endText === "" || OPEN_ENDED.test(endText)) return { start, end: "present" };
  const end = pointOf(endText);
  return end ? { start, end } : null;
}

/** "Jan 2021" / "January 2021" / "2021" / "01/2021" → a point; anything else → null. */
function pointOf(text: string): PeriodPoint | null {
  const value = text.trim();
  const yearOnly = /^(\d{4})$/.exec(value);
  if (yearOnly) return { year: Number(yearOnly[1]) };
  const monthName = /^([A-Za-z]{3,})\.?\s+(\d{4})$/.exec(value);
  if (monthName) {
    const month = MONTHS.indexOf(monthName[1].slice(0, 3).toLowerCase()) + 1;
    return month > 0 ? { year: Number(monthName[2]), month } : null;
  }
  const numeric = /^(\d{1,2})[/.](\d{4})$/.exec(value);
  if (numeric) {
    const month = Number(numeric[1]);
    return month >= 1 && month <= 12 ? { year: Number(numeric[2]), month } : null;
  }
  return null;
}

/**
 * How long a post lasted, as "3 yrs 2 mos", or null when the period is a point, unparsed, or under
 * a month. Month precision is used only when both ends have it — a year range never invents months.
 */
export function tenureOf(parsed: ParsedPeriod | null, now: Date = new Date()): string | null {
  if (!parsed || parsed.end === null) return null;
  const end: PeriodPoint =
    parsed.end === "present" ? { year: now.getFullYear(), month: now.getMonth() + 1 } : parsed.end;
  const { start } = parsed;

  if (start.month && end.month) {
    // Inclusive of both ends, which is how LinkedIn counts: Jan–Mar is three months.
    const months = (end.year - start.year) * 12 + (end.month - start.month) + 1;
    if (months <= 0) return null;
    return join(Math.floor(months / 12), months % 12);
  }
  return join(end.year - start.year, 0);
}

function join(years: number, months: number): string | null {
  const parts = [
    years > 0 ? `${years} ${years === 1 ? "yr" : "yrs"}` : null,
    months > 0 ? `${months} ${months === 1 ? "mo" : "mos"}` : null,
  ].filter(Boolean);
  return parts.length > 0 ? parts.join(" ") : null;
}

export function isCurrent(period: string | null | undefined): boolean {
  if (!period) return false;
  return ENDS_OPEN.test(period) || parsePeriod(period)?.end === "present";
}

/**
 * Consecutive posts at one company fold into a group, the way a profile shows a promotion. Stored
 * order is kept as it is: the writer's order is a fact about the source, not the drawer's to re-sort.
 * Posts naming no company never merge — two unknowns are not the same employer.
 */
export function groupCareer(career: readonly CandidateCareerEntry[]): CareerGroup[] {
  const groups: CareerGroup[] = [];
  for (const post of career) {
    const last = groups[groups.length - 1];
    if (last && post.company && sameCompany(last.company, post.company)) {
      last.posts.push(post);
      continue;
    }
    groups.push({ company: post.company, posts: [post] });
  }
  return groups;
}

function sameCompany(a: string | null, b: string): boolean {
  return a !== null && a.trim().toLowerCase() === b.trim().toLowerCase();
}

/** The one line a folded Experience section shows: the current post, then how long the list is. */
export function careerSummary(career: readonly CandidateCareerEntry[]): string | null {
  if (career.length === 0) return null;
  const headline = career.find((post) => isCurrent(post.period)) ?? career[0];
  const where = [headline.title, headline.company].filter(Boolean).join(" at ");
  const count = `${career.length} ${career.length === 1 ? "post" : "posts"}`;
  return where ? `${where} · ${count}` : count;
}
