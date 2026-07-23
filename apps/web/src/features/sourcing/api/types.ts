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
