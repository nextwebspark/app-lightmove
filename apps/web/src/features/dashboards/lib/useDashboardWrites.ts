import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import * as dashboardsApi from "../api/dashboardsApi";
import type { Dashboard, DashboardScope, SaveDashboard } from "../api/types";

/** Create, save and delete a dashboard; each refreshes the list, and a refusal is a toast in the server's words. */
export function useDashboardWrites(scope: DashboardScope) {
  const client = useQueryClient();
  const toast = useToast();
  const listKey = dashboardsApi.DASHBOARDS_KEY(scope);
  const refresh = () => client.invalidateQueries({ queryKey: listKey });
  const refuse = (error: unknown) => {
    if (codeOf(error) === "DASHBOARD_STALE" || codeOf(error) === "DASHBOARD_NOT_FOUND") refresh();
    toast.error(messageFor(error));
  };

  const create = useMutation({
    mutationFn: (body: SaveDashboard) => dashboardsApi.createDashboard(scope, body),
    onSuccess: (created) => {
      client.setQueryData<Dashboard[]>(listKey, (list) => [...(list ?? []), created]);
    },
    onError: refuse,
  });
  const save = useMutation({
    mutationFn: ({ id, body }: { id: string; body: SaveDashboard }) => dashboardsApi.updateDashboard(scope, id, body),
    onSuccess: (saved) => {
      client.setQueryData<Dashboard[]>(listKey, (list) => list?.map((item) => (item.id === saved.id ? saved : item)));
    },
    onError: refuse,
  });
  const remove = useMutation({
    mutationFn: (id: string) => dashboardsApi.deleteDashboard(scope, id),
    onSuccess: (_, id) => {
      client.setQueryData<Dashboard[]>(listKey, (list) => list?.filter((item) => item.id !== id));
    },
    onError: refuse,
  });
  return { create, save, remove };
}

/** A dashboard's body as saved, with whatever changed. */
export function bodyOf(dashboard: Dashboard, change: Partial<SaveDashboard> = {}): SaveDashboard {
  return {
    name: dashboard.name,
    shared: dashboard.shared,
    widgets: dashboard.widgets,
    version: dashboard.version,
    ...change,
  };
}
