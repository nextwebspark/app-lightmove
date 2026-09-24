import { useInfiniteQuery } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, Drawer, StagePill } from "../../../components/ui";
import { DrawerCloseButton } from "../../../components/ui/Drawer";
import { cn } from "../../../lib/cn";
import { formatDate, formatNumber } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import { projectActivity, projectActivityKey } from "../api/projectsApi";
import type {
  AttachedRepresentative,
  Project,
  ProjectHealth,
  ProjectStage,
  StaffRole,
  TeamMember,
} from "../api/types";
import { canExecuteProjectWork } from "../lib/access";
import { activityLines, formatActivityTime } from "../lib/activity";
import { projectProgress } from "../lib/projectProgress";
import { staffRoleOf } from "../lib/projectTeamColumns";

/**
 * The projects list's read-only summary of one position (Workspace.dc.html's "Position drawer"):
 * mapping progress, key metrics, stage gates, the team and hiring managers, and recent activity.
 * Every change is made in the position itself.
 */
export function ProjectDrawer({ project, onClose }: { project: Project | null; onClose: () => void }) {
  if (!project) return null;
  return <ProjectDrawerPanel key={project.id} project={project} onClose={onClose} />;
}

function ProjectDrawerPanel({ project, onClose }: { project: Project; onClose: () => void }) {
  const { user } = useAuth();
  const isStaff = canExecuteProjectWork(project, user?.id, user?.workspace?.roles);
  const staff = staffLeadsFirst(project.team);

  return (
    <Drawer open onClose={onClose} label={`${project.positionTitle} — ${project.clientName}`}>
      <div className="relative border-b border-u-border px-5 pb-3.5 pt-[18px]">
        <DrawerCloseButton onClose={onClose} />
        <div className="pe-8 text-meta font-medium uppercase tracking-[0.08em] text-u-text3">{project.clientName}</div>
        <div className="mt-1 pe-8 text-subhead font-semibold text-u-text">{project.positionTitle}</div>
        <div className="mt-2.5 flex flex-wrap gap-1.5">
          <StagePill stage={project.stage} />
          <HealthPill health={project.health} />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-[18px]">
        <MappingProgress project={project} />

        <SectionLabel className="mt-[18px]">Key metrics</SectionLabel>
        <div className="grid grid-cols-2 gap-2.5">
          <MetricTile value={formatNumber(project.companies)} label="Universe companies" />
          <MetricTile value={formatNumber(project.candidates)} label="Executives mapped" />
          <MetricTile value={formatNumber(project.engagedCandidates)} label="Engaged" />
          <MetricTile value={`${projectProgress(project).weeklyVelocity}/wk`} label="Mapping velocity" />
        </div>

        <SectionLabel className="mt-[18px]">Stage gates</SectionLabel>
        <StageGates stage={project.stage} />

        <SectionLabel className="mt-[18px]" action={isStaff ? <InviteLink projectId={project.id} /> : null}>
          Recruiting team
        </SectionLabel>
        <div className="overflow-hidden rounded-[10px] border border-u-border">
          {staff.length === 0 ? (
            <EmptyRow>No one staffed yet</EmptyRow>
          ) : (
            staff.map((member) => (
              <div
                key={member.memberId}
                className="flex items-center gap-[11px] border-b border-u-border px-[13px] py-[11px] last:border-b-0"
              >
                <Avatar id={member.memberId} name={member.fullName} src={member.avatarUrl} size="lg" />
                <div className="min-w-0 flex-1 truncate text-body font-medium text-u-text">{member.fullName}</div>
                <RoleChip role={staffRoleOf(member)} />
              </div>
            ))
          )}
        </div>

        <SectionLabel className="mt-[18px]" action={isStaff ? <InviteLink projectId={project.id} /> : null}>
          Hiring managers
        </SectionLabel>
        <div className="overflow-hidden rounded-[10px] border border-u-border">
          {project.representatives.length === 0 ? (
            <EmptyRow>No hiring managers on this position</EmptyRow>
          ) : (
            project.representatives.map((representative) => (
              <HiringManagerRow key={representative.representativeId} representative={representative} />
            ))
          )}
        </div>

        <Link
          to={`/projects/${project.id}/team`}
          className="mt-3 inline-flex items-center gap-1.5 text-note font-medium text-u-text2 hover:text-u-text hover:underline"
        >
          <Icon d={ICONS.settings} size={13} />
          Manage team &amp; hiring manager access in position settings
        </Link>

        {isStaff && <RecentActivity projectId={project.id} />}
      </div>

      <div className="flex-none border-t border-u-border px-5 py-3.5">
        <Link
          to={`/projects/${project.id}`}
          className="flex w-full items-center justify-center gap-2 rounded-md border border-u-accent-solid bg-u-accent-solid px-3.5 py-[11px] text-body font-semibold text-white transition hover:bg-u-accent-solid/90"
        >
          Open position
          <Icon d={ICONS.arrowRight} size={14} />
        </Link>
      </div>
    </Drawer>
  );
}

function MappingProgress({ project }: { project: Project }) {
  const { coveragePercent, daysRemaining } = projectProgress(project);
  const overdue = daysRemaining !== null && daysRemaining < 0;

  return (
    <>
      <SectionLabel action={<span className="font-u-num text-meta font-medium text-u-accent">{coveragePercent}%</span>}>
        Mapping progress
      </SectionLabel>
      <div
        role="progressbar"
        aria-label="Universe companies with an executive mapped"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={coveragePercent}
        className="h-1.5 overflow-hidden rounded-full border border-u-border bg-u-sunken"
      >
        <div
          className={cn("h-full rounded-full transition-[width]", overdue ? "bg-u-offlimits" : "bg-u-accent")}
          style={{ width: `${coveragePercent}%` }}
        />
      </div>
      <div className="mt-2 text-note text-u-text2">
        <b className="font-u-num font-medium text-u-text">{formatNumber(project.mappedCompanies)}</b> of{" "}
        <b className="font-u-num font-medium text-u-text">{formatNumber(project.companies)}</b>{" "}
        {project.companies === 1 ? "company" : "companies"} with an executive mapped
      </div>
      <div className="mt-2 flex items-baseline justify-between gap-3 text-meta text-u-text3">
        <span>Started: {formatDate(project.createdAt.slice(0, 10))}</span>
        <span>Target: {project.targetDate ? formatDate(project.targetDate) : "not set"}</span>
      </div>
      {daysRemaining !== null && project.health !== "DONE" && (
        <div className={cn("mt-1.5 flex items-center gap-1.5 text-meta font-semibold", daysRemainingColor(daysRemaining))}>
          <Icon d={ICONS.calendar} size={12} />
          {daysRemaining >= 0
            ? `${daysRemaining} ${daysRemaining === 1 ? "day" : "days"} remaining`
            : `${-daysRemaining} ${daysRemaining === -1 ? "day" : "days"} overdue`}
        </div>
      )}
      <HealthWarning project={project} coveragePercent={coveragePercent} daysRemaining={daysRemaining} />
    </>
  );
}

function daysRemainingColor(daysRemaining: number): string {
  if (daysRemaining < 0) return "text-u-offlimits";
  if (daysRemaining <= 14) return "text-u-signal";
  return "text-u-text2";
}

function HealthWarning({
  project,
  coveragePercent,
  daysRemaining,
}: {
  project: Project;
  coveragePercent: number;
  daysRemaining: number | null;
}) {
  if (daysRemaining === null) return null;
  if (project.health !== "RISK" && project.health !== "OFF") return null;

  const overdue = daysRemaining < 0;
  const text = overdue
    ? `The target date passed ${-daysRemaining} ${daysRemaining === -1 ? "day" : "days"} ago. Move it or close the position.`
    : `The target is ${daysRemaining} ${daysRemaining === 1 ? "day" : "days"} away and ${coveragePercent}% of the universe is mapped.`;

  return (
    <div
      className={cn(
        "mt-4 flex gap-[9px] rounded-lg px-[13px] py-[11px]",
        overdue ? "bg-u-offlimits-tint" : "bg-u-signal-tint",
      )}
    >
      <Icon
        d={ICONS.warning}
        size={15}
        className={cn("mt-px flex-none", overdue ? "text-u-offlimits" : "text-u-signal")}
      />
      <span className="text-note text-u-text">{text}</span>
    </div>
  );
}

/** The five gates a mandate walks. LOCKED reads as Universe and CLOSED as past them all. */
const GATES: { stage: ProjectStage; label: string }[] = [
  { stage: "BRIEF", label: "Brief" },
  { stage: "UNIVERSE", label: "Universe" },
  { stage: "MAPPING", label: "Mapping" },
  { stage: "OUTREACH", label: "Outreach" },
  { stage: "DELIVERED", label: "Delivered" },
];

function currentGateOf(stage: ProjectStage): number {
  if (stage === "LOCKED") return 1;
  if (stage === "CLOSED") return GATES.length;
  return GATES.findIndex((gate) => gate.stage === stage);
}

function StageGates({ stage }: { stage: ProjectStage }) {
  const current = currentGateOf(stage);
  return (
    <ol className="flex items-start" aria-label="Stage gates">
      {GATES.map((gate, index) => {
        const done = index < current;
        const now = index === current;
        return (
          <li key={gate.stage} className="flex flex-1 items-start last:flex-none" aria-current={now ? "step" : undefined}>
            <div className="flex w-[58px] flex-none flex-col items-center gap-1.5">
              <span
                className={cn(
                  "grid size-4 place-items-center rounded-full border-[1.5px]",
                  done ? "border-u-direct bg-u-direct-tint" : now ? "border-u-accent" : "border-u-border-strong",
                )}
              >
                {done ? (
                  <Icon d={ICONS.check} size={10} className="text-u-direct" />
                ) : (
                  <span className={cn("size-1.5 rounded-full", now && "bg-u-accent")} />
                )}
              </span>
              <span
                className={cn(
                  "text-center text-eyebrow uppercase tracking-[0.04em]",
                  now ? "font-semibold text-u-accent" : done ? "text-u-text2" : "text-u-text3",
                )}
              >
                {gate.label}
              </span>
            </div>
            {index < GATES.length - 1 && (
              <div className={cn("mx-0.5 mt-2 h-[1.5px] min-w-0 flex-1", done ? "bg-u-direct" : "bg-u-border-strong")} />
            )}
          </li>
        );
      })}
    </ol>
  );
}

const INITIAL_ACTIVITY_LINES = 4;
const MORE_ACTIVITY_LINES = 10;

function RecentActivity({ projectId }: { projectId: string }) {
  const [visible, setVisible] = useState(INITIAL_ACTIVITY_LINES);
  const activity = useInfiniteQuery({
    queryKey: projectActivityKey(projectId),
    queryFn: ({ pageParam }) => projectActivity(projectId, pageParam),
    initialPageParam: null as number | null,
    getNextPageParam: (last) => last.nextCursor,
  });

  const lines = activityLines(activity.data?.pages.flatMap((page) => page.entries) ?? []);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = activity;

  // A page can collapse into fewer lines than it has entries, so keep reading until the lines asked
  // for exist or the feed runs out.
  useEffect(() => {
    if (lines.length < visible && hasNextPage && !isFetchingNextPage) void fetchNextPage();
  }, [lines.length, visible, hasNextPage, isFetchingNextPage, fetchNextPage]);

  const canShowMore = lines.length > visible || Boolean(hasNextPage);

  return (
    <>
      <SectionLabel className="mt-[18px]">Recent activity</SectionLabel>
      {activity.isPending ? (
        <div className="text-meta text-u-text3">Loading activity…</div>
      ) : activity.isError ? (
        <div className="text-meta text-u-text3">Activity could not be loaded.</div>
      ) : lines.length === 0 ? (
        <div className="text-meta text-u-text3">No recent activity</div>
      ) : (
        <>
          <ul className="flex flex-col gap-2.5">
            {lines.slice(0, visible).map((line) => (
              <li key={line.key} className="border-l-2 border-u-border-strong pl-2.5">
                <div className="text-note text-u-text">
                  <span className="font-semibold">{firstNameOf(line.actorName)}</span> {line.text}
                </div>
                <div className="mt-0.5 text-meta text-u-text3">
                  <time dateTime={line.occurredAt}>{formatActivityTime(line.occurredAt)}</time>
                </div>
              </li>
            ))}
          </ul>
          {canShowMore && (
            <button
              type="button"
              onClick={() => setVisible((shown) => shown + MORE_ACTIVITY_LINES)}
              disabled={isFetchingNextPage}
              className="mt-3 text-note font-medium text-u-accent hover:underline disabled:opacity-60"
            >
              {isFetchingNextPage ? "Loading…" : "See more"}
            </button>
          )}
        </>
      )}
    </>
  );
}

function firstNameOf(fullName: string): string {
  return fullName.split(/\s+/)[0] || fullName;
}

function staffLeadsFirst(team: TeamMember[]): TeamMember[] {
  const staff = team.filter((member) => member.projectRoles.some((role) => role !== "CLIENT"));
  return staff.sort((a, b) => Number(staffRoleOf(b) === "LEAD") - Number(staffRoleOf(a) === "LEAD"));
}

const HEALTH_PILL: Record<ProjectHealth, { label: string; className: string }> = {
  OK: { label: "On track", className: "bg-u-direct-tint text-u-direct" },
  RISK: { label: "At risk", className: "bg-u-signal-tint text-u-signal" },
  OFF: { label: "Off track", className: "bg-u-offlimits-tint text-u-offlimits" },
  DONE: { label: "Complete", className: "bg-u-raised text-u-text3" },
};

function HealthPill({ health }: { health: ProjectHealth }) {
  const { label, className } = HEALTH_PILL[health];
  return (
    <span
      className={cn(
        "inline-flex items-center whitespace-nowrap rounded-md px-[9px] py-[3px] text-[10.5px] font-semibold uppercase tracking-[0.06em]",
        className,
      )}
    >
      {label}
    </span>
  );
}

const ROLE_CHIP: Record<StaffRole, { label: string; className: string }> = {
  LEAD: { label: "Lead", className: "border-transparent bg-u-accent-tint text-u-accent" },
  RESEARCHER: { label: "Researcher", className: "border-u-border-strong bg-u-raised text-u-text2" },
};

const REPRESENTATIVE_STATUS: Record<AttachedRepresentative["status"], { label: string; className: string }> = {
  ACTIVE: { label: "Active", className: "border-transparent bg-u-direct-tint text-u-direct" },
  INVITED: { label: "Invite sent", className: "border-transparent bg-u-accent-tint text-u-accent" },
};

function Chip({ label, className }: { label: string; className: string }) {
  return (
    <span
      className={cn(
        "flex-none whitespace-nowrap rounded-full border px-2 py-[3px] text-[9px] font-semibold uppercase tracking-[0.05em]",
        className,
      )}
    >
      {label}
    </span>
  );
}

function RoleChip({ role }: { role: StaffRole }) {
  return <Chip {...ROLE_CHIP[role]} />;
}

function HiringManagerRow({ representative }: { representative: AttachedRepresentative }) {
  return (
    <div className="flex items-center gap-[11px] border-b border-u-border px-[13px] py-[11px] last:border-b-0">
      <Avatar id={representative.representativeId} name={representative.fullName} size="lg" />
      <div className="min-w-0 flex-1">
        <div className="truncate text-body font-medium text-u-text">{representative.fullName}</div>
        <div className="mt-0.5 truncate text-meta text-u-text3">
          {[representative.position, representative.email].filter(Boolean).join(" · ")}
        </div>
      </div>
      <Chip {...REPRESENTATIVE_STATUS[representative.status]} />
    </div>
  );
}

function InviteLink({ projectId }: { projectId: string }) {
  return (
    <Link
      to={`/projects/${projectId}/team`}
      className="text-note font-medium normal-case tracking-normal text-u-accent hover:underline"
    >
      + Invite
    </Link>
  );
}

function EmptyRow({ children }: { children: string }) {
  return <div className="px-[13px] py-[11px] text-meta text-u-text3">{children}</div>;
}

function SectionLabel({
  children,
  action,
  className = "",
}: {
  children: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("mb-2 flex items-baseline justify-between gap-3", className)}>
      <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-u-text3">{children}</span>
      {action}
    </div>
  );
}

function MetricTile({ value, label }: { value: string; label: string }) {
  return (
    <div className="rounded-xl border border-u-border bg-u-raised px-3 py-2.5">
      <b className="block font-u-num text-subhead font-medium text-u-text">{value}</b>
      <span className="text-[10.5px] uppercase tracking-[0.06em] text-u-text3">{label}</span>
    </div>
  );
}
