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
  band: CompensationBand;
  measure: CompensationMeasure;
  disclosures: Disclosure[];
  values: number[];
  isReliable: boolean;
  /** Percentile rank of the band ceiling among the disclosures; null below the reliability floor. */
  ceilingPercentile: number | null;
  aboveBand: number;
  declined: number;
  declinedAboveBand: number;
  median: number;
  /** Axis bounds rounded out to the nearest 50 so the band and every point sit inside with room. */
  axisLowK: number;
  axisHighK: number;
}

export function measureValue(disclosure: Disclosure, measure: CompensationMeasure): number {
  return measure === "package" ? disclosure.packageK : disclosure.fixedK;
}

export function compensationStats(
  remuneration: ReportRemuneration,
  filter: CompensationFilter,
): CompensationStats {
  const band = filter.measure === "package" ? remuneration.packageBand : remuneration.fixedBand;
  const disclosures = remuneration.disclosures.filter(
    (d) =>
      (filter.country === ALL_COUNTRIES || d.country === filter.country) &&
      (filter.nationality === ALL_NATIONALITIES || d.nationality === filter.nationality),
  );
  const values = disclosures.map((d) => measureValue(d, filter.measure));
  const isReliable = disclosures.length >= MIN_DISCLOSURES_FOR_PERCENTILE;
  const declined = disclosures.filter((d) => d.outcome === "declined");
  const above = (d: Disclosure) => measureValue(d, filter.measure) > band.highK;
  const dataLow = values.length ? Math.min(...values) : band.lowK;
  const dataHigh = values.length ? Math.max(...values) : band.highK;
  return {
    band,
    measure: filter.measure,
    disclosures,
    values,
    isReliable,
    ceilingPercentile: isReliable ? percentileOf(values, band.highK) : null,
    aboveBand: disclosures.filter(above).length,
    declined: declined.length,
    declinedAboveBand: declined.filter(above).length,
    median: values.length ? median(values) : 0,
    axisLowK: Math.floor((Math.min(dataLow, band.lowK) - 60) / 50) * 50,
    axisHighK: Math.ceil((Math.max(dataHigh, band.highK) + 60) / 50) * 50,
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
  /** Which side commands the premium. */
  higherSide: "largest" | "rest";
  gapPct: number;
}

/**
 * The largest nationality group against everyone else, on total package and independent of the
 * chapter's filters. Two buckets only: most single groups in a sixteen-disclosure set are one or two
 * people, and largest-vs-rest is the one split where both sides can clear the reliability floor.
 */
export function nationalityGap(remuneration: ReportRemuneration): NationalityGap {
  const counts = new Map<string, number>();
  remuneration.disclosures.forEach((d) => counts.set(d.nationality, (counts.get(d.nationality) ?? 0) + 1));
  const largestNationality = [...counts.entries()].reduce((a, b) => (b[1] > a[1] ? b : a))[0];
  const largest = remuneration.disclosures.filter((d) => d.nationality === largestNationality).map((d) => d.packageK);
  const rest = remuneration.disclosures.filter((d) => d.nationality !== largestNationality).map((d) => d.packageK);
  const largestMedian = largest.length ? median(largest) : 0;
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
  return [ALL_COUNTRIES, ...new Set(remuneration.disclosures.map((d) => d.country))];
}

export function nationalitiesOf(remuneration: ReportRemuneration): string[] {
  return [ALL_NATIONALITIES, ...new Set(remuneration.disclosures.map((d) => d.nationality))];
}
