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

export type ProjectRole = "ADMIN" | "LEAD" | "RESEARCHER";

/** A seat on a project's team. Both tiers' roles are sets — the creator holds ADMIN and LEAD. */
export interface TeamMember {
  memberId: string;
  userId: string;
  fullName: string;
  workspaceRoles: WorkspaceRole[];
  projectRoles: ProjectRole[];
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
  /** 0 until pipeline tables exist. */
  companies: number;
  candidates: number;
  createdAt: string;
}

export interface Client {
  id: string;
  name: string;
  hqCountry: string | null;
  activeMandates: number;
  deliveredMandates: number;
}
