import type { WorkspaceRole } from "../../auth/api/types";

/** The workspace-management API contract, hand-mirrored like the auth module's. */

export interface WorkspaceDetail {
  id: string;
  name: string;
  slug: string;
  logoMark: string | null;
  emailDomain: string;
  defaultRegion: string;
  defaultCurrency: string;
  plan: string;
  memberCount: number;
  createdAt: string;
}

/** One row of the active roster. */
export interface Member {
  memberId: string;
  userId: string;
  fullName: string;
  email: string;
  title: string | null;
  roles: WorkspaceRole[];
  joinedAt: string | null;
}

/** An invitation still waiting to be accepted. */
export interface Invitation {
  id: string;
  email: string;
  role: WorkspaceRole;
  invitedByName: string | null;
  createdAt: string;
  expiresAt: string;
}
