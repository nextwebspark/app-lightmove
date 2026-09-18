/**
 * How long a mandate plans to wait for somebody — one vocabulary, both halves of a search.
 *
 * The brief states a period it can plan for; an executive's profile states the one they must serve.
 * Those are the same five choices, so one list answers both, and the screens offer nothing else.
 *
 * The two contracts store it differently, which is why the conversions live here: the position brief
 * keeps a count and a unit (`noticeValue` + `noticeUnit`, wide enough to hold ninety days), and a
 * candidate keeps the option's own label in a free-text column. Neither column is narrowed to these
 * five — a brief written before the picker existed, an externally authored template and a spreadsheet
 * all state what they state, and a write that refused them would clear a fact nobody touched. A value
 * outside the vocabulary stays offered, as recorded, wherever it is already stored.
 *
 * `None` is a claim — this person can start now — and is not the blank a profile carries before
 * anybody asked, so every picker keeps a blank option above these five.
 *
 * Deviation from the mockups, deliberate: Position.dc.html, Settings.dc.html and
 * CandidateProfile.handoff.md all draw a free number, a unit select or a text box. A mandate plans in
 * whole months, and three screens spelling one period four ways is what made it uncountable.
 */

export type NoticeUnit = "DAYS" | "WEEKS" | "MONTHS";

export const NOTICE_UNIT_LABELS: Record<NoticeUnit, string> = {
  MONTHS: "Months",
  WEEKS: "Weeks",
  DAYS: "Days",
};

/** The label is the candidate contract's stored value; the months are the brief's own count. */
export const NOTICE_PERIODS: { label: string; months: number }[] = [
  { label: "None", months: 0 },
  { label: "1 month", months: 1 },
  { label: "2 months", months: 2 },
  { label: "3 months", months: 3 },
  { label: "6 months", months: 6 },
];

export const NOTICE_PERIOD_LABELS = NOTICE_PERIODS.map((period) => period.label);

/** Exact equivalents only — ninety days is three months, six weeks is nothing on offer. */
const MONTHS_BY_UNIT: Record<NoticeUnit, Record<number, number>> = {
  MONTHS: { 0: 0, 1: 1, 2: 2, 3: 3, 6: 6 },
  WEEKS: { 0: 0, 4: 1, 8: 2, 12: 3, 26: 6 },
  DAYS: { 0: 0, 30: 1, 60: 2, 90: 3, 180: 6 },
};

/** The option a stored pair names, or null when it names none — which is what the escape hatch renders. */
export function noticePeriodOfPair(
  value: number | null | undefined,
  unit: NoticeUnit | null | undefined,
): string | null {
  if (value == null || unit == null) return null;
  const months = MONTHS_BY_UNIT[unit][value];
  if (months == null) return null;
  return NOTICE_PERIODS.find((period) => period.months === months)?.label ?? null;
}

export function pairOfNoticePeriod(label: string): { noticeValue: number; noticeUnit: NoticeUnit } | null {
  const period = NOTICE_PERIODS.find((option) => option.label === label);
  return period ? { noticeValue: period.months, noticeUnit: "MONTHS" } : null;
}

/** What a pair outside the vocabulary reads as — "6 weeks", "90 days" — so it can stay on offer. */
export function noticePairLabel(value: number, unit: NoticeUnit): string {
  return `${value} ${NOTICE_UNIT_LABELS[unit].toLowerCase()}`;
}

/** The compensation section's one-line summary. "None notice" is not a sentence. */
export function noticeSummaryOf(label: string | null): string | null {
  if (!label) return null;
  return label === "None" ? "No notice period" : `${label} notice`;
}
