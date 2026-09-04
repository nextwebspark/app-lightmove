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

/** Which door a profile came through. Only `manual` is reachable today. */
export type CandidateSource = "manual" | "csv" | "extension";

/** One post in a career history. Free-text period, because that is the precision sources publish. */
export interface CandidateCareerEntry {
  company: string | null;
  title: string | null;
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
}

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
  email: string | null;
  phone: string | null;
  linkedinUrl: string | null;
  locationCountry: string | null;
  locationCity: string | null;
  nationality: string | null;
  yearsExperience: number | null;
  summary: string | null;
  note: string | null;
  compensation: CandidateCompensation;
  career: CandidateCareerEntry[];
  languages: string[];
  source: CandidateSource;
  sourceUrl: string | null;
  /** This mandate's own extra columns for this person, keyed by each column's `fieldKey`. */
  customFields: CustomFieldValues;
  addedAt: string;
  /** When enrichment last filled this profile in; null while research is pending or off. */
  enrichedAt: string | null;
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
  email?: string;
  phone?: string;
  linkedinUrl?: string;
  locationCountry?: string;
  locationCity?: string;
  nationality?: string;
  yearsExperience?: number;
  summary?: string;
  note?: string;
  compensation?: Partial<CandidateCompensation>;
  career?: CandidateCareerEntry[];
  languages?: string[];
  /** Omitted leaves every custom column alone; a blank value clears that one column. */
  customFields?: CustomFieldValues;
}
