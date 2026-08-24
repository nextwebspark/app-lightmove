/** Where a company stands in a mandate's triage. There is no untriaged state — that has no row. */
export type TriageCompanyStatus = "inUniverse" | "shortlisted" | "declined";

/**
 * One company in a mandate's universe.
 *
 * <p>Every company field is the snapshot taken when it was added, not a live read of the universe.
 * That is deliberate: a triage decision has to keep rendering after the pipeline stops publishing
 * its subject.
 */
export interface TriageCompany {
  /** The triage row's id — what a status change addresses. Not the company's. */
  id: string;
  /** Null for a captured company the Apollo universe does not publish; `origin` says which. */
  apolloAccountId: string | null;
  /**
   * How the row arrived, and therefore how far its fields can be trusted. `STRATEGY` means the
   * snapshot was resolved from the Apollo universe; `CAPTURE` means the browser extension read it off
   * a page, and the team should know that before treating a headcount as a fact.
   */
  origin: "STRATEGY" | "CAPTURE";
  status: TriageCompanyStatus;
  note: string | null;
  companyName: string;
  industry: string | null;
  companyCountry: string | null;
  companyCity: string | null;
  numEmployees: number | null;
  annualRevenue: number | null;
  website: string | null;
  linkedinUrl: string | null;
  logoUrl: string | null;
  /** The page a capture was read from. Null for a company Strategy took out of the universe. */
  sourceUrl: string | null;
  tags: string[] | null;
}

/** The status sub-nav's badge counts. */
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
  /** Travels with every page, because the sub-nav is visible on all of them. */
  counts: TriageCounts;
}

/** What "Add all to Universe" did. Never partial — an oversized filter is refused, not truncated. */
export interface BulkAddResult {
  added: number;
  /** Companies the mandate already held, declined ones included. */
  skipped: number;
}
