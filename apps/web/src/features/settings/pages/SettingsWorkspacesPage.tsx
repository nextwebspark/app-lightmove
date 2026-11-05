import { useMutation } from "@tanstack/react-query";
import { useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, useToast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { titleCase } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import type { PendingInvitation, WorkspaceSummary } from "../../auth/api/types";
import { isPureClient } from "../../auth/roles";
import { NewWorkspaceModal } from "../../workspace/components/NewWorkspaceModal";
import { WorkspaceMark } from "../../workspace/components/WorkspaceMark";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";

/**
 * Settings → Workspaces: every workspace the caller is in, the invitations waiting for them, and the
 * door to founding another. Everyone's — a member of two firms switches here as well as from the
 * topbar — but only <b>staff</b> of the current workspace may create one: a client representative is
 * an outside contact on a mandate, not a consultant founding a firm.
 */
export function SettingsWorkspacesPage() {
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const canCreate = !isPureClient(user?.workspace?.roles ?? []);
  // The URL is the dialog's one state, so the topbar's Create workspace opens it whether or not this page is showing.
  const createOpen = canCreate && searchParams.get("create") === "1";
  const setCreateOpen = (open: boolean) => setSearchParams(open ? { create: "1" } : {}, { replace: !open });

  if (!user) return null;
  const workspaces = user.workspaces;
  const closeCreate = () => setCreateOpen(false);

  return (
    <>
      <PageHeader
        title="Workspaces"
        subtitle={`${workspaces.length} ${workspaces.length === 1 ? "workspace" : "workspaces"} · each with its own positions, team and settings`}
        action={
          canCreate ? (
            <Button size="sm" onClick={() => setCreateOpen(true)}>
              <Icon d={ICONS.plus} size={15} />
              Create workspace
            </Button>
          ) : undefined
        }
      />

      <div className="rounded-[10px] border border-u-border bg-u-raised px-5 py-2">
        {workspaces.map((workspace) => (
          <WorkspaceRow key={workspace.id} workspace={workspace} current={workspace.id === user.workspace?.id} />
        ))}
      </div>

      {user.pendingInvitations.length > 0 && (
        <section className="mt-5">
          <h3 className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-u-text3">
            Invitations
          </h3>
          <div className="rounded-[10px] border border-u-border bg-u-raised px-5 py-2">
            {user.pendingInvitations.map((invitation) => (
              <InvitationRow key={invitation.id} invitation={invitation} />
            ))}
          </div>
        </section>
      )}

      {createOpen && <NewWorkspaceModal onClose={closeCreate} />}
    </>
  );
}

function WorkspaceRow({ workspace, current }: { workspace: WorkspaceSummary; current: boolean }) {
  const { switchWorkspace } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const open = useMutation({
    mutationFn: () => switchWorkspace(workspace.id),
    onSuccess: () => navigate("/"),
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2 border-t border-u-border py-3 first:border-t-0">
      <WorkspaceMark workspace={workspace} size={30} />
      <div className="min-w-0 flex-1">
        <div className="truncate text-body font-medium">{workspace.name}</div>
        <div className="mt-0.5 truncate font-mono text-meta text-u-text3">
          {workspace.roles.map(titleCase).join(", ")}
        </div>
      </div>

      {current ? (
        <span className="rounded-full bg-u-direct-tint px-2.5 py-1 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-direct">
          Current
        </span>
      ) : (
        <Button variant="secondary" size="xs" loading={open.isPending} onClick={() => open.mutate()}>
          Open
        </Button>
      )}
    </div>
  );
}

function InvitationRow({ invitation }: { invitation: PendingInvitation }) {
  const { user, acceptAndSwitch, reload } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const vocabulary = useWorkspaceVocabulary();
  // isPending reaches the button a render late; a double click must not send the accept twice.
  const inFlight = useRef(false);

  const accept = useMutation({
    mutationFn: () => acceptAndSwitch(() => authApi.acceptInvitationById(invitation.id)),
    onSuccess: () => navigate("/"),
    onError: (error) => {
      toast.error(messageFor(error));
      // Revoked or lapsed since /me was read: refresh so the row goes rather than failing every click.
      if (error instanceof ApiRequestError
        && (error.code === "INVITATION_INVALID" || error.code === "INVITATION_EXPIRED")) {
        void reload();
      }
    },
    onSettled: () => {
      inFlight.current = false;
    },
  });

  const handleAccept = () => {
    if (inFlight.current) return;
    inFlight.current = true;
    accept.mutate();
  };

  const here = invitation.workspaceId === user?.workspace?.id;
  const role = here && invitation.role === "CLIENT" ? vocabulary.contactLower : titleCase(invitation.role);

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2 border-t border-u-border py-3 first:border-t-0">
      <div className="min-w-0 flex-1">
        <div className="truncate text-body font-medium">
          {invitation.workspaceName}
          {here && <span className="ms-2 text-meta font-normal text-u-text3">this workspace</span>}
        </div>
        <div className="mt-0.5 truncate font-mono text-meta text-u-text3">
          {invitation.inviterName ? `${invitation.inviterName} invited you` : "You were invited"} as {role}
        </div>
      </div>
      <Button size="xs" loading={accept.isPending} onClick={handleAccept}>
        Accept
      </Button>
    </div>
  );
}
