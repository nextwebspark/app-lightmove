/** Which scope bucket a company matched through — a direct sector, an adjacent sector, or a tag.
 *  (Targets are surfaced in the universe, never in the Sourcing list, so there is no target tier.) */
export type MatchTier = "DIRECT" | "ADJACENT" | "INFERRED";

/**
 * One company matching the project's saved Strategy scope. Wider than any one user's table: the column
 * picker decides which of these are rendered, and the server sends them all rather than the response
 * shape depending on a client-side preference.
 */
export interface CompanyResult {
  id: number;
  name: string;
  domain: string | null;
  website: string | null;
  linkedinUrl: string | null;
  logo: string | null;
  slogan: string | null;
  description: string | null;
  sector: string | null;
  industryTags: string[];
  specialties: string[];
  country: string | null;
  location: string;
  employeeRange: string | null;
  revenueRange: string | null;
  founded: number | null;
  ownership: string | null;
  ipoStatus: string | null;
  orgType: string | null;
  matchTier: MatchTier;
}

/** Which column the list is ordered by — mirror of the backend CompanySortField enum. */
export type SourcingSortField =
  | "name"
  | "tier"
  | "sector"
  | "employees"
  | "revenue"
  | "location"
  | "founded"
  | "country";

/** Mirror of the backend SortDirection enum. */
export type SortDirection = "asc" | "desc";

/** A chosen column sort, or `null` for the default match-tier order. */
export interface SourcingSort {
  field: SourcingSortField;
  direction: SortDirection;
}

export interface SourcingResponse {
  companies: CompanyResult[];
  totalCount: number;
  page: number;
  size: number;
}
