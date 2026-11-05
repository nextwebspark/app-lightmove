import { titleCase } from "../../lib/format";
import type { WorkspaceVocabulary } from "../workspace/lib/vocabulary";
import type { PendingInvitation } from "./api/types";

/**
 * How a pending invitation reads in a menu or list. One to the workspace already open is a role on top of the
 * membership held there — a client contact asked onto the team, or staff named a contact — so it never reads as
 * an invitation "to" the place the person already is.
 */
export function invitationLabel(
  invitation: PendingInvitation,
  currentWorkspaceId: string | null | undefined,
  vocabulary: WorkspaceVocabulary,
): string {
  if (invitation.workspaceId !== currentWorkspaceId) {
    return `Invited to ${invitation.workspaceName}`;
  }
  const role = invitation.role === "CLIENT" ? vocabulary.contactLower : titleCase(invitation.role);
  return `Invited as ${role} here`;
}
