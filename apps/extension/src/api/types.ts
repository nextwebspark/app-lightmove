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
  fieldErrors?: Record<string, string>;
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
  name: string;
  clientName: string | null;
}

/** A company the Apollo universe publishes, from `GET /companies/resolve`. */
export interface CompanySuggestion {
  apolloAccountId: string;
  companyName: string;
  industry: string | null;
  companyCity: string | null;
  companyCountry: string | null;
  website: string | null;
  logoUrl: string | null;
  numEmployees: number | null;
}

export interface CompanyMatch {
  matched: boolean;
  company: CompanySuggestion | null;
}

/** The body of `POST /projects/{id}/triage/captures`. */
export interface CaptureCompanyRequest {
  status: TriageDestination;
  apolloAccountId?: string | null;
  companyName: string;
  website?: string | null;
  linkedinUrl?: string | null;
  industry?: string | null;
  companyCountry?: string | null;
  companyCity?: string | null;
  numEmployees?: number | null;
  annualRevenue?: number | null;
  tags?: string[];
  note?: string | null;
  sourceUrl?: string | null;
}

/** The triage row the capture created or promoted. */
export interface TriagedCompany {
  id: string;
  apolloAccountId: string | null;
  status: TriageDestination | "declined";
  companyName: string;
  origin: "STRATEGY" | "CAPTURE";
}
