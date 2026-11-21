import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, EmptyState, useToast } from "../../../components/ui";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { cn } from "../../../lib/cn";
import * as dashboardsApi from "../api/dashboardsApi";
import type { Dashboard, DashboardScope } from "../api/types";
import { DashboardNameDialog } from "../components/DashboardNameDialog";
import { DASHBOARD_PARAM } from "../components/DashboardRailSection";
import { DashboardView } from "../components/DashboardView";
import { useDashboardWrites } from "../lib/useDashboardWrites";

const SCOPE: DashboardScope = { kind: "workspace" };
/** The page runs edge to edge under a fixed tab row, as Strategy does; the states without one keep the usual gutter. */
const PADDED = "px-4 pb-[60px] pt-5 sm:px-7 sm:pt-7";

/** Workspace → Dashboards: the member's own and shared dashboards as tabs, + for a new one. Staff only. */
export function WorkspaceDashboardsPage() {
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [naming, setNaming] = useState(false);
  const { create } = useDashboardWrites(SCOPE);
  const list = useQuery({
    queryKey: dashboardsApi.DASHBOARDS_KEY(SCOPE),
    queryFn: ({ signal }) => dashboardsApi.listDashboards(SCOPE, signal),
  });
  const library = useQuery({
    queryKey: dashboardsApi.CHART_LIBRARY_KEY(SCOPE),
    queryFn: ({ signal }) => dashboardsApi.getChartLibrary(SCOPE, signal),
    staleTime: Infinity,
  });
  const open = (id: string) => setSearchParams({ [DASHBOARD_PARAM]: id });
  const heading = <h1 className="sr-only">Dashboards</h1>;
  const dialog = naming && (
    <DashboardNameDialog
      title="New dashboard"
      subtitle="Only you see it until you share it with the workspace team."
      submitLabel="Create and add charts"
      pending={create.isPending}
      onClose={() => setNaming(false)}
      onSubmit={(name) =>
        create.mutate({ name, shared: false, widgets: [], version: null }, {
          onSuccess: (created) => {
            setNaming(false);
            toast.success("Dashboard created");
            open(created.id);
          },
        })
      }
    />
  );

  if (list.isPending || library.isPending) {
    return (
      <div className={PADDED}>
        {heading}
        <LinesSkeleton lines={6} />
      </div>
    );
  }
  if (list.isError || library.isError) {
    return (
      <div className={PADDED}>
        {heading}
        <EmptyState
          icon={<Icon d={ICONS.lock} size={24} />}
          title="Couldn't load your dashboards"
          body="You may no longer have access to them, or the request failed. Reload the page, and ask an admin if it keeps happening."
        />
      </div>
    );
  }
  const dashboards = list.data;
  if (dashboards.length === 0) {
    return (
      <div className={PADDED}>
        {heading}
        <EmptyState
          icon={<Icon d={ICONS.dashboard} size={24} />}
          title="No dashboards yet"
          body="Build one from figures across every open position: pipeline by stage, research pace, positions at risk."
        >
          <Button onClick={() => setNaming(true)}>
            <Icon d={ICONS.plus} size={15} />
            New dashboard
          </Button>
        </EmptyState>
        {dialog}
      </div>
    );
  }
  const current = dashboards.find((item) => item.id === searchParams.get(DASHBOARD_PARAM)) ?? dashboards[0];

  return (
    <>
      {heading}
      <DashboardView
        key={current.id}
        scope={SCOPE}
        dashboard={current}
        library={library.data}
        liveNote="live across every open position"
        sharedWith="the workspace team"
        onOpen={open}
        onDeleted={() => setSearchParams({})}
        tabs={<DashboardTabs dashboards={dashboards} activeId={current.id} onOpen={open} onNew={() => setNaming(true)} />}
      />
      {dialog}
    </>
  );
}

function DashboardTabs({
  dashboards,
  activeId,
  onOpen,
  onNew,
}: {
  dashboards: Dashboard[];
  activeId: string;
  onOpen: (id: string) => void;
  onNew: () => void;
}) {
  return (
    <nav aria-label="Your dashboards" className="flex items-end gap-1 overflow-x-auto">
      {dashboards.map((dashboard) => {
        const isActive = dashboard.id === activeId;
        return (
          <button
            key={dashboard.id}
            type="button"
            aria-current={isActive ? "page" : undefined}
            title={dashboard.name}
            onClick={() => onOpen(dashboard.id)}
            className={cn(
              "inline-flex max-w-[220px] flex-none items-center gap-1.5 whitespace-nowrap rounded-t-u-chip border px-4 text-body transition-colors",
              isActive
                ? "border-u-border border-b-u-surface bg-u-surface py-2.5 font-semibold text-u-text shadow-[inset_0_2px_0_var(--color-u-accent)]"
                : "mb-px border-transparent bg-u-sunken py-2 text-u-text2 hover:bg-u-raised hover:text-u-text",
            )}
          >
            <span className="min-w-0 truncate">{dashboard.name}</span>
            {dashboard.shared && (
              <Icon d={ICONS.team} size={13} className={cn("flex-none", isActive ? "text-u-accent" : "text-u-text3")} />
            )}
          </button>
        );
      })}
      <button
        type="button"
        aria-label="New dashboard"
        title="New dashboard"
        onClick={onNew}
        className="mb-1 ml-1 grid size-8 flex-none place-items-center rounded-u-control text-u-text2 hover:bg-u-raised hover:text-u-accent"
      >
        <Icon d={ICONS.plus} size={15} />
      </button>
    </nav>
  );
}
