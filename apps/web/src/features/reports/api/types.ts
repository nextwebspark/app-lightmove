/** One labelled slice of the scoped company universe — a sector, a country, a city, a match tier. */
export interface Breakdown {
  label: string;
  count: number;
}

/** The band the position brief asks for. Null on the report until the brief states one. */
export interface CompensationBand {
  min: number | null;
  max: number | null;
  currency: string;
}

/**
 * A mandate's report. Carries only what the project itself does not: the position title, client,
 * stage, target date and team seats all arrive with `Project`, and the screen reads them from there.
 */
export interface Report {
  universeCount: number;
  targetCompanies: number;
  offLimitsCompanies: number;
  sectorsInScope: number;
  marketsInScope: number;
  /** DIRECT, then ADJACENT, then INFERRED — a strength ladder, so the server fixes the order. */
  relevance: Breakdown[];
  sectors: Breakdown[];
  countries: Breakdown[];
  cities: Breakdown[];
  mandateBand: CompensationBand | null;
}
