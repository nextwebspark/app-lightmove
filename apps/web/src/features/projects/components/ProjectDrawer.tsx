import { Link, useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, Button, CompanyLogo, Drawer, StagePill, stageLabel } from "../../../components/ui";
import { DrawerCloseButton } from "../../../components/ui/Drawer";
import { formatDate } from "../../../lib/format";
import type { AttachedRepresentative, Project, StaffRole, TeamMember } from "../api/types";
import { STAGE_ORDER } from "../lib/filtering";
import { staffRoleOf } from "../lib/projectTeamColumns";
import { ROLE_STYLES } from "./ProjectRoleChips";

/** The project list's read-only summary of one mandate; every change is made in the project itself. */
export function ProjectDrawer({ project, onClose }: { project: Project | null; onClose: () => void }) {
  const navigate = useNavigate();

  if (!project) return null;

  const currentStage = STAGE_ORDER.indexOf(project.stage);
  const gates = STAGE_ORDER.filter((stage) => stage !== "CLOSED");
  const staff = staffLeadsFirst(project.team);

  return (
    <Drawer open onClose={onClose} label={`${project.positionTitle} — ${project.clientName}`}>
      <div className="relative border-b border-line-soft px-5 pb-3.5 pt-[18px]">
        <DrawerCloseButton onClose={onClose} />
        <div className="font-mono text-[11px] font-medium uppercase tracking-[0.08em] text-text3">
          {project.clientName}
        </div>
        <div className="mt-1 text-[17px] font-semibold">{project.positionTitle}</div>
        <div className="mt-2.5">
          <StagePill stage={project.stage} />
        </div>
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

        <SectionLabel className="mt-[18px]">Project team</SectionLabel>
        <div className="overflow-hidden rounded-[10px] border border-line-soft">
          {staff.length === 0 ? (
            <EmptyRow>No one staffed yet</EmptyRow>
          ) : (
            staff.map((member) => (
              <div
                key={member.memberId}
                className="flex items-center gap-[11px] border-b border-line-soft px-[13px] py-[11px] last:border-b-0"
              >
                <Avatar id={member.memberId} name={member.fullName} src={member.avatarUrl} size="lg" />
                <div className="min-w-0 flex-1 truncate text-[13px] font-medium">{member.fullName}</div>
                <RoleChip role={staffRoleOf(member)} />
              </div>
            ))
          )}
        </div>

        <SectionLabel className="mt-[18px]">Client</SectionLabel>
        <div className="overflow-hidden rounded-[10px] border border-line-soft">
          <div className="flex items-center gap-[11px] px-[13px] py-[11px]">
            <CompanyLogo name={project.clientName} logo={project.clientLogoUrl} size={30} />
            <div className="min-w-0 flex-1 truncate text-[13px] font-medium">{project.clientName}</div>
            <Chip label="Hiring entity" className="border-line bg-panel2 text-text2" />
          </div>
          {project.representatives.length === 0 ? (
            <EmptyRow>No client contacts on this mandate</EmptyRow>
          ) : (
            project.representatives.map((representative) => (
              <RepresentativeRow key={representative.representativeId} representative={representative} />
            ))
          )}
        </div>

        <Link
          to={`/projects/${project.id}/team`}
          className="mt-3 inline-flex items-center gap-1.5 text-xs font-medium text-text2 hover:text-text hover:underline"
        >
          <Icon d={ICONS.settings} size={13} />
          Manage team &amp; client access in project settings
        </Link>
      </div>
    </Drawer>
  );
}

function staffLeadsFirst(team: TeamMember[]): TeamMember[] {
  const staff = team.filter((member) => member.projectRoles.some((role) => role !== "CLIENT"));
  return staff.sort((a, b) => Number(staffRoleOf(b) === "LEAD") - Number(staffRoleOf(a) === "LEAD"));
}

const REPRESENTATIVE_STATUS: Record<AttachedRepresentative["status"], { label: string; className: string }> = {
  ACTIVE: { label: "Active", className: "border-transparent bg-green-dim text-green" },
  INVITED: { label: "Invite sent", className: "border-transparent bg-amber-dim text-amber" },
};

function Chip({ label, className }: { label: string; className: string }) {
  return (
    <span
      className={`flex-none whitespace-nowrap rounded-full border px-2 py-[3px] font-mono text-[9px] font-semibold uppercase tracking-[0.05em] ${className}`}
    >
      {label}
    </span>
  );
}

function RoleChip({ role }: { role: StaffRole }) {
  const { label, on } = ROLE_STYLES[role];
  return <Chip label={label} className={on} />;
}

function RepresentativeRow({ representative }: { representative: AttachedRepresentative }) {
  return (
    <div className="flex items-center gap-[11px] border-t border-line-soft px-[13px] py-[11px]">
      <Avatar id={representative.representativeId} name={representative.fullName} size="lg" />
      <div className="min-w-0 flex-1">
        <div className="truncate text-[13px] font-medium">{representative.fullName}</div>
        <div className="mt-0.5 truncate font-mono text-[11px] text-text3">
          {[representative.position, representative.email].filter(Boolean).join(" · ")}
        </div>
      </div>
      <Chip
        label={REPRESENTATIVE_STATUS[representative.status].label}
        className={REPRESENTATIVE_STATUS[representative.status].className}
      />
    </div>
  );
}

function EmptyRow({ children }: { children: string }) {
  return (
    <div className="border-t border-line-soft px-[13px] py-[11px] font-mono text-[11px] text-text3 first:border-t-0">
      {children}
    </div>
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
