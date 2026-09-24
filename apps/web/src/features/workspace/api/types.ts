import type { WorkspaceCompany } from "../../auth/api/types";
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
  persona: WorkspacePersona;
  company: WorkspaceCompany | null;
}

/** What the firm is, for the assistant to tailor its research to. Edited by an admin. */
export interface WorkspacePersona {
  summary: string | null;
  sectors: string[];
  competitors: string[];
  geographies: string[];
  notes: string | null;
}

/** One row of the active roster. */
export interface Member {
  memberId: string;
  userId: string;
  fullName: string;
  email: string;
  title: string | null;
  avatarUrl: string | null;
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
