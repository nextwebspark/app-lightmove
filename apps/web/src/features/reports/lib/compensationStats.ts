import type { CompensationBand, Disclosure, ReportRemuneration } from "../api/types";

export type CompensationMeasure = "package" | "fixed";

export const ALL_COUNTRIES = "All countries";
export const ALL_NATIONALITIES = "All nationalities";

/** Below this many disclosures a percentile is a coin toss, so the screen shows the points and no rank. */
export const MIN_DISCLOSURES_FOR_PERCENTILE = 5;

export interface CompensationFilter {
  measure: CompensationMeasure;
  country: string;
  nationality: string;
}

export interface CompensationStats {
  /** Null while the brief states no band: the points still show, nothing is ranked against them. */
  band: CompensationBand | null;
  measure: CompensationMeasure;
  disclosures: Disclosure[];
  isReliable: boolean;
  /** Percentile rank of the band ceiling among the disclosures; null without a band or below the floor. */
  ceilingPercentile: number | null;
  aboveBand: number;
  /** Executives who said no — the nearest thing to a declined offer the pipeline records today. */
  notInterested: number;
  notInterestedAboveBand: number;
  median: number;
  /** Axis bounds with room either side of both the band and every point. */
  axisLow: number;
  axisHigh: number;
}

export function measureValue(disclosure: Disclosure, measure: CompensationMeasure): number {
  return measure === "package" ? disclosure.totalPackage : disclosure.fixed;
}

export function bandFor(remuneration: ReportRemuneration, measure: CompensationMeasure): CompensationBand | null {
  return measure === "package" ? remuneration.packageBand : remuneration.fixedBand;
}

export function compensationStats(remuneration: ReportRemuneration, filter: CompensationFilter): CompensationStats {
  const band = bandFor(remuneration, filter.measure);
  const disclosures = remuneration.disclosures.filter(
    (d) =>
      (filter.country === ALL_COUNTRIES || d.country === filter.country) &&
      (filter.nationality === ALL_NATIONALITIES || d.nationality === filter.nationality),
  );
  const values = disclosures.map((d) => measureValue(d, filter.measure));
  const isReliable = disclosures.length >= MIN_DISCLOSURES_FOR_PERCENTILE;
  const notInterested = disclosures.filter((d) => d.status === "notInterested");
  const isAbove = (d: Disclosure) => band !== null && measureValue(d, filter.measure) > band.high;
  const low = Math.min(...values, band?.low ?? Number.POSITIVE_INFINITY);
  const high = Math.max(...values, band?.high ?? Number.NEGATIVE_INFINITY);
  const hasAxis = Number.isFinite(low) && Number.isFinite(high);
  const padding = hasAxis ? Math.max((high - low) * 0.08, 1) : 0;
  return {
    band,
    measure: filter.measure,
    disclosures,
    isReliable,
    ceilingPercentile: band !== null && isReliable ? percentileOf(values, band.high) : null,
    aboveBand: disclosures.filter(isAbove).length,
    notInterested: notInterested.length,
    notInterestedAboveBand: notInterested.filter(isAbove).length,
    median: values.length ? median(values) : 0,
    axisLow: hasAxis ? low - padding : 0,
    axisHigh: hasAxis ? high + padding : 1,
  };
}

/** Count-below rank, so "the ceiling sits at the 38th percentile" is computed rather than asserted. */
export function percentileOf(values: number[], x: number): number {
  if (values.length === 0) return 0;
  return Math.round((values.filter((v) => v < x).length / values.length) * 100);
}

export function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = sorted.length / 2;
  return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[Math.floor(mid)];
}

export interface NationalityGap {
  largestNationality: string;
  largestCount: number;
  restCount: number;
  largestMedian: number;
  restMedian: number;
  isReliable: boolean;
  higherSide: "largest" | "rest";
  gapPct: number;
}

/**
 * The largest nationality group against everyone else, on total package and independent of the
 * chapter's filters. Two buckets only: most single groups in a sixteen-disclosure set are one or two
 * people, and largest-vs-rest is the one split where both sides can clear the reliability floor.
 * Null when nobody disclosed with a nationality on file.
 */
export function nationalityGap(remuneration: ReportRemuneration): NationalityGap | null {
  const known = remuneration.disclosures.filter((d): d is Disclosure & { nationality: string } => d.nationality !== null);
  if (known.length === 0) return null;
  const counts = new Map<string, number>();
  known.forEach((d) => counts.set(d.nationality, (counts.get(d.nationality) ?? 0) + 1));
  const largestNationality = [...counts.entries()].reduce((a, b) => (b[1] > a[1] ? b : a))[0];
  const largest = known.filter((d) => d.nationality === largestNationality).map((d) => d.totalPackage);
  const rest = known.filter((d) => d.nationality !== largestNationality).map((d) => d.totalPackage);
  const largestMedian = median(largest);
  const restMedian = rest.length ? median(rest) : 0;
  const lower = Math.min(largestMedian, restMedian);
  return {
    largestNationality,
    largestCount: largest.length,
    restCount: rest.length,
    largestMedian,
    restMedian,
    isReliable: largest.length >= MIN_DISCLOSURES_FOR_PERCENTILE && rest.length >= MIN_DISCLOSURES_FOR_PERCENTILE,
    higherSide: restMedian > largestMedian ? "rest" : "largest",
    gapPct: lower === 0 ? 0 : Math.round((Math.abs(restMedian - largestMedian) / lower) * 100),
  };
}

export function countriesOf(remuneration: ReportRemuneration): string[] {
  return [ALL_COUNTRIES, ...new Set(remuneration.disclosures.flatMap((d) => (d.country ? [d.country] : [])))];
}

export function nationalitiesOf(remuneration: ReportRemuneration): string[] {
  return [ALL_NATIONALITIES, ...new Set(remuneration.disclosures.flatMap((d) => (d.nationality ? [d.nationality] : [])))];
}
