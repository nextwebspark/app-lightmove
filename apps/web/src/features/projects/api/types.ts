import type { WorkspaceRole } from "../../auth/api/types";

/** The projects/clients API contract, hand-mirrored like the auth module's. */

export type ProjectStage =
  | "BRIEF"
  | "UNIVERSE"
  | "LOCKED"
  | "MAPPING"
  | "OUTREACH"
  | "DELIVERED"
  | "CLOSED";

export type ProjectHealth = "OK" | "RISK" | "OFF" | "DONE";

export type ProjectRole = "LEAD" | "RESEARCHER" | "CLIENT";

/** The staff roles the team table hands out. CLIENT is not one of them — it comes from an attach. */
export const STAFF_ROLES = ["LEAD", "RESEARCHER"] as const satisfies readonly ProjectRole[];

export type StaffRole = (typeof STAFF_ROLES)[number];

/**
 * A seat on a project's team. `projectRoles` holds one staff role; a client representative who also
 * staffs the mandate carries CLIENT alongside it.
 */
export interface TeamMember {
  memberId: string;
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  workspaceRoles: WorkspaceRole[];
  projectRoles: ProjectRole[];
}

/**
 * A client-side contact on this mandate: seated read-only (ACTIVE) or attached while their portal
 * invitation is still out (INVITED — the server seats them automatically when they accept).
 */
export interface AttachedRepresentative {
  representativeId: string;
  fullName: string;
  position: string | null;
  email: string;
  status: "INVITED" | "ACTIVE";
}

/** What a mandate delivers: a mapped executive universe, or a full search through to a shortlist. */
export type ProjectType = "MAPPING" | "SEARCH";

export interface Project {
  id: string;
  clientId: string;
  clientName: string;
  clientLogoUrl: string | null;
  positionTitle: string;
  stage: ProjectStage;
  health: ProjectHealth;
  /** The brief's hire date ("Target start"); not when the work is due — see `deliveryDate`. */
  targetDate: string | null;
  projectType: ProjectType;
  startDate: string | null;
  /** When the business unit expects the map or the shortlist. Null on a mandate older than V73. */
  deliveryDate: string | null;
  /** A search's point by which the universe should be mapped; null on a mapping project. */
  mappingTargetDate: string | null;
  team: TeamMember[];
  representatives: AttachedRepresentative[];
  /** The mandate's live universe: every company it has triaged and not declined. */
  companies: number;
  /** Every executive the mandate still has in play — those who left the running are not counted. */
  candidates: number;
  /** Every executive the mandate has mapped, ruled out or not. */
  mappedCandidates: number;
  /** Executives who have answered: engaged or interested. */
  engagedCandidates: number;
  /** Universe companies with at least one executive mapped at them: the side panel's coverage. */
  mappedCompanies: number;
  createdAt: string;
}

/**
 * One line of a mandate's recent activity — an audit event with its actor resolved. `details` holds
 * only the few keys the server allows through (`status`, `added`, `companyName`, `fullName`,
 * `fileName`, `companiesCreated`, `candidatesCreated`, `stage`).
 */
export interface ProjectActivityEntry {
  id: number;
  type: string;
  occurredAt: string;
  actorUserId: string | null;
  actorName: string | null;
  actorAvatarUrl: string | null;
  details: Record<string, string>;
}

export interface ProjectActivityPage {
  entries: ProjectActivityEntry[];
  /** Pass back as `before` for the next page; null on the last one. */
  nextCursor: number | null;
}
