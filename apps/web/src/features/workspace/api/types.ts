import type { WorkspaceCompany, WorkspaceMode } from "../../auth/api/types";
import type { WorkspaceRole } from "../../auth/api/types";

/** The workspace-management API contract, hand-mirrored like the auth module's. */

export interface WorkspaceDetail {
  id: string;
  name: string;
  slug: string;
  logoMark: string | null;
  emailDomain: string;
  mode: WorkspaceMode;
  defaultRegion: string;
  defaultCurrency: string;
  plan: string;
  memberCount: number;
  createdAt: string;
  persona: HiringPersona;
  company: WorkspaceCompany | null;
}

/** What a hiring company is — the firm itself, or an agency's client — for the assistant to tailor research to. */
export interface HiringPersona {
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
