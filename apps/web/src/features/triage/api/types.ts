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
  apolloAccountId: string;
  status: TriageCompanyStatus;
  note: string | null;
  companyName: string;
  industry: string | null;
  companyCountry: string | null;
  companyCity: string | null;
  numEmployees: number | null;
  annualRevenue: number | null;
  website: string | null;
  logoUrl: string | null;
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
