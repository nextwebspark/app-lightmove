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
  calendarSync: CalendarSync;
  defaultRegion: string;
  defaultCurrency: string;
  plan: string;
  memberCount: number;
  createdAt: string;
  persona: HiringPersona;
  company: WorkspaceCompany | null;
}

/** How calendar events are read: pushed through Recall.ai, or read directly when a drawer opens. */
export type CalendarSync = "RECALL" | "DIRECT";

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

/** The swatches a pipeline stage or status is drawn in: the tag palette's six roles, plus signal and red. */
export type PipelineColour = "green" | "accent" | "neutral" | "violet" | "adjacent" | "inferred" | "signal" | "red";

/** One status of the catalog. `key` is the wire value a candidate's `status` carries. */
export interface PipelineStatus {
  id: string;
  key: string;
  label: string;
  colour: string;
  position: number;
  isDefault: boolean;
  /** One of the seven seeded statuses: a wire key the extension and the public API still speak, nothing more. */
  system: boolean;
  /** Whoever holds it is Inactive on the board and approached no more, whatever stage it sits under. */
  outOfProcess: boolean;
  retired: boolean;
}

export interface PipelineStage {
  id: string;
  key: string;
  label: string;
  colour: string;
  position: number;
  system: boolean;
  retired: boolean;
  statuses: PipelineStatus[];
}

/** The workspace's stages in order, each with its statuses in order; retired ones included and flagged. */
export interface PipelineCatalog {
  stages: PipelineStage[];
}

export interface PipelineStageChange {
  label?: string;
  colour?: string;
  retired?: boolean;
  /** With `retired`, retires the stage's live statuses along with it. */
  retireStatuses?: boolean;
}

/** The events that move a candidate forward to a status of the workspace's choosing. */
export type StatusAutomationTrigger = "emailSent" | "replied" | "callBooked";

export interface StatusAutomation {
  trigger: StatusAutomationTrigger;
  enabled: boolean;
  statusId: string | null;
  /** Its status was retired or taken out of process since, so it moves nobody until another is chosen. */
  broken: boolean;
}

export interface StatusAutomationChange {
  enabled: boolean;
  statusId: string | null;
}

/** What retiring a stage or a status would touch: who holds each status, and the automations it stops. */
export interface PipelineRetirePreview {
  statuses: { statusId: string; label: string; holders: number }[];
  automations: StatusAutomation[];
}

export interface PipelineStatusChange {
  label?: string;
  colour?: string;
  stageId?: string;
  isDefault?: true;
  outOfProcess?: boolean;
  retired?: boolean;
}
