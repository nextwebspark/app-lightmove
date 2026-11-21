import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { LinesSkeleton } from "../../../components/ui";
import { HealthPill } from "../../../components/ui/Badge";
import { cn } from "../../../lib/cn";
import { formatInstantTime, formatShortDate } from "../../../lib/datetime/format";
import * as dashboardsApi from "../../dashboards/api/dashboardsApi";
import { positionPipelinePath } from "../../pipeline/lib/pipelinePath";
import type { Project } from "../../projects/api/types";
import { isActive } from "../../projects/lib/filtering";
import type { TaskResponse } from "../../tasks/api/types";

const WORKSPACE = { kind: "workspace" } as const;

function Card({ title, aside, children }: { title: string; aside?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section aria-label={title} className="overflow-hidden rounded-u-card border border-u-border-strong bg-u-surface">
      <div className="flex items-center gap-2 border-b border-u-border px-4 py-3">
        <h2 className="text-body font-semibold">{title}</h2>
        {aside && <div className="ml-auto">{aside}</div>}
      </div>
      {children}
    </section>
  );
}

/** Today: the calls and meetings set for today, soonest first. */
export function TodaySection({ agenda, pending }: { agenda: TaskResponse[]; pending: boolean }) {
  return (
    <Card title="Today">
      {pending ? (
        <div className="p-4">
          <LinesSkeleton lines={2} />
        </div>
      ) : agenda.length === 0 ? (
        <p className="px-4 py-4 text-note text-u-text3">No calls or meetings today.</p>
      ) : (
        <ul>
          {agenda.map((task, index) => (
            <li key={task.id} className={cn(index > 0 && "border-t border-u-border")}>
              <Link
                to={task.candidateId ? positionPipelinePath(task.projectId, { candidate: task.candidateId }) : positionPipelinePath(task.projectId)}
                className="grid grid-cols-[48px_minmax(0,1fr)] gap-2.5 px-4 py-2.5 hover:bg-u-raised"
              >
                <span className="pt-px font-mono text-note text-u-accent">{formatInstantTime(task.dueAt) ?? "—"}</span>
                <span className="min-w-0">
                  <span className="block text-body font-medium">{[task.title, task.personName].filter(Boolean).join(" · ")}</span>
                  <span className="block truncate text-meta text-u-text3">{task.projectTitle}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

/** Your positions: the ones the caller holds a seat on, the ones that need attention first. */
export function PositionsSection({ positions, myUserId }: { positions: Project[]; myUserId: string | undefined }) {
  const RANK = { OFF: 0, RISK: 1, OK: 2, DONE: 3 } as const;
  const mine = positions
    .filter((project) => isActive(project) && project.team.some((seat) => seat.userId === myUserId))
    .sort((left, right) => RANK[left.health] - RANK[right.health] || (left.targetDate ?? "").localeCompare(right.targetDate ?? ""));
  return (
    <Card
      title="Your positions"
      aside={
        <Link to="/positions" className="text-meta font-medium text-u-text3 hover:text-u-text">
          View all →
        </Link>
      }
    >
      {mine.length === 0 ? (
        <p className="px-4 py-4 text-note text-u-text3">When a lead adds you to a position, it appears here.</p>
      ) : (
        <ul>
          {mine.slice(0, 5).map((project, index) => (
            <li key={project.id} className={cn(index > 0 && "border-t border-u-border")}>
              <Link to={`/projects/${project.id}`} className="block px-4 py-2.5 hover:bg-u-raised">
                <span className="flex items-center gap-2">
                  <span className="min-w-0 flex-1 truncate text-body font-medium">{project.positionTitle}</span>
                  <HealthPill health={project.health} />
                </span>
                <span className="mt-0.5 block truncate text-meta text-u-text3">
                  {[
                    project.clientName,
                    `${project.companies} companies · ${project.candidates} executives`,
                    project.targetDate ? `Due ${formatShortDate(project.targetDate)}` : null,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

/** Pinned charts: workspace headline figures from the dashboard library, as tiles. */
export function ChartsSection({
  queryIds,
  labels,
  onPin,
}: {
  queryIds: string[];
  labels: Record<string, string>;
  onPin: () => void;
}) {
  return (
    <section aria-label="Pinned charts" className="grid grid-cols-2 gap-2.5">
      {queryIds.map((queryId) => (
        <ChartTile key={queryId} queryId={queryId} label={labels[queryId]} />
      ))}
      <button
        type="button"
        onClick={onPin}
        className="flex min-h-[96px] flex-col items-center justify-center gap-1 rounded-u-card border border-dashed border-u-border-strong text-note font-medium text-u-text3 hover:border-u-text3 hover:text-u-text"
      >
        <Icon d={ICONS.plus} size={16} />
        Pin a chart
      </button>
    </section>
  );
}

function ChartTile({ queryId, label }: { queryId: string; label: string | undefined }) {
  const { data, isPending, isError } = useQuery({
    queryKey: dashboardsApi.CHART_RESULT_KEY(WORKSPACE, queryId, {}),
    queryFn: ({ signal }) => dashboardsApi.runChartQuery(WORKSPACE, queryId, {}, signal),
    staleTime: 60_000,
  });
  return (
    <div className="flex min-w-0 flex-col gap-0.5 rounded-u-card border border-u-border-strong bg-u-surface px-3.5 py-3">
      {isPending ? (
        <LinesSkeleton lines={2} />
      ) : isError ? (
        <span className="text-meta text-u-text3">Couldn't load this chart</span>
      ) : !data.kpi ? (
        <span className="text-meta text-u-text3">This chart is no longer available</span>
      ) : (
        <>
          <span className="truncate text-meta font-medium text-u-text3">{label ?? data.measure}</span>
          <span className={cn("font-mono text-title font-medium", data.kpi.alarm && "text-u-signal")}>{data.kpi.value}</span>
          {data.kpi.sub && <span className="text-meta text-u-text3">{data.kpi.sub}</span>}
        </>
      )}
    </div>
  );
}
