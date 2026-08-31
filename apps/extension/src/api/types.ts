import type { TriageDestination } from "../domain/triageDestination";

/**
 * The API payloads the extension touches, and nothing more.
 *
 * Deliberately narrower than the web app's equivalents: the popup renders a company and a project
 * picker, so it declares the fields those need and ignores the rest of what the endpoints return.
 * A type here is a description of what this client uses, not a mirror of the server's DTO.
 */

/** RFC 9457. The popup switches on `code` and never on `detail`, exactly as the web app does. */
export interface ApiError {
  code: string;
  detail: string;
  status: number;
}

export interface WorkspaceUser {
  id: string;
  fullName: string;
  email: string;
}

/** What `POST /auth/extension/tokens` and `/auth/extension/refresh` both answer with. */
export interface ExtensionSession {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  user: WorkspaceUser;
}

/** One entry in the popup's project dropdown, from `GET /projects`. */
export interface ProjectSummary {
  id: string;
  /** The mandate's role — the server calls it this, and there is no `name` on `ProjectResponse`. */
  positionTitle: string;
  clientName: string | null;
}

/**
 * The body of `POST /projects/{id}/triage/capture`.
 *
 * The API's own `CaptureCompanyRequest`, which already exists for this plugin — the endpoint was built
 * for the two destination buttons in `Extension.dc.html`. Field names are the server's; nothing here
 * is invented, and the extension adds no endpoint of its own.
 */
export interface CaptureCompanyRequest {
  /** `extension` — the provenance the Companies screen shows. `strategy` is refused here. */
  source: "extension";
  /** The landing stage, from whichever destination button was pressed. */
  status: TriageDestination;
  companyName: string;
  industry?: string | null;
  companyCountry?: string | null;
  companyCity?: string | null;
  numEmployees?: number | null;
  annualRevenue?: number | null;
  foundedYear?: number | null;
  website?: string | null;
  companyLinkedinUrl?: string | null;
  shortDescription?: string | null;
  /** The page this was read from. */
  sourceUrl?: string | null;
  note?: string | null;
}

/** The triage row the capture created. */
export interface TriagedCompany {
  id: string;
  source: "strategy" | "manual" | "extension";
  status: TriageDestination | "declined";
  companyName: string;
}

/** The seniority tokens the API speaks — `Seniority.value()`, not the enum names. */
export const CANDIDATE_SENIORITIES = ["Board", "C-Suite", "N-1", "N-2", "N-3"] as const;
export type CandidateSeniority = (typeof CANDIDATE_SENIORITIES)[number];

/** One role before the current one, as `CandidateCareerEntryDto` takes it. */
export interface CandidateCareerEntry {
  company?: string | null;
  title?: string | null;
  /** Free text: "2016 – 2021", "c. 2015". */
  period?: string | null;
}

/**
 * The body of `POST /projects/{id}/candidates`.
 *
 * The API's own `SaveCandidateRequest`, the same one the web app's Add-executive drawer posts. A strict
 * subset of its fields: the popup sends what a page can honestly yield and leaves the rest — nationality,
 * compensation, languages — to the drawer. Nothing here is invented and the extension adds no endpoint.
 */
export interface SaveCandidateRequest {
  /** Set only when the employer matched a company the mandate already triaged; never with employerName. */
  triageCompanyId?: string | null;
  fullName: string;
  title?: string | null;
  seniority?: CandidateSeniority | null;
  /** `offLimits` when the toggle is on; omitted otherwise, so the server's `identified` stands. */
  status?: "offLimits" | null;
  /** Ignored by the server when triageCompanyId is present, so the two are never sent together. */
  employerName?: string | null;
  email?: string | null;
  phone?: string | null;
  linkedinUrl?: string | null;
  locationCountry?: string | null;
  locationCity?: string | null;
  note?: string | null;
  career?: CandidateCareerEntry[];
  source: "extension";
  sourceUrl?: string | null;
}

/** The candidate row the capture created. */
export interface CapturedCandidate {
  id: string;
  fullName: string;
  companyName: string | null;
  triageCompanyId: string | null;
}

/** A company the mandate already holds, as the auto-link needs it. */
export interface TriageCompanyMatch {
  id: string;
  companyName: string;
  status: TriageDestination | "declined";
}
