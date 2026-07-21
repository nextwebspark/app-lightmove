/** Which scope bucket a company matched through — a direct sector, an adjacent sector, a tag, or a
 *  manually-seeded target (which bypasses the other criteria entirely). */
export type MatchTier = "DIRECT" | "ADJACENT" | "INFERRED" | "TARGET";

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
