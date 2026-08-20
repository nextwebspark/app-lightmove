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
  /** Rolled up across `industries`, so the header states the slice without the client summing it. */
  count: number;
  industries: FacetCount[];
}

/**
 * Everything the filter sidebar renders, in one read. Counts are over the whole universe rather than
 * the current selection, so this is the same for every mandate and no chip click invalidates it.
 */
export interface Facets {
  sectorGroups: SectorGroup[];
  /** Overlapping by design — a company can be B2B and SaaS at once. */
  marketSegments: FacetCount[];
  countries: FacetCount[];
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
 * data, and the cell says unknown rather than showing a zero.
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
  linkedinUrl: string | null;
  logoUrl: string | null;
  shortDescription: string | null;
  foundedYear: number | null;
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

/** What "Add all to Universe" did. `capped` means the filter matched more than `limit`. */
export interface BulkAddResult {
  added: number;
  skipped: number;
  capped: boolean;
  limit: number;
}
