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

/** What a mandate is engaged to deliver, which decides how many milestones it has. */
export type ProjectType = "MAPPING" | "EXECUTIVE_SEARCH";

/** Which half of the work a mandate is in. Derived from coverage, never from its stage. */
export type MandatePhase = "MAP" | "ENGAGE";

/** How far a mandate has got, as the list's bar and the drawer's tiles read it. */
export interface MandateProgress {
  activePhase: MandatePhase;
  mappingComplete: boolean;
  mapPercent: number;
  engagePercent: number;
  universeCompanies: number;
  companiesResearched: number;
  candidatesMapped: number;
  candidatesEngaged: number;
  qualifiedMatches: number;
  mappingVelocityPerWeek: number;
  /** The milestone health is measured against — the mapping target, or the shortlist. */
  governingMilestone: string | null;
  daysRemaining: number | null;
}

/** One line of the drawer's activity feed, already rendered as a sentence by the server. */
export interface ProjectActivity {
  eventType: string;
  summary: string;
  actorName: string;
  actorAvatarUrl: string | null;
  occurredAt: string;
}

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

export interface Project {
  id: string;
  clientId: string;
  clientName: string;
  clientLogoUrl: string | null;
  positionTitle: string;
  stage: ProjectStage;
  projectType: ProjectType;
  health: ProjectHealth;
  startDate: string;
  mappingTargetDate: string | null;
  shortlistTargetDate: string | null;
  /** The brief's target start — when the hire should begin. Not a milestone; health ignores it. */
  targetDate: string | null;
  progress: MandateProgress;
  team: TeamMember[];
  representatives: AttachedRepresentative[];
  /** The mandate's live universe: every company it has triaged and not declined. */
  companies: number;
  /** Every executive the mandate has mapped. */
  candidates: number;
  createdAt: string;
}
