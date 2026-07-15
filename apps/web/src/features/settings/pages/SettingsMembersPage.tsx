import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, Button, Modal, Select, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { titleCase } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import type { PendingMember, WorkspaceRole } from "../../auth/api/types";
import { INVITE_ROLES } from "../../auth/schemas";
import { PROJECTS_KEY } from "../../projects/api/projectsApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import type { Invitation, Member } from "../../workspace/api/types";
import { InviteModal } from "../../workspace/components/InviteModal";

/**
 * Settings → Members: the pending join queue on top (the other half of the join flow — someone is
 * on a waiting screen until an admin acts here), then the active roster with role pickers and
 * removal, then outstanding invitations.
 */
export function SettingsMembersPage() {
  const [inviteOpen, setInviteOpen] = useState(false);

  const { data: pending = [] } = useQuery({
    queryKey: workspaceApi.PENDING_MEMBERS_KEY,
    queryFn: workspaceApi.pendingMembers,
  });
  const { data: members = [] } = useQuery({
    queryKey: workspaceApi.MEMBERS_KEY,
    queryFn: workspaceApi.members,
  });
  const { data: invitations = [] } = useQuery({
    queryKey: workspaceApi.INVITATIONS_KEY,
    queryFn: workspaceApi.invitations,
  });

  return (
    <>
      <PageHeader
        title="Members"
        subtitle={`${members.length} ${members.length === 1 ? "member" : "members"} · roles apply per project`}
        action={
          <Button className="!px-3.5 !py-[7px] !text-[13px]" onClick={() => setInviteOpen(true)}>
            <Icon d={ICONS.plus} size={15} />
            Invite
          </Button>
        }
      />

      {pending.length > 0 && (
        <section className="mb-5 rounded-[10px] border border-amber bg-amber-dim/50 p-4">
          <h3 className="text-[14px] font-semibold">
            {pending.length} {pending.length === 1 ? "person wants" : "people want"} to join
          </h3>
          <p className="mb-3 mt-0.5 font-mono text-[11px] text-text3">
            Approving grants access to this workspace's candidate data.
          </p>
          <div className="flex flex-col gap-2">
            {pending.map((member) => (
              <PendingRow key={member.memberId} member={member} />
            ))}
          </div>
        </section>
      )}

      <div className="rounded-[10px] border border-line-soft bg-panel2 px-5 py-2">
        {members.map((member) => (
          <MemberRow key={member.memberId} member={member} />
        ))}
      </div>

      {invitations.length > 0 && (
        <section className="mt-5">
          <h3 className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
            Outstanding invitations
          </h3>
          <div className="rounded-[10px] border border-line-soft bg-panel2 px-5 py-2">
            {invitations.map((invitation) => (
              <InvitationRow key={invitation.id} invitation={invitation} />
            ))}
          </div>
        </section>
      )}

      {inviteOpen && <InviteModal open onClose={() => setInviteOpen(false)} />}
    </>
  );
}

/** One person waiting to be let in. The role picker is where the actual grant happens. */
function PendingRow({ member }: { member: PendingMember }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [role, setRole] = useState<WorkspaceRole>(member.requestedRole);

  const settle = {
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: workspaceApi.PENDING_MEMBERS_KEY });
      void queryClient.invalidateQueries({ queryKey: workspaceApi.MEMBERS_KEY });
    },
    onError: (error: unknown) => toast(messageFor(error)),
  };

  const approve = useMutation({
    mutationFn: () => workspaceApi.approveMember(member.memberId, role),
    ...settle,
  });
  const decline = useMutation({
    mutationFn: () => workspaceApi.rejectMember(member.memberId),
    ...settle,
  });

  const deciding = approve.isPending || decline.isPending;

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-lg border border-line bg-panel p-3">
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] font-medium">{member.fullName}</span>
        <span className="block font-mono text-[11px] text-text3">
          {member.email} · asked for {titleCase(member.requestedRole)}
        </span>
      </span>

      <Select
        value={role}
        onChange={(event) => setRole(event.target.value as WorkspaceRole)}
        disabled={deciding}
        aria-label={`Role for ${member.fullName}`}
        className="w-[130px] shrink-0"
      >
        {INVITE_ROLES.map((option) => (
          <option key={option} value={option}>
            {titleCase(option)}
          </option>
        ))}
      </Select>

      <Button className="!py-1.5 !text-xs" loading={approve.isPending} disabled={deciding} onClick={() => approve.mutate()}>
        Approve
      </Button>
      <Button variant="secondary" className="!py-1.5 !text-xs" loading={decline.isPending} disabled={deciding} onClick={() => decline.mutate()}>
        Decline
      </Button>
    </div>
  );
}

function MemberRow({ member }: { member: Member }) {
  const { user, reload } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [confirmRemove, setConfirmRemove] = useState(false);
  const isSelf = member.userId === user?.id;

  const refresh = async () => {
    // A change to yourself changes your token's claims; anyone else only changes the roster.
    if (isSelf) await reload();
    void queryClient.invalidateQueries({ queryKey: workspaceApi.MEMBERS_KEY });
    void queryClient.invalidateQueries({ queryKey: PROJECTS_KEY });
  };

  const changeRole = useMutation({
    mutationFn: (role: WorkspaceRole) => workspaceApi.changeMemberRole(member.memberId, role),
    onSuccess: async () => {
      await refresh();
      toast("Role updated");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const remove = useMutation({
    mutationFn: () => workspaceApi.removeMember(member.memberId),
    onSuccess: async () => {
      setConfirmRemove(false);
      await refresh();
      toast(isSelf ? "You left the workspace" : "Member removed");
    },
    onError: (error) => {
      setConfirmRemove(false);
      toast(messageFor(error));
    },
  });

  return (
    <div className="flex items-center gap-3 border-t border-line-soft py-3 first:border-t-0">
      <Avatar id={member.memberId} name={member.fullName} size="lg" />
      <div className="min-w-0 flex-1">
        <div className="truncate text-[13px] font-medium">
          {member.fullName}
          {isSelf && <span className="ml-1.5 font-mono text-[10px] text-text3">(you)</span>}
        </div>
        <div className="mt-0.5 truncate font-mono text-[11px] text-text3">{member.email}</div>
      </div>

      <Select
        value={member.role}
        onChange={(event) => changeRole.mutate(event.target.value as WorkspaceRole)}
        disabled={changeRole.isPending}
        aria-label={`Role for ${member.fullName}`}
        className="w-[130px] shrink-0 !bg-panel"
      >
        {INVITE_ROLES.map((option) => (
          <option key={option} value={option}>
            {titleCase(option)}
          </option>
        ))}
      </Select>

      <button
        type="button"
        title={isSelf ? "Leave workspace" : "Remove member"}
        onClick={() => setConfirmRemove(true)}
        className="flex-none rounded-md p-1.5 text-text3 transition hover:bg-red-dim hover:text-red"
      >
        ✕
      </button>

      {confirmRemove && (
        <Modal open onClose={() => setConfirmRemove(false)} title={isSelf ? "Leave workspace" : "Remove member"}>
          <p className="mb-5 text-[13px] text-text2">
            {isSelf
              ? "You'll lose access to this workspace and everything in it."
              : `${member.fullName} loses access immediately. If they lead projects, reassign those first.`}
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setConfirmRemove(false)}>
              Cancel
            </Button>
            <Button
              className="!border-red !bg-red !text-white hover:!brightness-105"
              loading={remove.isPending}
              onClick={() => remove.mutate()}
            >
              {isSelf ? "Leave" : "Remove"}
            </Button>
          </div>
        </Modal>
      )}
    </div>
  );
}

function InvitationRow({ invitation }: { invitation: Invitation }) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const settle = {
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: workspaceApi.INVITATIONS_KEY });
    },
    onError: (error: unknown) => toast(messageFor(error)),
  };

  const resend = useMutation({
    mutationFn: () => workspaceApi.resendInvitation(invitation.id),
    ...settle,
    onSuccess: () => {
      settle.onSuccess();
      toast("Invitation re-sent");
    },
  });
  const revoke = useMutation({
    mutationFn: () => workspaceApi.revokeInvitation(invitation.id),
    ...settle,
    onSuccess: () => {
      settle.onSuccess();
      toast("Invitation revoked");
    },
  });

  const busy = resend.isPending || revoke.isPending;

  return (
    <div className="flex items-center gap-3 border-t border-line-soft py-3 first:border-t-0">
      <div className="min-w-0 flex-1">
        <div className="truncate font-mono text-[13px]">{invitation.email}</div>
        <div className="mt-0.5 font-mono text-[11px] text-text3">
          {titleCase(invitation.role)}
          {invitation.invitedByName && ` · invited by ${invitation.invitedByName}`}
        </div>
      </div>
      <Button variant="secondary" className="!py-1.5 !text-xs" disabled={busy} loading={resend.isPending} onClick={() => resend.mutate()}>
        Resend
      </Button>
      <Button variant="ghost" className="!py-1.5 !text-xs !text-red" disabled={busy} onClick={() => revoke.mutate()}>
        Revoke
      </Button>
    </div>
  );
}

