import type { CustomFieldValues } from "../../customcolumns/api/types";

import type { SeniorityToken } from "../../../lib/seniority";
/**
 * Where a mandate's research on an executive has got to. Not a shortlist flag: talent mapping records
 * the market as it is, so someone ruled out is kept with the reason rather than deleted — the map is
 * worth less if it only shows the people still in play.
 */
export type CandidateStatus =
  | "identified"
  | "contacted"
  | "engaged"
  | "interested"
  | "notInterested"
  | "offLimits"
  | "outOfScope";

/**
 * Distance from the chief executive, the axis a search brief is written in. The same job title means
 * different things in a family holding and a listed multinational; this does not. The two tiers above
 * the executive line are named rather than numbered, because a board seat is a different kind of role
 * and not a distance from the CEO.
 */
/** The shared ladder's wire token — see lib/seniority.ts. This contract speaks the label. */
export type CandidateSeniority = SeniorityToken;

/**
 * Gender as a researcher recorded it, for the report's diversity chapter. Never inferred from a
 * name, and `null` — nobody recorded it — is a different fact from `other`, which somebody did.
 */
export type CandidateGender = "female" | "male" | "other";

/** Which door a profile came through. Only `manual` is reachable today. */
export type CandidateSource = "manual" | "csv" | "extension";

/** One post in a career history. Free-text period, because that is the precision sources publish. */
export interface CandidateCareerEntry {
  company: string | null;
  title: string | null;
  period: string | null;
}

/** One school, shaped like a career post and written only by enrichment — no screen edits it yet. */
export interface CandidateEducationEntry {
  school: string | null;
  degree: string | null;
  period: string | null;
}

/**
 * A package as it was quoted, in the currency it was quoted in. Nothing converts it — a rate applied
 * at write time is wrong by the time anyone reads the row.
 */
export interface CandidateCompensation {
  currency: string | null;
  baseSalary: number | null;
  bonus: number | null;
  allowances: number | null;
  longTermIncentive: number | null;
  noticePeriod: string | null;
  /** The allowances itemised. When present they sum to `allowances`, which the server keeps agreeing. */
  allowanceLines: AllowanceLine[];
  /** What the long-term incentive is paid in; `none` is a recorded "no LTIP" and only ever alone. */
  longTermIncentiveTypes: LongTermIncentiveType[];
}

export interface AllowanceLine {
  label: string | null;
  amount: number | null;
}

export type LongTermIncentiveType = "options" | "rsus" | "cash" | "none";

/**
 * One executive mapped for a mandate.
 *
 * <p>`triageCompanyId` is the mandate's own company row, and it is null for someone whose employer is
 * not in the universe. `companyName` is carried either way: it is the employer snapshotted when the
 * row was written, so it renders identically after that company has been removed from the mandate.
 */
export interface Candidate {
  id: string;
  triageCompanyId: string | null;
  companyName: string | null;
  fullName: string;
  title: string | null;
  seniority: CandidateSeniority | null;
  status: CandidateStatus;
  linkedinUrl: string | null;
  locationCountry: string | null;
  locationCity: string | null;
  nationality: string | null;
  gender: CandidateGender | null;
  yearsExperience: number | null;
  summary: string | null;
  note: string | null;
  compensation: CandidateCompensation;
  career: CandidateCareerEntry[];
  languages: string[];
  /** Enrichment's, not the drawer's: empty until research has run, and never part of a save. */
  education: CandidateEducationEntry[];
  skills: string[];
  source: CandidateSource;
  sourceUrl: string | null;
  /** This mandate's own extra columns for this person, keyed by each column's `fieldKey`. */
  customFields: CustomFieldValues;
  addedAt: string;
  /** When enrichment last filled this profile in; null while research is pending or off. */
  enrichedAt: string | null;
  contacts: CandidateContacts;
}

/** Which door one contact value came through — the row's three doors plus the lookup provider. */
export type ContactSource = "manual" | "csv" | "extension" | "contactout";

/**
 * One address the mandate knows. `kind` and `verified` are only ever what the provider said — null
 * and false for anything a person typed, and for an address the provider listed without saying.
 */
export interface CandidateEmail {
  address: string;
  kind: "work" | "personal" | null;
  verified: boolean;
  status: string | null;
  source: ContactSource;
  foundAt: string;
}

/** One number the mandate knows, spelled as it arrived. `kind` only where a person tagged it. */
export interface CandidatePhone {
  number: string;
  kind: "work" | "personal" | null;
  verified: boolean;
  status: string | null;
  source: ContactSource;
  foundAt: string;
}

/** One email or phone as the Contact section or the Add form states it. */
export interface ContactEntryInput {
  value: string;
  kind: "work" | "personal" | null;
  verified: boolean;
}

/** The Contact section's save: both channels, replaced wholesale by what is listed. */
export interface SaveContactsPayload {
  emails: ContactEntryInput[];
  phones: ContactEntryInput[];
}

/**
 * Every email and phone known for the person, in the order the drawer lists them, and when each
 * channel was last looked up.
 *
 * <p>The timestamps decide what the Contact section offers, not the lists: null means the button is
 * still worth pressing, and a timestamp with no row from `source` means the provider had nothing and
 * asking again would only buy the same answer.
 */
export interface CandidateContacts {
  emails: CandidateEmail[];
  phones: CandidatePhone[];
  emailsLookedUpAt: string | null;
  phonesLookedUpAt: string | null;
  /** The provider that answered the lookups, for the "Found via" line. */
  source: string | null;
}

export interface CandidatesPage {
  candidates: Candidate[];
  totalCount: number;
  page: number;
  size: number;
}

/**
 * What the drawer submits, for both an add and an edit. The server replaces the whole profile with it,
 * so an omitted field is a cleared field — which is what the drawer means, because it holds every one.
 */
export interface SaveCandidatePayload {
  triageCompanyId?: string | null;
  fullName: string;
  title?: string;
  seniority?: CandidateSeniority;
  status?: CandidateStatus;
  /** Ignored by the server when `triageCompanyId` names one of the mandate's companies. */
  employerName?: string;
  /** The Add form's contacts. A profile edit never sends them: the Contact section has its own write. */
  emails?: ContactEntryInput[];
  phones?: ContactEntryInput[];
  linkedinUrl?: string;
  locationCountry?: string;
  locationCity?: string;
  nationality?: string;
  gender?: CandidateGender;
  yearsExperience?: number;
  summary?: string;
  note?: string;
  compensation?: Partial<CandidateCompensation>;
  career?: CandidateCareerEntry[];
  languages?: string[];
  /** Omitted leaves every custom column alone; a blank value clears that one column. */
  customFields?: CustomFieldValues;
}
