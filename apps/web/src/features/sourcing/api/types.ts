/** Which scope bucket a company matched through — a direct sector, an adjacent sector, or a tag.
 *  (Targets are surfaced in the universe, never in the Sourcing list, so there is no target tier.) */
export type MatchTier = "DIRECT" | "ADJACENT" | "INFERRED";

/** One company matching the project's saved Strategy scope. */
export interface CompanyResult {
  id: number;
  name: string;
  domain: string | null;
  sector: string | null;
  employeeRange: string | null;
  revenueRange: string | null;
  location: string;
  matchTier: MatchTier;
}

/**
 * Which scope categories this query actually filtered on. Every returned company already satisfies
 * each `true` category (the query ANDs them together) — this drives which checkmark rows a card shows,
 * not a per-company fit score.
 */
export interface AppliedFilters {
  sector: boolean;
  employee: boolean;
  revenue: boolean;
  geography: boolean;
}

export interface SourcingResponse {
  companies: CompanyResult[];
  totalCount: number;
  page: number;
  size: number;
  appliedFilters: AppliedFilters;
}

// ── CoreSignal run flow (POC) ────────────────────────────────────────────────

/** PENDING/SEARCHING/COLLECTING are the polling window; READY and FAILED are terminal. */
export type RunStatus = "PENDING" | "SEARCHING" | "COLLECTING" | "READY" | "FAILED";

/** One company collected from CoreSignal — card and detail drawer share this one shape. */
export interface SourcedCompany {
  coresignalId: number;
  name: string;
  website: string | null;
  linkedinUrl: string | null;
  logoUrl: string | null;
  industry: string | null;
  sizeRange: string | null;
  employeesCount: number | null;
  revenueRange: string | null;
  revenueAnnualUsd: number | null;
  location: string | null;
  country: string | null;
  foundedYear: number | null;
  description: string | null;
  matchTier: MatchTier;
}

/**
 * A poll of the current run. `companies` grows toward `requestedCount` during COLLECTING, already
 * in the provider's revenue-desc order — new results append/fill, they never reshuffle.
 * `criteriaMatchesStrategy` false means the results describe an older scope: start a fresh run.
 */
export interface SourcingRun {
  status: RunStatus;
  requestedCount: number;
  collectedCount: number;
  /** How many ids the search kept — `requestedCount < searchedCount` means more can be loaded. */
  searchedCount: number;
  totalMatched: number;
  criteriaMatchesStrategy: boolean;
  error: string | null;
  companies: SourcedCompany[];
}

/** `run` is null when this project has never sourced from CoreSignal — the page auto-starts one. */
export interface SourcingRunResponse {
  run: SourcingRun | null;
}
