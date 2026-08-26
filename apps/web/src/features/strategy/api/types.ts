/** One selectable value in a filter accordion, with the count that makes it worth clicking. */
export interface FacetCount {
  /** What a saved filter stores. A slug for bands, the value itself for industries and countries. */
  value: string;
  /** What the chip reads. Presentation only — never stored, so relabelling breaks nothing. */
  label: string;
  count: number;
}

/**
 * One sector group with its industries. The universe's `industry` column is 148 flat labels; the
 * grouping is the API's, and clicking a group selects every leaf under it.
 */
export interface SectorGroup {
  name: string;
  industries: FacetCount[];
}

/**
 * The shape of the market: what every accordion can offer, the order it renders in, and how big each
 * option is across the whole universe. The same for every mandate and stable until the pipeline next
 * loads, which is why no chip click invalidates it.
 *
 * The numbers the sidebar actually shows are `FacetCounts`, which follow the selection. Order stays
 * here so a row cannot re-rank itself under the hand that just clicked the row above it.
 */
export interface Facets {
  sectorGroups: SectorGroup[];
  /**
   * Which industries sit beside which — the panel's suggestion chips. Advice rather than a facet, so
   * no counts: a name the taxonomy no longer holds renders no chip rather than one selecting nothing.
   */
  adjacentIndustries: Record<string, string[]>;
  /** Overlapping by design — a company can be B2B and SaaS at once. */
  marketSegments: FacetCount[];
  countries: FacetCount[];
  employeeBands: FacetCount[];
  revenueBands: FacetCount[];
}

/**
 * A facet row as the sidebar renders it. The count is optional because it is not always knowable: a
 * refused counts read shows no number rather than the universe total, which would silently answer a
 * question nobody asked.
 */
export interface FacetOption extends Omit<FacetCount, "count"> {
  count?: number;
}

/**
 * How many companies each option still reaches under the current selection, keyed by the token a
 * saved filter stores.
 *
 * Each axis is counted with every criterion applied **except its own** — picking a country recounts
 * the industries under it and leaves the other countries countable, where applying everything would
 * read zero for every country but the chosen one.
 *
 * **An option absent from a map counts zero.** The vocabulary lives in `Facets`, which the sidebar
 * already renders from, so repeating it here to carry a zero would only let the two disagree.
 */
export interface FacetCounts {
  industries: Record<string, number>;
  countries: Record<string, number>;
  employeeBands: Record<string, number>;
  revenueBands: Record<string, number>;
  marketSegments: Record<string, number>;
}

/**
 * A Custom Range on one numeric axis. Either end may be null — "at least 500" is a legal thing to
 * ask for — and a range with neither end set is normalised away server-side.
 */
export interface NumericRange {
  min: number | null;
  max: number | null;
}

/**
 * The whole sidebar selection. Every list holds wire values, never labels, and never group names —
 * a group is expanded to its industries before it is stored.
 *
 * A non-null range *is* Custom Range mode for its axis, and overrides that axis's band list. There
 * is no separate mode flag, so the two can never disagree about which is in force.
 */
export interface StrategyFilter {
  industries: string[];
  keywords: string[];
  marketSegments: string[];
  countries: string[];
  employeeBands: string[];
  revenueBands: string[];
  employeeRange: NumericRange | null;
  revenueRange: NumericRange | null;
}

/** One entry on the off-limits list: its identity plus the snapshot taken when it was barred. */
export interface CompanyRef {
  apolloAccountId: string;
  companyName: string;
  industry: string | null;
  companyCity: string | null;
  companyCountry: string | null;
  logoUrl: string | null;
}

/** A named filter a mandate saved. Frozen at save time — editing the sidebar does not follow it. */
export interface SavedSearch {
  id: string;
  name: string;
  filter: StrategyFilter;
  createdAt: string;
}

/** Everything the screen needs before it draws. */
export interface Strategy {
  filter: StrategyFilter;
  offLimits: CompanyRef[];
  searches: SavedSearch[];
}

/**
 * One row of the results table. `annualRevenue` is null on roughly nine rows in ten — that is the
 * data, and the cell says unknown rather than showing a zero. The funding fields are sparser still.
 *
 * Every field arrives whether or not the user has that column switched on: the visible set is a
 * local preference, and making the response depend on it would put UI state in the query key.
 */
export interface CompanyResult {
  apolloAccountId: string;
  companyName: string;
  industry: string | null;
  companyCountry: string | null;
  companyCity: string | null;
  numEmployees: number | null;
  annualRevenue: number | null;
  website: string | null;
  logoUrl: string | null;
  shortDescription: string | null;
  foundedYear: number | null;
  companyLinkedinUrl: string | null;
  facebookUrl: string | null;
  twitterUrl: string | null;
  companyPhone: string | null;
  companyState: string | null;
  companyAddress: string | null;
  parentCompany: string | null;
  totalFunding: number | null;
  latestFunding: string | null;
  latestFundingAmount: number | null;
  /** `YYYY-MM-DD`, not a timestamp. */
  lastRaisedAt: string | null;
  numberOfRetailLocations: number | null;
  keywords: string[];
  technologies: string[];
  sicCodes: string[];
  naicsCodes: string[];
}

export interface CompanyPage {
  companies: CompanyResult[];
  /** Over the whole filter, not the page — what the pagination bar states. */
  totalCount: number;
  page: number;
  size: number;
}

/** A company offered by a picker's typeahead. */
export interface CompanySuggestion {
  apolloAccountId: string;
  companyName: string;
  industry: string | null;
  companyCity: string | null;
  companyCountry: string | null;
  website: string | null;
  logoUrl: string | null;
  numEmployees: number | null;
}

/** The columns the results table can sort by. These tokens are the backend's allowlist. */
export type CompanySortField =
  | "name"
  | "sector"
  | "country"
  | "location"
  | "employees"
  | "revenue"
  | "founded";

export type SortDirection = "asc" | "desc";

export interface CompanySort {
  field: CompanySortField;
  direction: SortDirection;
}

