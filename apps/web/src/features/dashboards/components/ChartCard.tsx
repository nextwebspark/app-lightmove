import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { cn } from "../../../lib/cn";
import type { ChartAnswer, ChartQueryDef, DashboardScope, DashboardWidget } from "../api/types";
import { CHART_LABELS, defaultTitle, optionSummary } from "../lib/chartRules";
import { useRelabel } from "../lib/useRelabel";
import { DashboardChart } from "./DashboardChart";

/**
 * One chart on a dashboard, drawn from its answer in the dashboard's batch. A chart whose query left the library, or whose type no longer fits its
 * answer, says so rather than drawing something it cannot stand behind.
 */
export function ChartCard({
  scope,
  widget,
  query,
  answer,
  isPending,
  isError,
  actions,
  className,
}: {
  scope: DashboardScope;
  widget: DashboardWidget;
  query: ChartQueryDef | undefined;
  /** Undefined while the batch is in flight or when it failed. */
  answer: ChartAnswer | undefined;
  isPending: boolean;
  isError: boolean;
  /** The card's controls: edit chart in view mode, edit and remove in edit mode. */
  actions?: ReactNode;
  className?: string;
}) {
  const relabel = useRelabel();
  const result = answer?.result ?? undefined;
  const askWhom = scope.kind === "project" ? "the position's lead" : "an admin";
  const title = widget.title || (query ? relabel(defaultTitle(query, widget.options)) : "Chart");
  const caption = query ? relabel(optionSummary(query, widget.options)) : "";
  const fits = result?.allowedCharts.includes(widget.chart) ?? true;

  return (
    <section aria-label={title} className={cn("relative flex h-full min-w-0 flex-col rounded-u-card border border-u-border bg-u-surface p-5", className)}>
      <div className="flex items-start justify-between gap-2.5">
        <div className="min-w-0">
          <h3 className="text-lead font-bold">{title}</h3>
          {caption && <p className="mt-0.5 text-note text-u-text3">{caption}</p>}
        </div>
        {actions && <div className="flex flex-none items-center gap-1">{actions}</div>}
      </div>
      {/* An unknown query is retired only once the reads have settled: a library that failed to load knows no query,
          and told the reader to remove charts that were fine. */}
      {answer?.code === "CHART_QUERY_NOT_FOUND" || (query === undefined && !isPending && !isError) ? (
        <Unavailable>This chart is no longer available. Remove it, or pick a replacement from the library.</Unavailable>
      ) : isPending ? (
        <div className="mt-4">
          <LinesSkeleton lines={4} />
        </div>
      ) : isError || !result ? (
        <Unavailable>{`Couldn't load this chart. Reload the page, and ask ${askWhom} if it keeps happening.`}</Unavailable>
      ) : !fits ? (
        <Unavailable>{`This data no longer fits the ${CHART_LABELS[widget.chart]} chart type. Use the pencil to pick another way to show it.`}</Unavailable>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col overflow-auto">
          <DashboardChart result={{ ...result, dimension: relabel(result.dimension) }} chart={widget.chart} />
        </div>
      )}
    </section>
  );
}

function Unavailable({ children }: { children: ReactNode }) {
  return (
    <div className="mt-3 flex items-center gap-2.5 rounded-u-chip bg-u-raised p-3.5 text-note text-u-text2">
      <Icon d={ICONS.info} size={16} className="flex-none text-u-text3" />
      {children}
    </div>
  );
}
