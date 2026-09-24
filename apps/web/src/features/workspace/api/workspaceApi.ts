import { request } from "../../../lib/apiClient";
import type { InviteRequest, WorkspaceRole } from "../../auth/api/types";
import type { Invitation, Member, WorkspaceDetail, WorkspacePersona } from "./types";

/** Every call workspace management makes (roster, invitations, settings), plus shared query keys. */

export const WORKSPACE_KEY = ["workspace"] as const;
export const MEMBERS_KEY = ["members"] as const;
export const INVITATIONS_KEY = ["invitations"] as const;

export function workspace(): Promise<WorkspaceDetail> {
  return request<WorkspaceDetail>("/workspace");
}

export function updateWorkspace(payload: {
  name: string;
  /** Null files the typed name with no company, clearing any snapshot the workspace held. */
  apolloAccountId: string | null;
  defaultRegion?: string;
  defaultCurrency?: string;
}): Promise<WorkspaceDetail> {
  return request<WorkspaceDetail>("/workspace", { method: "PATCH", body: payload });
}

export function updatePersona(persona: WorkspacePersona): Promise<WorkspaceDetail> {
  return request<WorkspaceDetail>("/workspace/persona", { method: "PUT", body: persona });
}

export function deleteWorkspace(confirmName: string): Promise<void> {
  return request<void>("/workspace", { method: "DELETE", body: { confirmName } });
}

export function members(): Promise<Member[]> {
  return request<Member[]>("/members");
}

/** Replace-set: the full set of roles the member holds afterwards. */
export function changeMemberRoles(memberId: string, roles: WorkspaceRole[]): Promise<Member> {
  return request<Member>(`/members/${memberId}`, { method: "PATCH", body: { roles } });
}

export function removeMember(memberId: string): Promise<void> {
  return request<void>(`/members/${memberId}`, { method: "DELETE" });
}

export function invitations(): Promise<Invitation[]> {
  return request<Invitation[]>("/invitations");
}

export function invite(invites: InviteRequest[]): Promise<{ sent: number }> {
  return request<{ sent: number }>("/invitations", { method: "POST", body: invites });
}

export function resendInvitation(invitationId: string): Promise<void> {
  return request<void>(`/invitations/${invitationId}/resend`, { method: "POST" });
}

export function revokeInvitation(invitationId: string): Promise<void> {
  return request<void>(`/invitations/${invitationId}`, { method: "DELETE" });
}
