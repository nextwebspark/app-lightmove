import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useToast } from "../../../components/ui";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import * as dashboardsApi from "../api/dashboardsApi";
import type { DashboardScope } from "../api/types";
import { useDashboardWrites } from "../lib/useDashboardWrites";
import { DashboardNameDialog } from "./DashboardNameDialog";
import { DASHBOARD_PARAM, DashboardRailSection } from "./DashboardRailSection";
import { DashboardView } from "./DashboardView";

/** A position's dashboards for its rail: the list, and the New dashboard dialog it opens. Staff only. */
export function useProjectDashboards(projectId: string, enabled: boolean) {
  const scope: DashboardScope = { kind: "project", projectId };
  return useQuery({
    queryKey: dashboardsApi.DASHBOARDS_KEY(scope),
    queryFn: ({ signal }) => dashboardsApi.listDashboards(scope, signal),
    enabled,
  });
}

export function ProjectDashboardRail({
  projectId,
  activeId,
  collapsed,
}: {
  projectId: string;
  activeId: string | null;
  collapsed: boolean;
}) {
  const scope: DashboardScope = { kind: "project", projectId };
  const navigate = useNavigate();
  const toast = useToast();
  const { data: dashboards = [], isError } = useProjectDashboards(projectId, true);
  const { create } = useDashboardWrites(scope);
  const [naming, setNaming] = useState(false);
  if (isError) return null;
  return (
    <>
      <DashboardRailSection dashboards={dashboards} activeId={activeId} collapsed={collapsed} onNew={() => setNaming(true)} />
      {naming && (
        <DashboardNameDialog
          title="New dashboard"
          subtitle="Only you see it until you share it with the position's team."
          submitLabel="Create and add charts"
          pending={create.isPending}
          onClose={() => setNaming(false)}
          onSubmit={(name) =>
            create.mutate({ name, shared: false, widgets: [], version: null }, {
              onSuccess: (created) => {
                setNaming(false);
                toast.success("Dashboard created");
                navigate({ search: `?${DASHBOARD_PARAM}=${created.id}` });
              },
            })
          }
        />
      )}
    </>
  );
}

/** The open dashboard in the report's body, in place of a chapter. */
export function ProjectDashboardBody({ projectId, dashboardId }: { projectId: string; dashboardId: string }) {
  const scope: DashboardScope = { kind: "project", projectId };
  const navigate = useNavigate();
  const list = useProjectDashboards(projectId, true);
  const library = useQuery({
    queryKey: dashboardsApi.CHART_LIBRARY_KEY(scope),
    queryFn: ({ signal }) => dashboardsApi.getChartLibrary(scope, signal),
    staleTime: Infinity,
  });
  if (list.isPending || library.isPending) return <LinesSkeleton lines={6} />;
  const dashboard = list.data?.find((item) => item.id === dashboardId);
  if (list.isError || library.isError || !dashboard) {
    return (
      <p className="rounded-u-chip bg-u-sunken px-4 py-3.5 text-body text-u-text2">
        This dashboard no longer exists, or it is no longer shared with you. Pick another under Your dashboards.
      </p>
    );
  }
  return (
    <DashboardView
      key={dashboard.id}
      scope={scope}
      dashboard={dashboard}
      library={library.data}
      liveNote="live from the mandate's rows"
      sharedWith="the position's team"
      onOpen={(id) => navigate({ search: `?${DASHBOARD_PARAM}=${id}` })}
      onDeleted={() => navigate({ search: "" })}
    />
  );
}
