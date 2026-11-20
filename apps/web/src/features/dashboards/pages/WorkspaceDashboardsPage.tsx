import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, EmptyState, useToast } from "../../../components/ui";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { cn } from "../../../lib/cn";
import * as dashboardsApi from "../api/dashboardsApi";
import type { DashboardScope } from "../api/types";
import { DashboardNameDialog } from "../components/DashboardNameDialog";
import { DASHBOARD_PARAM } from "../components/DashboardRailSection";
import { DashboardView } from "../components/DashboardView";
import { useDashboardWrites } from "../lib/useDashboardWrites";

const SCOPE: DashboardScope = { kind: "workspace" };

/** Workspace → Dashboards: the member's own and shared dashboards across every open position. Staff only. */
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
  const header = (
    <PageHeader
      title="Dashboards"
      subtitle="Figures across every open position, arranged your way"
      action={
        <Button size="sm" onClick={() => setNaming(true)}>
          <Icon d={ICONS.plus} size={15} />
          New dashboard
        </Button>
      }
    />
  );
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
      <>
        {header}
        <LinesSkeleton lines={6} />
      </>
    );
  }
  if (list.isError || library.isError) {
    return (
      <>
        {header}
        <EmptyState
          icon={<Icon d={ICONS.lock} size={24} />}
          title="Couldn't load your dashboards"
          body="You may no longer have access to them, or the request failed. Reload the page, and ask an admin if it keeps happening."
        />
      </>
    );
  }
  const dashboards = list.data;
  if (dashboards.length === 0) {
    return (
      <>
        {header}
        <EmptyState
          icon={<Icon d={ICONS.dashboard} size={24} />}
          title="No dashboards yet"
          body="Build one from figures across every open position: pipeline by stage, research pace, positions at risk."
        />
        {dialog}
      </>
    );
  }
  const current = dashboards.find((item) => item.id === searchParams.get(DASHBOARD_PARAM)) ?? dashboards[0];

  return (
    <>
      {header}
      <nav aria-label="Your dashboards" className="mb-5 flex gap-1 overflow-x-auto border-b border-u-border pb-2.5">
        {dashboards.map((dashboard) => {
          const isActive = dashboard.id === current.id;
          return (
            <button
              key={dashboard.id}
              type="button"
              aria-current={isActive ? "page" : undefined}
              onClick={() => open(dashboard.id)}
              className={cn(
                "inline-flex flex-none items-center gap-1.5 whitespace-nowrap rounded-u-control px-3 py-1.5 text-body",
                isActive ? "bg-u-sunken font-medium text-u-text" : "text-u-text2 hover:bg-u-raised hover:text-u-text",
              )}
            >
              {dashboard.name}
              {dashboard.shared && <Icon d={ICONS.team} size={13} className="text-u-text3" />}
            </button>
          );
        })}
      </nav>
      <DashboardView
        key={current.id}
        scope={SCOPE}
        dashboard={current}
        library={library.data}
        liveNote="live across every open position"
        sharedWith="the workspace team"
        onOpen={open}
        onDeleted={() => setSearchParams({})}
      />
      {dialog}
    </>
  );
}
