import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthProvider";
import { Avatar, Button, Drawer, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { formatDate, titleCase } from "../../../lib/format";
import type { Member } from "../../workspace/api/types";
import * as projectsApi from "../api/projectsApi";
import type { Project, ProjectRole, StaffRole } from "../api/types";
import { STAGE_ORDER } from "../lib/filtering";
import { stageLabel } from "../../../components/ui";
import { ProjectRoleChips } from "./ProjectRoleChips";

/**
 * The right slide-over: pipeline stats, display-only stage gates, and a quick team panel — seat a
 * member on a mandate, take them off, or move their one staff role. The mandate's own Team & access
 * screen is the fuller surface; this is the list view's shortcut, sharing its role chips so the two
 * cannot drift. The server owns the invariants: the last lead refuses demotion and removal with a toast.
 */
export function ProjectDrawer({
  project,
  members,
  onClose,
}: {
  project: Project | null;
  members: Member[];
  onClose: () => void;
}) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();

  const settle = {
    onError: (error: unknown) => toast(messageFor(error)),
  };

  const toggle = useMutation({
    mutationFn: ({ memberId, on }: { memberId: string; on: boolean }) =>
      on
        ? projectsApi.putProjectMember(project!.id, memberId, "RESEARCHER")
        : projectsApi.removeProjectMember(project!.id, memberId),
    onSuccess: (_, { on }) => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      toast(on ? "Added to project" : "Removed from project");
    },
    ...settle,
  });

  const changeRole = useMutation({
    mutationFn: ({ memberId, role }: { memberId: string; role: StaffRole }) =>
      projectsApi.putProjectMember(project!.id, memberId, role),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      toast("Role updated");
    },
    ...settle,
  });

  if (!project) return null;

  // The server's TEAM_MANAGE gate, mirrored per project: the mandate's lead, or a workspace admin by
  // bypass. Gating on "not a pure client" instead would offer a researcher controls that only 403.
  const canManageTeam =
    (user?.workspace?.roles.includes("ADMIN") ?? false) ||
    project.team.some((seat) => seat.userId === user?.id && seat.projectRoles.includes("LEAD"));

  // A CLIENT-only seat is a client contact, not a team member: it is granted and dropped from the
  // project's Team & access screen, and must not read as "on the team" here.
  const staffRoleOf = (memberId: string): StaffRole | undefined => {
    const seat = project.team.find((held) => held.memberId === memberId);
    const staffRoles: ProjectRole[] = seat?.projectRoles.filter((role) => role !== "CLIENT") ?? [];
    if (staffRoles.length === 0) return undefined;
    return staffRoles.includes("LEAD") ? "LEAD" : "RESEARCHER";
  };
  const currentStage = STAGE_ORDER.indexOf(project.stage);
  const gates = STAGE_ORDER.filter((stage) => stage !== "CLOSED");
  const busy = toggle.isPending || changeRole.isPending;

  return (
    <Drawer open onClose={onClose}>
      <div className="relative border-b border-line-soft px-5 pb-3.5 pt-[18px]">
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="absolute right-3.5 top-3.5 rounded-md p-1.5 text-text3 hover:bg-panel2 hover:text-text"
        >
          ✕
        </button>
        <div className="font-mono text-[11px] font-medium uppercase tracking-[0.08em] text-text3">
          {project.clientName}
        </div>
        <div className="mt-1 text-[17px] font-semibold">{project.positionTitle}</div>
        <Button className="mt-3 w-full" onClick={() => navigate(`/projects/${project.id}`)}>
          Open project →
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-[18px]">
        <SectionLabel>Pipeline</SectionLabel>
        <div className="flex gap-2.5">
          <StatTile value={String(project.companies)} label="Companies" />
          <StatTile value={String(project.candidates)} label="Candidates" />
          <StatTile value={formatDate(project.targetDate).slice(0, 6)} label="Target" />
        </div>

        <SectionLabel className="mt-[18px]">Stage gates</SectionLabel>
        {gates.map((stage, index) => {
          const done = index < currentStage;
          const now = index === currentStage;
          return (
            <div
              key={stage}
              className={`flex items-center gap-2.5 py-[7px] font-mono text-[12.5px] ${
                now ? "font-semibold text-amber" : done ? "text-text2" : "text-text3"
              }`}
            >
              <span
                className={`grid size-3.5 flex-none place-items-center rounded-full border-[1.5px] ${
                  done ? "border-green bg-green-dim" : now ? "border-amber" : "border-line"
                }`}
              >
                <span className={`size-1.5 rounded-full ${done ? "bg-green" : now ? "bg-amber" : ""}`} />
              </span>
              {stageLabel(stage)}
            </div>
          );
        })}

        {canManageTeam && (
          <>
            <SectionLabel className="mt-[18px]">Team</SectionLabel>
            {members.map((member) => {
              const role = staffRoleOf(member.memberId);
              const on = Boolean(role);
              return (
                <div key={member.memberId} className="rounded-[7px] px-2 py-[7px] hover:bg-panel2">
                  <div className="flex items-center gap-2.5">
                    <Avatar id={member.memberId} name={member.fullName} src={member.avatarUrl} />
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[13px]">{member.fullName}</div>
                      <div className="font-mono text-[11px] text-text3">
                        {member.roles.map(titleCase).join(" · ")}
                      </div>
                    </div>
                    <button
                      type="button"
                      role="switch"
                      aria-checked={on}
                      aria-label={`Toggle ${member.fullName}`}
                      disabled={busy}
                      onClick={() => toggle.mutate({ memberId: member.memberId, on: !on })}
                      className={`relative h-[18px] w-8 flex-none rounded-full transition ${on ? "bg-amber-btn" : "bg-line"}`}
                    >
                      <span
                        className={`absolute left-0.5 top-0.5 size-3.5 rounded-full transition-transform ${
                          on ? "translate-x-3.5 bg-on-amber" : "bg-text3"
                        }`}
                      />
                    </button>
                  </div>

                  {role && (
                    <div className="ml-9 mt-1.5">
                      <ProjectRoleChips
                        memberName={member.fullName}
                        role={role}
                        canManage={canManageTeam}
                        pending={busy}
                        onChange={(next) =>
                          changeRole.mutate({ memberId: member.memberId, role: next })
                        }
                      />
                    </div>
                  )}
                </div>
              );
            })}
          </>
        )}
      </div>
    </Drawer>
  );
}

function SectionLabel({ children, className = "" }: { children: string; className?: string }) {
  return (
    <div className={`mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3 ${className}`}>
      {children}
    </div>
  );
}

function StatTile({ value, label }: { value: string; label: string }) {
  return (
    <div className="flex-1 rounded-lg border border-line-soft bg-panel2 px-3 py-2.5">
      <b className="block font-mono text-[17px] font-semibold text-text">{value}</b>
      <span className="font-mono text-[10.5px] uppercase tracking-[0.06em] text-text3">{label}</span>
    </div>
  );
}
