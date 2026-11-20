import { useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { cn } from "../../../lib/cn";
import * as dashboardsApi from "../api/dashboardsApi";
import type { ChartQueryDef, DashboardScope, DashboardWidget } from "../api/types";
import { defaultTitle, optionSummary } from "../lib/chartRules";
import { useRelabel } from "../lib/useRelabel";
import { DashboardChart } from "./DashboardChart";

/**
 * One chart on a dashboard, answered live. A chart whose query left the library, or whose type no longer fits its
 * answer, says so rather than drawing something it cannot stand behind.
 */
export function ChartCard({
  scope,
  widget,
  query,
  actions,
  className,
}: {
  scope: DashboardScope;
  widget: DashboardWidget;
  query: ChartQueryDef | undefined;
  /** The card's controls: edit chart in view mode, move/size/remove in edit mode. */
  actions?: ReactNode;
  className?: string;
}) {
  const relabel = useRelabel();
  const { data: result, isPending, isError } = useQuery({
    queryKey: dashboardsApi.CHART_RESULT_KEY(scope, widget.queryId, widget.options),
    queryFn: ({ signal }) => dashboardsApi.runChartQuery(scope, widget.queryId, widget.options, signal),
    enabled: query !== undefined,
    staleTime: 0,
  });
  const title = widget.title || (query ? relabel(defaultTitle(query, widget.options)) : "Chart");
  const caption = query ? relabel(optionSummary(query, widget.options)) : "";
  const fits = result?.allowedCharts.includes(widget.chart) ?? true;

  return (
    <section aria-label={title} className={cn("relative min-w-0 rounded-u-card border border-u-border bg-u-surface p-5", className)}>
      <div className="flex items-start justify-between gap-2.5">
        <div className="flex min-w-0 items-start gap-2">
          <div className="min-w-0">
            <h3 className="text-lead font-bold">{title}</h3>
            {caption && <p className="mt-0.5 text-note text-u-text3">{caption}</p>}
          </div>
        </div>
        {actions && <div className="flex flex-none items-center gap-1">{actions}</div>}
      </div>
      {query === undefined ? (
        <Unavailable>This chart is no longer available. Remove it, or pick a replacement from the library.</Unavailable>
      ) : isPending ? (
        <div className="mt-4">
          <LinesSkeleton lines={4} />
        </div>
      ) : isError ? (
        <Unavailable>Couldn't load this chart. Reload the page, and ask the position's lead if it keeps happening.</Unavailable>
      ) : !fits ? (
        <Unavailable>This data no longer fits a {widget.chart} chart. Use the pencil to pick another way to show it.</Unavailable>
      ) : (
        <DashboardChart result={{ ...result, dimension: relabel(result.dimension) }} chart={widget.chart} />
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
