import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Avatar, useToast } from "../../../components/ui";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { messageFor } from "../../../lib/errorCodes";
import { initials } from "../../../lib/format";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import { useGridPaging } from "../../../lib/useGridPaging";
import { useGridSort } from "../../../lib/useGridSort";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import * as clientsApi from "../../clients/api/clientsApi";
import * as projectsApi from "../api/projectsApi";
import type { AttachedRepresentative, StaffRole, TeamMember } from "../api/types";
import { AddClientContactModal } from "../components/AddClientContactModal";
import { AddTeamMemberModal } from "../components/AddTeamMemberModal";
import { ProjectRoleLegend } from "../components/ProjectRoleChips";
import { ProjectTeamTable } from "../components/ProjectTeamTable";
import {
  PROJECT_TEAM_COLUMN_VISIBILITY,
  PROJECT_TEAM_SORT_FIELDS,
  projectTeamColumns,
  type ProjectTeamSortField,
  type ProjectTeamTableMeta,
} from "../lib/projectTeamColumns";

const PROJECT_TEAM_LAYOUT_COLUMNS = layoutColumnsOf(projectTeamColumns);

const DEFAULT_PROJECT_TEAM_SORT = { field: "roles", direction: "asc" } as const;

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
  const { user, reload } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  const [addTeamOpen, setAddTeamOpen] = useState(false);
  const [addContactOpen, setAddContactOpen] = useState(false);
  const [sort, setSort] = useGridSort<ProjectTeamSortField>(
    "projectTeam",
    project.id,
    PROJECT_TEAM_SORT_FIELDS,
    DEFAULT_PROJECT_TEAM_SORT,
  );
  const [columnVisibility, setColumnVisibility] = useColumnVisibility(
    "projectTeam",
    project.id,
    PROJECT_TEAM_COLUMN_VISIBILITY,
  );
  const [layout, setLayout] = useGridLayout("projectTeam", PROJECT_TEAM_LAYOUT_COLUMNS);
  const paging = useGridPaging();
  const { reset: resetPage } = paging;
  useEffect(() => resetPage(), [resetPage, sort]);

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
  const staff = useMemo(
    () => project.team.filter((member) => member.projectRoles.some((role) => role !== "CLIENT")),
    [project.team],
  );
  const leads = staff.filter((member) => member.projectRoles.includes("LEAD"));

  // A change to your own seat changes what you may do here, so the session has to catch up before the
  // page re-renders off it — otherwise a lead who just demoted themselves keeps the manage controls.
  const refresh = async (member: TeamMember) => {
    if (member.userId === user?.id) await reload();
    void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
  };

  const changeRole = useMutation({
    mutationFn: ({ member, role }: { member: TeamMember; role: StaffRole }) =>
      projectsApi.putProjectMember(project.id, member.memberId, role),
    onSuccess: async (_project, { member, role }) => {
      await refresh(member);
      toast(
        role === "LEAD"
          ? `${member.fullName} is now a lead on this project`
          : `${member.fullName} is now a researcher`,
      );
    },
    onError: (error) => toast(messageFor(error)),
  });

  const remove = useMutation({
    mutationFn: (member: TeamMember) => projectsApi.removeProjectMember(project.id, member.memberId),
    onSuccess: async (_project, member) => {
      await refresh(member);
      toast(`${member.fullName} removed from project`);
      // Removing your own seat can take the mandate with it — a non-lead loses WORK_VIEW entirely.
      if (member.userId === user?.id) navigate("/projects");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const busyMemberId =
    (changeRole.isPending ? changeRole.variables?.member.memberId : null) ??
    (remove.isPending ? remove.variables?.memberId : null) ??
    null;

  const teamMeta: ProjectTeamTableMeta = {
    viewerUserId: user?.id ?? null,
    canManage,
    soleLeadMemberId: leads.length === 1 ? leads[0]!.memberId : null,
    busyMemberId,
    onChangeRole: (member, role) => changeRole.mutate({ member, role }),
    onRemove: (member) => remove.mutate(member),
  };

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

        <div className="flex flex-col gap-3">
          <ProjectTeamTable
            staff={staff}
            meta={teamMeta}
            sort={sort}
            onSortChange={setSort}
            columnVisibility={columnVisibility}
            onColumnVisibilityChange={setColumnVisibility}
            layout={layout}
            onLayoutChange={setLayout}
            pagination={paging.pagination}
            onPaginationChange={paging.onPaginationChange}
          />
          <PaginationBar
            page={paging.page}
            size={paging.size}
            totalCount={staff.length}
            onPage={paging.setPage}
            onSize={paging.setSize}
            autoHide
          />
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
          className="rounded-md p-2.5 text-text3 hover:bg-red-dim hover:text-red disabled:opacity-50 lg:p-1.5"
        >
          <Icon d={ICONS.trash} size={15} />
        </button>
      )}
    </div>
  );
}
