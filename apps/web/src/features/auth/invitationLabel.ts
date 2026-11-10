import { ApiRequestError } from "../../lib/apiClient";
import { titleCase } from "../../lib/format";
import type { WorkspaceVocabulary } from "../workspace/lib/vocabulary";
import type { PendingInvitation } from "./api/types";

/**
 * Whether an invitation is to the workspace already open: a role on top of the membership held there — a client
 * contact asked onto the team, or staff named a contact — never an invitation "to" the place the person already is.
 */
export function isInvitationHere(invitation: PendingInvitation, currentWorkspaceId: string | null | undefined): boolean {
  return invitation.workspaceId === currentWorkspaceId;
}

/** The role an invitation grants, in the words the screen uses: "Member", "Admin", or the workspace's contact word. */
export function invitationRoleWord(invitation: PendingInvitation, vocabulary: WorkspaceVocabulary): string {
  return invitation.role === "CLIENT" ? vocabulary.contact : titleCase(invitation.role);
}

/** How a pending invitation reads in a menu: "Invited to Northgate", or "Invited as Member here". */
export function invitationLabel(
  invitation: PendingInvitation,
  currentWorkspaceId: string | null | undefined,
  vocabulary: WorkspaceVocabulary,
): string {
  return isInvitationHere(invitation, currentWorkspaceId)
    ? `Invited as ${invitationRoleWord(invitation, vocabulary)} here`
    : `Invited to ${invitation.workspaceName}`;
}

/** Revoked, accepted elsewhere or lapsed since it was listed: the list is stale and should be read again. */
export function isDeadInvitation(error: unknown): boolean {
  return error instanceof ApiRequestError
    && (error.code === "INVITATION_INVALID" || error.code === "INVITATION_EXPIRED");
}
