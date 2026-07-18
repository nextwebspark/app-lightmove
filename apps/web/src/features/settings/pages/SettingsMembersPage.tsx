import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, Button, Modal, Select, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { titleCase } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import type { WorkspaceRole } from "../../auth/api/types";
import { INVITE_ROLES } from "../../auth/schemas";
import { PROJECTS_KEY } from "../../projects/api/projectsApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import type { Invitation, Member } from "../../workspace/api/types";
import { InviteModal } from "../../workspace/components/InviteModal";

/**
 * Settings → Members: the active roster with role pickers and removal, then outstanding
 * invitations. Membership is invitation-only, so there is no approval queue — inviting someone
 * *is* letting them in.
 */
export function SettingsMembersPage() {
  const [inviteOpen, setInviteOpen] = useState(false);

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

function MemberRow({ member }: { member: Member }) {
  const { user, reload } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [confirmRemove, setConfirmRemove] = useState(false);
  const isSelf = member.userId === user?.id;

  // The workspace tier has two roles, so the picker stays a single select; the API takes the full
  // set, and picking one writes exactly that set. Multi-role selection matters at the project tier.
  const primaryRole: WorkspaceRole = member.roles.includes("ADMIN") ? "ADMIN" : "MEMBER";

  const refresh = async () => {
    // A change to yourself changes your token's claims; anyone else only changes the roster.
    if (isSelf) await reload();
    void queryClient.invalidateQueries({ queryKey: workspaceApi.MEMBERS_KEY });
    void queryClient.invalidateQueries({ queryKey: PROJECTS_KEY });
  };

  const changeRoles = useMutation({
    mutationFn: (role: WorkspaceRole) => workspaceApi.changeMemberRoles(member.memberId, [role]),
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
        value={primaryRole}
        onChange={(event) => changeRoles.mutate(event.target.value as WorkspaceRole)}
        disabled={changeRoles.isPending}
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
              : `${member.fullName} loses access immediately. If they are the only admin on live projects, hand those over first.`}
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
