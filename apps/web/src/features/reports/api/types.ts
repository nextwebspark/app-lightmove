/** One labelled slice of the scoped company universe — a sector, a country, a city. */
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
 * Where the report's measurement is narrower than the mandate's scope.
 *
 * <p>Two earlier caveats are gone, and both because the product has one universe instead of two: the
 * off-limits bar was unenforceable when the report and triage read different tables, and a selected
 * sector could be absent from the report's source entirely. Neither can happen now.
 */
export interface ScopeCaveats {
  /**
   * True when a revenue band is selected without Unknown beside it. The universe publishes a revenue
   * figure on roughly a tenth of its rows, so such a report measures a tenth of the market.
   */
  revenueBandExcludesUnknown: boolean;
}

/**
 * A mandate's report. Carries only what the project itself does not: the position title, client,
 * stage, target date and team seats all arrive with `Project`, and the screen reads them from there.
 */
export interface Report {
  universeCount: number;
  offLimitsCompanies: number;
  sectorsInScope: number;
  marketsInScope: number;
  sectors: Breakdown[];
  countries: Breakdown[];
  cities: Breakdown[];
  mandateBand: CompensationBand | null;
  caveats: ScopeCaveats;
}
