import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Avatar, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { initials } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import * as clientsApi from "../../clients/api/clientsApi";
import * as projectsApi from "../api/projectsApi";
import type { AttachedRepresentative, StaffRole, TeamMember } from "../api/types";
import { AddClientContactModal } from "../components/AddClientContactModal";
import { AddTeamMemberModal } from "../components/AddTeamMemberModal";
import { ProjectRoleChips, ProjectRoleLegend } from "../components/ProjectRoleChips";

/**
 * The Team & access tab (Project.dc.html): who staffs this mandate and what they may do, then the
 * client organisation and the people we report to on their side.
 *
 * The two halves are separate on purpose. Staff hold one project role apiece and it is editable here;
 * client contacts hold the read-only CLIENT seat, which is granted by attaching a representative and
 * never by the team table — so they render below with a lifecycle status, not a role.
 *
 * Contacts render from the project itself, so a pure client sees their own colleagues without ever
 * touching the staff-only client registry — which is why the registry query is gated off for them.
 */
export function TeamAccessPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [addTeamOpen, setAddTeamOpen] = useState(false);
  const [addContactOpen, setAddContactOpen] = useState(false);

  const clientOnly = isPureClient(user?.workspace?.roles ?? []);
  const seat = project.team.find((member) => member.userId === user?.id);
  // Mirrors the server: TEAM_MANAGE (the staff table) and CLIENT_ACCESS_MANAGE (who from the client
  // sees this mandate) are both the lead's, so one flag covers both sections. The workspace-admin
  // bypass applies to both. Note the client *registry* is a wider grant — any staff member may mint a
  // representative there; this flag is only about granting one sight of this search.
  const canManage =
    (user?.workspace?.roles.includes("ADMIN") ?? false) ||
    (seat?.projectRoles.includes("LEAD") ?? false);

  // The registry supplies the sector line and the contact modal's roster. Staff-only — a pure client's
  // page renders entirely from the project, and firing this for them would just 403.
  const { data: client } = useQuery({
    queryKey: clientsApi.clientKey(project.clientId),
    queryFn: () => clientsApi.client(project.clientId),
    // Waits for the session: until `me` resolves the roles are unknown, and firing early would hit
    // the staff-only registry as a pure client — a guaranteed 403.
    enabled: Boolean(user) && !clientOnly,
  });

  const detach = useMutation({
    mutationFn: (representativeId: string) =>
      projectsApi.detachRepresentative(project.id, representativeId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      toast("Contact removed from this mandate");
    },
    onError: (error) => toast(messageFor(error)),
  });

  // Staff only: a seat holding nothing but CLIENT belongs to the section below, not this table.
  const staff = project.team.filter((member) =>
    member.projectRoles.some((role) => role !== "CLIENT"),
  );
  const leadCount = staff.filter((member) => member.projectRoles.includes("LEAD")).length;

  const contacts = project.representatives;
  const contactCount = `${contacts.length} contact${contacts.length === 1 ? "" : "s"}`;

  return (
    <>
      <div className="animate-fade-up">
        <PageHeader
          title="Team & access"
          subtitle={`Who works this mandate and what they can do · ${staff.length} member${
            staff.length === 1 ? "" : "s"
          }`}
          action={
            canManage && (
              <button
                type="button"
                onClick={() => setAddTeamOpen(true)}
                className="inline-flex items-center gap-[7px] rounded-lg border border-amber-btn bg-amber-btn px-[13px] py-[7px] text-[13px] font-semibold text-on-amber hover:brightness-105"
              >
                <Icon d={ICONS.plus} size={14} />
                Add team member
              </button>
            )
          }
        />

        <PermissionBanner canManage={canManage} />
        <ProjectRoleLegend />

        <div className="overflow-hidden rounded-[11px] border border-line">
          <div className="grid grid-cols-[1.5fr_2.4fr_auto] gap-[14px] border-b border-line bg-panel2 px-4 py-2.5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
            <div>Member</div>
            <div>Roles on this project</div>
            <div className="text-right">{canManage ? "Manage" : ""}</div>
          </div>

          {staff.map((member) => (
            <TeamRow
              key={member.memberId}
              projectId={project.id}
              member={member}
              isSelf={member.userId === user?.id}
              canManage={canManage}
              isSoleLead={leadCount === 1 && member.projectRoles.includes("LEAD")}
            />
          ))}
        </div>

        <p className="mt-3 font-mono text-[11.5px] text-text3">
          A mandate always keeps at least one lead. Leads add members, set their role and decide who on
          the client side may read it.
        </p>

        <div className="mb-3.5 mt-8 flex items-start gap-4">
          <div>
            <h2 className="text-base font-semibold leading-tight">Client</h2>
            <p className="mt-1 font-mono text-xs text-text3">
              The client organisation and the people we report to on their side
            </p>
          </div>
        </div>

        <div className="overflow-hidden rounded-[11px] border border-line">
          <div className="flex items-center gap-3 border-b border-line bg-panel2 px-4 py-[15px]">
            <span className="grid size-[38px] flex-none place-items-center rounded-[9px] bg-sky-dim font-mono text-[13px] font-bold text-sky">
              {initials(project.clientName)}
            </span>
            <div className="min-w-0 flex-1">
              <div className="text-sm font-semibold">{project.clientName}</div>
              <div className="mt-0.5 font-mono text-[11.5px] text-text3">
                {[client?.sector, contactCount].filter(Boolean).join(" · ")}
              </div>
            </div>
            {canManage && (
              <button
                type="button"
                onClick={() => setAddContactOpen(true)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-amber-btn bg-amber-btn px-3 py-1.5 text-[12.5px] font-semibold text-on-amber hover:brightness-105"
              >
                <Icon d={ICONS.plus} size={14} />
                Add contact
              </button>
            )}
          </div>

          {contacts.map((contact) => (
            <ContactRow
              key={contact.representativeId}
              contact={contact}
              canRemove={canManage}
              removing={detach.isPending}
              onRemove={() => detach.mutate(contact.representativeId)}
            />
          ))}

          <div className="px-4 py-[11px] font-mono text-[11px] text-text3">
            Invited contacts join automatically once they accept — no action needed on your side.
          </div>
        </div>
      </div>

      {/* Outside the animated wrapper: while that ancestor's transform runs, it is the containing
          block for position:fixed, and the modal overlay would dim only this section, not the page. */}
      {addTeamOpen && (
        <AddTeamMemberModal project={project} onClose={() => setAddTeamOpen(false)} />
      )}
      {addContactOpen && (
        <AddClientContactModal
          project={project}
          roster={client?.representatives ?? []}
          onClose={() => setAddContactOpen(false)}
        />
      )}
    </>
  );
}

function PermissionBanner({ canManage }: { canManage: boolean }) {
  return (
    <div
      className={`mb-[18px] flex items-center gap-2.5 rounded-[9px] border px-[13px] py-2.5 text-[12.5px] ${
        canManage
          ? "border-sky bg-sky-dim text-sky"
          : "border-line bg-panel2 text-text2"
      }`}
    >
      <Icon d={canManage ? ICONS.info : ICONS.lock} size={15} className="shrink-0" />
      <span>
        {canManage
          ? "You're a lead on this mandate — you can add members and change their roles."
          : "You have view-only access to team roles. Ask a project lead to make changes."}
      </span>
    </div>
  );
}

function TeamRow({
  projectId,
  member,
  isSelf,
  canManage,
  isSoleLead,
}: {
  projectId: string;
  member: TeamMember;
  isSelf: boolean;
  canManage: boolean;
  /** The last lead standing: the server refuses to demote or unseat them, so the row says so first. */
  isSoleLead: boolean;
}) {
  const { reload } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();

  const role: StaffRole = member.projectRoles.includes("LEAD") ? "LEAD" : "RESEARCHER";

  // A change to your own seat changes what you may do here, so the session has to catch up before the
  // page re-renders off it — otherwise a lead who just demoted themselves keeps the manage controls.
  const refresh = async () => {
    if (isSelf) await reload();
    void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
  };

  const changeRole = useMutation({
    mutationFn: (next: StaffRole) => projectsApi.putProjectMember(projectId, member.memberId, next),
    onSuccess: async (_project, next) => {
      await refresh();
      toast(
        next === "LEAD"
          ? `${member.fullName} is now a lead on this project`
          : `${member.fullName} is now a researcher`,
      );
    },
    onError: (error) => toast(messageFor(error)),
  });

  const remove = useMutation({
    mutationFn: () => projectsApi.removeProjectMember(projectId, member.memberId),
    onSuccess: async () => {
      await refresh();
      toast(`${member.fullName} removed from project`);
      // Removing your own seat can take the mandate with it — a non-lead loses WORK_VIEW entirely.
      if (isSelf) navigate("/projects");
    },
    onError: (error) => toast(messageFor(error)),
  });

  return (
    <div className="grid grid-cols-[1.5fr_2.4fr_auto] items-center gap-[14px] border-b border-line-soft px-4 py-[13px]">
      <div className="flex min-w-0 items-center gap-2.5">
        <Avatar id={member.memberId} name={member.fullName} size="lg" className="size-8" />
        <div className="min-w-0">
          <div className="truncate text-[13.5px] font-medium">{member.fullName}</div>
          {isSelf && <div className="mt-0.5 font-mono text-[11px] text-text3">You</div>}
        </div>
      </div>

      <ProjectRoleChips
        memberName={member.fullName}
        role={role}
        canManage={canManage}
        isSoleLead={isSoleLead}
        pending={changeRole.isPending || remove.isPending}
        onChange={(next) => changeRole.mutate(next)}
      />

      <div className="flex justify-end">
        {isSoleLead ? (
          <span
            title="A mandate must keep a lead — make someone else lead first"
            className="p-1.5 text-text3"
          >
            <Icon d={ICONS.lock} size={15} />
          </span>
        ) : canManage ? (
          <button
            type="button"
            title="Remove from project"
            aria-label={`Remove ${member.fullName}`}
            disabled={remove.isPending || changeRole.isPending}
            onClick={() => remove.mutate()}
            className="rounded-md p-1.5 text-text3 hover:bg-red-dim hover:text-red disabled:opacity-50"
          >
            <Icon d={ICONS.trash} size={15} />
          </button>
        ) : null}
      </div>
    </div>
  );
}

const CONTACT_BADGE: Record<AttachedRepresentative["status"], { label: string; className: string }> = {
  ACTIVE: { label: "Active", className: "text-green bg-green-dim" },
  INVITED: { label: "Invite sent", className: "text-amber bg-amber-dim" },
};

function ContactRow({
  contact,
  canRemove,
  removing,
  onRemove,
}: {
  contact: AttachedRepresentative;
  canRemove: boolean;
  removing: boolean;
  onRemove: () => void;
}) {
  const badge = CONTACT_BADGE[contact.status];
  return (
    <div className="flex items-center gap-3 border-b border-line-soft px-4 py-[13px]">
      <Avatar id={contact.representativeId} name={contact.fullName} size="lg" />
      <div className="min-w-0 flex-1">
        <div className="text-[13.5px] font-medium">{contact.fullName}</div>
        <div className="mt-0.5 truncate font-mono text-[11.5px] text-text3">
          {[contact.position, contact.email].filter(Boolean).join(" · ")}
        </div>
      </div>
      <span
        className={`whitespace-nowrap rounded-full px-2 py-[3px] font-mono text-[9.5px] font-semibold uppercase tracking-[0.05em] ${badge.className}`}
      >
        {badge.label}
      </span>
      {canRemove && (
        <button
          type="button"
          title="Remove contact"
          aria-label={`Remove ${contact.fullName}`}
          disabled={removing}
          onClick={onRemove}
          className="rounded-md p-1.5 text-text3 hover:bg-red-dim hover:text-red disabled:opacity-50"
        >
          <Icon d={ICONS.trash} size={15} />
        </button>
      )}
    </div>
  );
}
