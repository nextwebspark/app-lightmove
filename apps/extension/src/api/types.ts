import type { CandidateCaptureStatus } from "../domain/candidateStatus";

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
 * The API's own `CaptureCompanyRequest`, which already exists for this plugin. It sends no `status`:
 * the server files a capture in universe, the one stage a capture may land in. Field names are the
 * server's; nothing here is invented, and the extension adds no endpoint of its own.
 */
export interface CaptureCompanyRequest {
  /** `extension` — the provenance the Companies screen shows. `strategy` is refused here. */
  source: "extension";
  companyName: string;
  companyLinkedinUrl?: string | null;
  /** The page this was read from. */
  sourceUrl?: string | null;
  note?: string | null;
}

/** The triage row the capture created. */
export interface TriagedCompany {
  id: string;
  source: "strategy" | "manual" | "extension";
  companyName: string;
}

/**
 * The body of `POST /projects/{id}/candidates`.
 *
 * The API's own `SaveCandidateRequest`, the same one the web app's Add-executive drawer posts. A strict
 * subset of its fields: the popup sends the name, the profile URL and the status the drawer's own
 * select offers, and leaves the rest — title, employer, career, contact — to the drawer and to later
 * enrichment. Nothing here is invented and the extension adds no endpoint.
 */
export interface SaveCandidateRequest {
  fullName: string;
  linkedinUrl?: string | null;
  note?: string | null;
  source: "extension";
  /**
   * Left out for the panel's starting choice, so the server files them at the workspace's own starting status:
   * a workspace may have retired the seeded Identified.
   */
  status?: CandidateCaptureStatus;
  sourceUrl?: string | null;
}

/** The candidate row the capture created. */
export interface CapturedCandidate {
  id: string;
  fullName: string;
  companyName: string | null;
  triageCompanyId: string | null;
}
