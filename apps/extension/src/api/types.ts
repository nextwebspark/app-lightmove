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
  /** The workspace the extension is paired into; everything it files lands there. */
  workspace?: { id: string; name: string } | null;
}

/** What `POST /auth/extension/tokens` and `/auth/extension/refresh` both answer with. */
export interface ExtensionSession {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  user: WorkspaceUser;
}

/** A seat on a position's team, as `GET /projects` lists it: who a note may mention. */
export interface TeamMember {
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  projectRoles: string[];
}

/** One position the panel can file into, from `GET /projects`. */
export interface ProjectSummary {
  id: string;
  /** The mandate's role — the server calls it this, and there is no `name` on `ProjectResponse`. */
  positionTitle: string;
  clientName: string | null;
  clientLogoUrl: string | null;
  team: TeamMember[];
  /** Set once the position is archived; the panel files into live positions only. */
  archivedAt: string | null;
}

/**
 * The body of `POST /projects/{id}/triage/capture`.
 *
 * The API's own `CaptureCompanyRequest`, built for this plugin. Field names are the server's; nothing here is
 * invented.
 */
export interface CaptureCompanyRequest {
  /** `extension` — the provenance the Companies screen shows. `strategy` is refused here. */
  source: "extension";
  /** Always the universe: the panel files a company there and nowhere else. */
  status: TriageDestination;
  companyName: string;
  companyLinkedinUrl?: string | null;
  /** The page this was read from. */
  sourceUrl?: string | null;
  note?: string | null;
}

/** A company of the position, as the capture creates it or the held lookup finds it. */
export interface TriagedCompany {
  id: string;
  source: string;
  status: "inUniverse" | "shortlisted" | "declined";
  companyName: string;
  industry: string | null;
  companyCity: string | null;
  companyCountry: string | null;
  companyLinkedinUrl: string | null;
  logoUrl: string | null;
  addedAt: string | null;
  /** Who filed it; a staff read's alone, which every read the panel makes is. */
  addedByName: string | null;
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
  status?: string;
  sourceUrl?: string | null;
}

/** An executive of the position, as the capture creates it or a read answers it. */
export interface CapturedCandidate {
  id: string;
  personId: string;
  fullName: string;
  title: string | null;
  companyName: string | null;
  triageCompanyId: string | null;
  linkedinUrl: string | null;
  /** When research last landed; null until then, and a person never researched has no photo. */
  enrichedAt: string | null;
  addedAt: string | null;
  /** A key of the workspace's pipeline catalog. */
  status: string;
  statusLabel: string | null;
  statusColour: string | null;
  statusOutOfProcess: boolean;
  pipelineStage: { key: string; label: string; colour: string | null } | null;
}

/** Who filed an executive into a position. */
export interface AddedBy {
  userId: string;
  name: string;
  avatarUrl: string | null;
}

/** `GET /projects/{id}/candidates/held`: the executive this position already maps at a profile. */
export interface HeldCandidate {
  candidate: CapturedCandidate;
  addedBy: AddedBy | null;
  /** Their employer's logo: the company row's, else research's. */
  companyLogoUrl: string | null;
}

/** One status of the workspace's pipeline, from `GET /workspace/pipeline`. */
export interface PipelineStatus {
  id: string;
  key: string;
  label: string;
  colour: string | null;
  position: number;
  isDefault: boolean;
  outOfProcess: boolean;
  retired: boolean;
}

export interface PipelineStage {
  id: string;
  key: string;
  label: string;
  colour: string | null;
  position: number;
  retired: boolean;
  statuses: PipelineStatus[];
}

export interface PipelineCatalog {
  stages: PipelineStage[];
}

export type NoteKind = "general" | "call" | "meeting" | "email" | "linkedin";
export type NoteFormat = "plain" | "markdown";

/** A note on a person or a company of the position; a company's names no position. */
export interface NoteRecord {
  id: string;
  kind: NoteKind;
  body: string;
  format: NoteFormat;
  pinned: boolean;
  projectId?: string | null;
  projectTitle?: string | null;
  authorUserId: string;
  authorName: string | null;
  authorAvatarUrl: string | null;
  createdAt: string;
  editedAt: string | null;
  editedByName: string | null;
  editable: boolean;
}

/** What the panel writes: always the editor's Markdown. `allPositions` only for a person's note. */
export interface WriteNotePayload {
  kind: NoteKind;
  body: string;
  format: NoteFormat;
  allPositions?: boolean;
}

/** Whose notes: a person mapped on a position, or a company of it. */
export type NoteTarget =
  | { subject: "person"; projectId: string; candidateId: string }
  | { subject: "company"; projectId: string; triageCompanyId: string };

export type CandidateTagColour = "green" | "accent" | "neutral" | "violet" | "adjacent" | "signal" | "red";

export interface CandidateTag {
  id: string;
  label: string;
  colour: CandidateTagColour;
  retired: boolean;
  holders: number;
}

/** The part of the workspace's person record the panel reads: the tags they hold. */
export interface PersonTags {
  personId: string;
  tagIds: string[];
}
