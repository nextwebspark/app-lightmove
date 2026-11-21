import { Link } from "react-router-dom";
import { LinesSkeleton } from "../../../components/ui";
import { HealthPill } from "../../../components/ui/Badge";
import { cn } from "../../../lib/cn";
import { formatInstantTime, formatShortDate } from "../../../lib/datetime/format";
import type { Project } from "../../projects/api/types";
import type { TaskResponse } from "../../tasks/api/types";
import { taskPath } from "../lib/taskPath";

const HEALTH_RANK = { OFF: 0, RISK: 1, OK: 2, DONE: 3 } as const;

function Card({ title, aside, children }: { title: string; aside?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section aria-label={title} className="flex h-full flex-col overflow-hidden rounded-u-card border border-u-border-strong bg-u-surface">
      <div className="flex flex-none items-center gap-2 border-b border-u-border px-4 py-3">
        <h2 className="text-body font-semibold">{title}</h2>
        {aside && <div className="ml-auto">{aside}</div>}
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
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
                to={taskPath(task)}
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

/** Your positions: the active ones the caller holds a seat on, the ones that need attention first. */
export function PositionsSection({ positions }: { positions: Project[] }) {
  const mine = [...positions].sort(
    (left, right) =>
      HEALTH_RANK[left.health] - HEALTH_RANK[right.health] || (left.targetDate ?? "").localeCompare(right.targetDate ?? ""),
  );
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
          {mine.map((project, index) => (
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
