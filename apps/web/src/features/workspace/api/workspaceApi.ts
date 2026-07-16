import { request } from "../../../lib/apiClient";
import type { InviteRequest, PendingMember, WorkspaceRole } from "../../auth/api/types";
import type { Invitation, Member, WorkspaceDetail } from "./types";

/** Every call workspace management makes (roster, invitations, settings), plus shared query keys. */

export const WORKSPACE_KEY = ["workspace"] as const;
export const MEMBERS_KEY = ["members"] as const;
export const PENDING_MEMBERS_KEY = ["pending-members"] as const;
export const INVITATIONS_KEY = ["invitations"] as const;

export function workspace(): Promise<WorkspaceDetail> {
  return request<WorkspaceDetail>("/workspace");
}

export function updateWorkspace(payload: {
  name: string;
  defaultRegion?: string;
  defaultCurrency?: string;
}): Promise<WorkspaceDetail> {
  return request<WorkspaceDetail>("/workspace", { method: "PATCH", body: payload });
}

export function deleteWorkspace(confirmName: string): Promise<void> {
  return request<void>("/workspace", { method: "DELETE", body: { confirmName } });
}

export function members(): Promise<Member[]> {
  return request<Member[]>("/members");
}

export function changeMemberRole(memberId: string, role: WorkspaceRole): Promise<Member> {
  return request<Member>(`/members/${memberId}`, { method: "PATCH", body: { role } });
}

export function removeMember(memberId: string): Promise<void> {
  return request<void>(`/members/${memberId}`, { method: "DELETE" });
}

export function pendingMembers(): Promise<PendingMember[]> {
  return request<PendingMember[]>("/members/pending");
}

export function approveMember(memberId: string, role: WorkspaceRole): Promise<PendingMember> {
  return request<PendingMember>(`/members/${memberId}/approve`, { method: "POST", body: { role } });
}

export function rejectMember(memberId: string): Promise<void> {
  return request<void>(`/members/${memberId}/reject`, { method: "POST" });
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
