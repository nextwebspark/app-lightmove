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

export interface Project {
  id: string;
  clientId: string;
  clientName: string;
  positionTitle: string;
  stage: ProjectStage;
  health: ProjectHealth;
  targetDate: string | null;
  team: TeamMember[];
  representatives: AttachedRepresentative[];
  /** 0 until pipeline tables exist. */
  companies: number;
  candidates: number;
  createdAt: string;
}
