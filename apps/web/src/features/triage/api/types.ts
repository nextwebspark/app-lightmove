import type { AddedBy, Candidate } from "../../candidates/api/types";
import type { CustomFieldValues } from "../../customcolumns/api/types";

/** Where a company stands in a mandate's triage. There is no untriaged state — that has no row. */
export type TriageCompanyStatus = "inUniverse" | "shortlisted" | "declined";

/**
 * Which door a company came through. Provenance the grid shows rather than bookkeeping: a headcount
 * exported by Apollo and one typed off a careers page are not equally trustworthy, and only a
 * `strategy` company has a universe id to link back to.
 */
export type TriageCompanySource = "strategy" | "manual" | "extension" | "csv" | "assistant" | "people_search";

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
  /** How many notes the team holds on it; null on every read but the staff grid's. */
  noteCount: number | null;
  /** Set once the mandate has looked and concluded nobody here fits. Orthogonal to `status`. */
  noExecutiveFound: boolean;
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
  /** This mandate's own extra columns for this company, keyed by each column's `fieldKey`. */
  customFields: CustomFieldValues;
  addedAt: string;
  /** Who filed the company into this mandate. Staff reads only; a client seat's answers null for all three. */
  addedByUserId?: string | null;
  addedByName?: string | null;
  addedByAvatarUrl?: string | null;
}

/** The sidebar's badge counts for the three Companies stages. */
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
  /** Travels with every page, because the sidebar's badges are visible on all of them. */
  counts: TriageCounts;
}

/** What "Add all to Universe" did. Never partial — an oversized filter is refused, not truncated. */
export interface BulkAddResult {
  added: number;
  /** Companies the mandate already held, declined ones included. */
  skipped: number;
}

/**
 * The columns a Companies grid can be sorted by — each the column's own id, which is also the server's sort token.
 * The grid orders its lines, so a person's own fields sort by the person whatever company they sit at; a mandate's
 * own column sorts as `custom:<target>:<fieldKey>`.
 */
export type TriageSortField =
  | "name"
  | "executive"
  | "executiveTitle"
  | "country"
  | "location"
  | "executiveStatus"
  | "sector"
  | "revenue"
  | "employees"
  | "founded"
  | "added"
  | "addedBy"
  | "note"
  | "source"
  | `custom:${string}`;

/** One grid line: a person at a company — `candidate` null on a company nobody is listed at yet, `company` null on an
 *  executive whose employer is not in the mandate. */
export interface StageLine {
  company: TriageCompany | null;
  candidate: Candidate | null;
}

/** One page of a stage's lines, ordered across the whole stage. */
export interface StageLinesPage {
  lines: StageLine[];
  /** Who filed each executive, by candidate id: a staff read's alone, null for a client seat. */
  addedBy: Record<string, AddedBy> | null;
  totalCount: number;
  page: number;
  size: number;
  counts: TriageCounts;
  /** The stage ran past what one read orders, so the order covers its first companies by name. */
  truncated: boolean;
}

/**
 * A company the mandate supplies itself — typed into the Add company form, or read off a live page by
 * the browser plugin.
 *
 * <p>Only the name is required. The plugin reads whatever a page happens to publish and a researcher
 * may have a name and a country and nothing else; refusing the row until it is complete would push
 * the consultant back to a spreadsheet, which is what these screens exist to replace.
 */
/**
 * A company's own facts, replaced whole — what the Companies panel's Edit form submits.
 *
 * <p>Only for a company the mandate supplied itself. A row taken from Strategy carries the market
 * export's snapshot and the server refuses to rewrite it, which is why `apolloAccountId === null` is
 * also what decides whether the panel offers an Edit button at all.
 *
 * <p>No `note`: that is the mandate's remark rather than a fact about the company, it stays editable
 * on the very companies this refuses, and it has its own write.
 */
export interface EditCompanyPayload {
  companyName: string;
  industry?: string;
  companyCountry?: string;
  companyCity?: string;
  numEmployees?: number;
  annualRevenue?: number;
  foundedYear?: number;
  website?: string;
  companyLinkedinUrl?: string;
  shortDescription?: string;
  /** Omitted leaves every custom column alone; a blank value clears that one column. */
  customFields?: CustomFieldValues;
}

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
  /** Omitted leaves every custom column alone; a blank value clears that one column. */
  customFields?: CustomFieldValues;
}
