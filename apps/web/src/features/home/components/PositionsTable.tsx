import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { HealthPill, stageLabel } from "../../../components/ui/Badge";
import { cn } from "../../../lib/cn";
import { formatShortDate } from "../../../lib/datetime/format";
import { dayKeyOf } from "../../../lib/datetime/zone";
import type { Project } from "../../projects/api/types";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import { nextMilestoneOf } from "../lib/milestone";
import { CardEmpty, HomeCard } from "./HomeCard";

const HEALTH_RANK = { OFF: 0, RISK: 1, OK: 2, DONE: 3 } as const;

const COLUMNS = "md:grid-cols-[minmax(0,2.2fr)_minmax(0,1fr)_minmax(0,0.9fr)_minmax(0,1.6fr)_minmax(0,0.7fr)]";

const deadlineOf = (project: Project) => project.deliveryDate ?? project.targetDate;

/** Your positions: the live ones the caller is on, off track and at risk first, each with its next dated step. */
export function PositionsTable({
  positions,
  onNewPosition,
  now = new Date(),
}: {
  positions: Project[];
  onNewPosition: () => void;
  now?: Date;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const today = dayKeyOf(now);
  const rows = [...positions].sort(
    (left, right) =>
      HEALTH_RANK[left.health] - HEALTH_RANK[right.health] ||
      (deadlineOf(left) ?? "9999").localeCompare(deadlineOf(right) ?? "9999"),
  );
  const atRisk = positions.filter((project) => project.health === "RISK" || project.health === "OFF").length;

  return (
    <HomeCard
      title="Your positions"
      badge={
        <span className="flex items-center gap-2 text-note font-normal">
          {positions.length > 0 && <span className="text-u-text3">{positions.length} active</span>}
          {atRisk > 0 && (
            <>
              <span aria-hidden="true" className="text-u-text3">·</span>
              <span className="text-u-signal">{atRisk} at risk</span>
            </>
          )}
        </span>
      }
      aside={
        <Link to="/positions" className="inline-flex items-center gap-1 text-note font-medium text-u-accent hover:underline">
          View all positions
          <Icon d={ICONS.arrowRight} size={12} />
        </Link>
      }
      footer={
        rows.length === 0 ? undefined : (
        <>
          <span className="text-u-text3">Showing positions you are on</span>
          <Link to="/dashboards" className="ml-auto font-medium text-u-accent hover:underline">
            View dashboards
          </Link>
        </>
        )
      }
    >
      {rows.length === 0 ? (
        <CardEmpty
          icon={ICONS.briefcase}
          title="You're not on any position yet"
          body="When a lead adds you to a position, it appears here with its stage, health and next milestone. You can also open one yourself."
          action={
            <Button size="sm" onClick={onNewPosition}>
              <Icon d={ICONS.plus} size={14} />
              New position
            </Button>
          }
        />
      ) : (
        <div role="table" aria-label="Positions">
          <div
            role="row"
            className={cn("hidden gap-4 border-y border-u-border px-5 py-2 type-micro-label text-u-text3 md:grid", COLUMNS)}
          >
            <span role="columnheader">Position / {vocabulary.unit}</span>
            <span role="columnheader">Stage</span>
            <span role="columnheader">Health</span>
            <span role="columnheader">Next milestone</span>
            <span role="columnheader">Due</span>
          </div>
          {rows.map((project, index) => {
            const milestone = nextMilestoneOf(project, today);
            return (
              <div
                key={project.id}
                role="row"
                className={cn(
                  "relative grid gap-1.5 px-5 py-3 transition-colors hover:bg-u-raised md:items-center md:gap-4",
                  COLUMNS,
                  index > 0 && "border-t border-u-border",
                  index === 0 && "border-t border-u-border md:border-t-0",
                )}
              >
                <span role="cell" className="min-w-0">
                  <Link
                    to={`/projects/${project.id}`}
                    className="block truncate text-body font-medium text-u-text after:absolute after:inset-0 after:content-['']"
                  >
                    {project.positionTitle}
                  </Link>
                  <span className="block truncate text-meta text-u-text3">{project.clientName}</span>
                </span>
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 md:contents">
                  <span role="cell" className="text-note text-u-text2">{stageLabel(project.stage)}</span>
                  <span role="cell">
                    <HealthPill health={project.health} />
                  </span>
                  <span role="cell" className="min-w-0 basis-full md:basis-auto">
                    {milestone ? (
                      <>
                        <span className={cn("block truncate text-note font-medium", milestone.overdue ? "text-u-signal" : "text-u-text")}>
                          {milestone.label}
                        </span>
                        <span className="block truncate text-meta text-u-text3">{milestone.when}</span>
                      </>
                    ) : (
                      <span className="text-note text-u-text3">—</span>
                    )}
                  </span>
                  <span role="cell" className="hidden whitespace-nowrap text-note text-u-text2 md:block">
                    {formatShortDate(deadlineOf(project)) ?? "—"}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </HomeCard>
  );
}
