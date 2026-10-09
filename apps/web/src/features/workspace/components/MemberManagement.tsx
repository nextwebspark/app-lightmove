import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Select, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { messageFor } from "../../../lib/errorCodes";
import { titleCase } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import type { WorkspaceRole } from "../../auth/api/types";
import { INVITE_ROLES } from "../../auth/schemas";
import { PROJECTS_KEY } from "../../projects/api/projectsApi";
import * as workspaceApi from "../api/workspaceApi";
import type { Invitation, Member } from "../api/types";

/** Reloads what a roster change touches: yourself first when it was you, since your token's claims moved. */
function useRosterRefresh(member: Member) {
  const { user, reload } = useAuth();
  const queryClient = useQueryClient();
  const isSelf = member.userId === user?.id;
  const refresh = async () => {
    if (isSelf) await reload();
    void queryClient.invalidateQueries({ queryKey: workspaceApi.MEMBERS_KEY });
    void queryClient.invalidateQueries({ queryKey: PROJECTS_KEY });
  };
  return { isSelf, refresh };
}

/**
 * An admin's role picker on one roster row. The workspace tier has two staff roles, so it stays a single select;
 * the API takes the full set, and picking one writes exactly that set.
 */
export function MemberRoleSelect({ member }: { member: Member }) {
  const toast = useToast();
  const { refresh } = useRosterRefresh(member);
  const primaryRole: WorkspaceRole = member.roles.includes("ADMIN") ? "ADMIN" : "MEMBER";

  const changeRoles = useMutation({
    mutationFn: (role: WorkspaceRole) => workspaceApi.changeMemberRoles(member.memberId, [role]),
    onSuccess: async () => {
      await refresh();
      toast.success("Role updated");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <Select
      value={primaryRole}
      onChange={(event) => changeRoles.mutate(event.target.value as WorkspaceRole)}
      disabled={changeRoles.isPending}
      aria-label={`Workspace role for ${member.fullName}`}
      className="w-[120px] shrink-0 !py-1.5"
    >
      {INVITE_ROLES.map((option) => (
        <option key={option} value={option}>
          {titleCase(option)}
        </option>
      ))}
    </Select>
  );
}

/** An admin's way to take someone off the roster — or to leave, on their own row. */
export function RemoveMemberButton({ member }: { member: Member }) {
  const toast = useToast();
  const { isSelf, refresh } = useRosterRefresh(member);
  const [confirmRemove, setConfirmRemove] = useState(false);

  const remove = useMutation({
    mutationFn: () => workspaceApi.removeMember(member.memberId),
    onSuccess: async () => {
      setConfirmRemove(false);
      await refresh();
      toast.success(isSelf ? "You left the workspace" : `${member.fullName} removed`);
    },
    onError: (error) => {
      setConfirmRemove(false);
      toast.error(messageFor(error));
    },
  });

  return (
    <>
      <button
        type="button"
        aria-label={isSelf ? "Leave workspace" : `Remove ${member.fullName}`}
        title={isSelf ? "Leave workspace" : "Remove from workspace"}
        onClick={() => setConfirmRemove(true)}
        className="flex-none rounded-md p-1.5 text-u-text3 transition hover:bg-u-offlimits-tint hover:text-u-offlimits"
      >
        <Icon d={ICONS.close} size={14} />
      </button>
      <ConfirmDialog
        open={confirmRemove}
        title={isSelf ? "Leave this workspace?" : `Remove ${member.fullName}?`}
        confirmLabel={isSelf ? "Leave" : "Remove"}
        pending={remove.isPending}
        onConfirm={() => remove.mutate()}
        onClose={() => setConfirmRemove(false)}
      >
        <p>
          {isSelf
            ? "You'll lose access to this workspace and everything in it."
            : `${member.fullName} loses access immediately. If they lead live positions, hand those over first.`}
        </p>
      </ConfirmDialog>
    </>
  );
}

/**
 * Invitations not yet answered. An admin sees each one and can resend or revoke it; everyone else sees only how
 * many there are, so nobody asks for a colleague who has already been asked in.
 */
export function PendingInvitations({ canManage }: { canManage: boolean }) {
  const { data: invitations = [] } = useQuery({
    queryKey: workspaceApi.INVITATIONS_KEY,
    queryFn: workspaceApi.invitations,
    enabled: canManage,
  });
  const { data: pending } = useQuery({
    queryKey: workspaceApi.PENDING_INVITATIONS_COUNT_KEY,
    queryFn: workspaceApi.pendingInvitationCount,
    enabled: !canManage,
  });

  if (!canManage) {
    const count = pending?.count ?? 0;
    if (count === 0) return null;
    return (
      <p className="mt-5 text-note text-u-text3">
        {count === 1 ? "1 invitation is" : `${count} invitations are`} waiting to be accepted. An admin manages them.
      </p>
    );
  }
  if (invitations.length === 0) return null;

  return (
    <section className="mt-6" aria-labelledby="pending-invitations">
      <h2
        id="pending-invitations"
        className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-u-text3"
      >
        Invitations waiting · {invitations.length}
      </h2>
      <div className="rounded-[10px] border border-u-border bg-u-raised px-5 py-2">
        {invitations.map((invitation) => (
          <InvitationRow key={invitation.id} invitation={invitation} />
        ))}
      </div>
    </section>
  );
}

function InvitationRow({ invitation }: { invitation: Invitation }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [isConfirmingRevoke, setIsConfirmingRevoke] = useState(false);

  const settle = () => {
    void queryClient.invalidateQueries({ queryKey: workspaceApi.INVITATIONS_KEY });
    void queryClient.invalidateQueries({ queryKey: workspaceApi.PENDING_INVITATIONS_COUNT_KEY });
  };
  const resend = useMutation({
    mutationFn: () => workspaceApi.resendInvitation(invitation.id),
    onSuccess: () => {
      settle();
      toast.success(`Invitation re-sent to ${invitation.email}`);
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const revoke = useMutation({
    mutationFn: () => workspaceApi.revokeInvitation(invitation.id),
    onSuccess: () => {
      setIsConfirmingRevoke(false);
      settle();
      toast.success("Invitation revoked");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const busy = resend.isPending || revoke.isPending;

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2 border-t border-u-border py-3 first:border-t-0">
      <div className="min-w-0 flex-1">
        <div className="truncate font-mono text-[13px]">{invitation.email}</div>
        <div className="mt-0.5 font-mono text-[11px] text-u-text3">
          {titleCase(invitation.role)} · sent {sentAgo(invitation.createdAt)}
          {invitation.invitedByName && ` by ${invitation.invitedByName}`}
        </div>
      </div>
      <Button
        variant="secondary"
        className="!py-1.5 !text-xs"
        disabled={busy}
        loading={resend.isPending}
        onClick={() => resend.mutate()}
      >
        Resend
      </Button>
      <Button
        variant="ghost"
        className="!py-1.5 !text-xs !text-u-offlimits"
        disabled={busy}
        onClick={() => setIsConfirmingRevoke(true)}
      >
        Revoke
      </Button>
      <ConfirmDialog
        open={isConfirmingRevoke}
        title={`Revoke the invitation to ${invitation.email}?`}
        confirmLabel="Revoke"
        pending={revoke.isPending}
        onConfirm={() => revoke.mutate()}
        onClose={() => setIsConfirmingRevoke(false)}
      >
        <p>The link in their email will stop working. You can invite them again later.</p>
      </ConfirmDialog>
    </div>
  );
}

export function sentAgo(createdAt: string, now: number = Date.now()): string {
  const days = Math.floor((now - new Date(createdAt).getTime()) / 86_400_000);
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  return `${days} days ago`;
}
