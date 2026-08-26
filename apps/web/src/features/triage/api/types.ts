/** Where a company stands in a mandate's triage. There is no untriaged state — that has no row. */
export type TriageCompanyStatus = "inUniverse" | "shortlisted" | "declined";

/**
 * Which door a company came through. Provenance the grid shows rather than bookkeeping: a headcount
 * exported by Apollo and one typed off a careers page are not equally trustworthy, and only a
 * `strategy` company has a universe id to link back to.
 */
export type TriageCompanySource = "strategy" | "manual" | "extension";

/**
 * One company in a mandate's universe.
 *
 * <p>Every company field is the snapshot taken when it was added, not a live read of the universe.
 * That is deliberate: a triage decision has to keep rendering after the pipeline stops publishing
 * its subject.
 */
export interface TriageCompany {
  /** The triage row's id — what a status change, a note and a delete all address. Not the company's. */
  id: string;
  /** Null for a company the mandate supplied itself; there is no universe row to point at. */
  apolloAccountId: string | null;
  source: TriageCompanySource;
  status: TriageCompanyStatus;
  note: string | null;
  companyName: string;
  industry: string | null;
  companyCountry: string | null;
  companyCity: string | null;
  numEmployees: number | null;
  annualRevenue: number | null;
  website: string | null;
  companyLinkedinUrl: string | null;
  foundedYear: number | null;
  shortDescription: string | null;
  /** The page the plugin captured this from. Null for every other source. */
  sourceUrl: string | null;
  logoUrl: string | null;
  addedAt: string;
}

/** The stage switcher's badge counts. */
export interface TriageCounts {
  inUniverse: number;
  shortlisted: number;
  declined: number;
}

export interface TriageCompaniesPage {
  companies: TriageCompany[];
  totalCount: number;
  page: number;
  size: number;
  /** Travels with every page, because the switcher is visible on all of them. */
  counts: TriageCounts;
}

/** What "Add all to Universe" did. Never partial — an oversized filter is refused, not truncated. */
export interface BulkAddResult {
  added: number;
  /** Companies the mandate already held, declined ones included. */
  skipped: number;
}

/** The columns a Companies grid can be sorted by. Mirrors the server's allowlist token for token. */
export type TriageSortField =
  | "name"
  | "sector"
  | "country"
  | "location"
  | "employees"
  | "revenue"
  | "founded"
  | "added";

/**
 * A company the mandate supplies itself — typed into the Add company form, or read off a live page by
 * the browser plugin.
 *
 * <p>Only the name is required. The plugin reads whatever a page happens to publish and a researcher
 * may have a name and a country and nothing else; refusing the row until it is complete would push
 * the consultant back to a spreadsheet, which is what these screens exist to replace.
 */
export interface CaptureCompanyPayload {
  companyName: string;
  source?: Exclude<TriageCompanySource, "strategy">;
  /** The landing stage. Omitted, a capture lands in universe like everything else. */
  status?: TriageCompanyStatus;
  industry?: string;
  companyCountry?: string;
  companyCity?: string;
  numEmployees?: number;
  annualRevenue?: number;
  foundedYear?: number;
  website?: string;
  companyLinkedinUrl?: string;
  shortDescription?: string;
  sourceUrl?: string;
  note?: string;
}
