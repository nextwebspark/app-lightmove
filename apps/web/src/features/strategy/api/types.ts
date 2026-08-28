/** One offerable value: what a filter stores, and what the control reading it says. */
export interface FacetOption {
  /** What a saved filter stores. A slug for bands, the value itself for industries and countries. */
  value: string;
  /** What the chip reads. Presentation only — never stored, so relabelling breaks nothing. */
  label: string;
}

/** A facet value counted over the universe — the count is what makes it worth clicking. */
export interface FacetCount extends FacetOption {
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
 * Everything the filter sidebar counts, in one read. Counts are over the whole universe rather than
 * the current selection, so this is the same for every mandate and no chip click invalidates it.
 *
 * <p>Location is not here: its vocabulary is the six fixed GCC markets in {@link GCC_COUNTRIES}, so
 * the panel offers them without waiting on this read at all.
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
  employeeBands: FacetCount[];
  revenueBands: FacetCount[];
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

/** Who a saved search is for: one person's scratch list, or the mandate's. */
export type SearchVisibility = "PRIVATE" | "SHARED";

/**
 * A named filter a mandate saved. Frozen at save time — editing the sidebar does not follow it, and
 * re-capturing the current filter onto it is an explicit act.
 *
 * A PRIVATE search never reaches anyone but its author, so `createdById` on a row in this list is
 * either the viewer or someone who chose to share.
 */
export interface SavedSearch {
  id: string;
  name: string;
  filter: StrategyFilter;
  visibility: SearchVisibility;
  createdById: string;
  createdByName: string | null;
  createdAt: string;
  /** Moves when the search is renamed or re-captured; this is the date the row shows. */
  updatedAt: string;
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

