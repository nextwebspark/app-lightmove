import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "../../../components/layout/PageHeader";
import { messageFor } from "../../../lib/errorCodes";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as integrationsApi from "../api/integrationsApi";
import { CalendarSyncCard } from "../components/CalendarSyncCard";
import { IntegrationCard } from "../components/IntegrationCard";

/**
 * Settings → Integrations: how the workspace's mailboxes, calendars and video meetings connect. Admin-only — the
 * route is behind `RequireAdmin` and every endpoint behind WORKSPACE_MANAGE.
 */
export function SettingsIntegrationsPage() {
  const integrations = useQuery({
    queryKey: integrationsApi.INTEGRATIONS_KEY,
    queryFn: ({ signal }) => integrationsApi.integrations(signal),
  });
  const workspace = useQuery({ queryKey: workspaceApi.WORKSPACE_KEY, queryFn: workspaceApi.workspace });

  return (
    <>
      <PageHeader
        title="Integrations"
        subtitle="How your team's mailboxes, calendars and video meetings connect. Uncava's shared apps need nothing from your IT department beyond an approval; your own apps keep every key inside your organisation."
      />

      {integrations.isError || workspace.isError ? (
        <p className="text-body text-u-text3">{messageFor(integrations.error ?? workspace.error)}</p>
      ) : integrations.isPending || workspace.isPending ? (
        <p className="text-body text-u-text3">Loading…</p>
      ) : (
        <div className="space-y-4">
          <CalendarSyncCard
            calendarSync={workspace.data.calendarSync}
            recallOffered={integrations.data.recallOffered}
          />
          {integrations.data.providers.map((integration) => (
            <IntegrationCard
              key={integration.provider}
              integration={integration}
              canStoreKeys={integrations.data.ownAppsOffered}
              calendarSyncsThroughRecall={
                integrations.data.recallOffered && workspace.data.calendarSync === "RECALL"
              }
            />
          ))}
        </div>
      )}
    </>
  );
}
