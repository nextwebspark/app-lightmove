import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { PageHeader } from "../../../components/layout/PageHeader";
import { useToast } from "../../../components/ui";
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
  const consentNotice = useAdminConsentReturn();

  return (
    <>
      <PageHeader
        title="Integrations"
        subtitle="How your team's mailboxes, calendars and video meetings connect. Uncava's shared apps need nothing from your IT department beyond an approval; your own apps keep every key inside your organisation."
      />

      {consentNotice && (
        <p role="alert" className="mb-4 text-body text-u-text2">
          {consentNotice}
        </p>
      )}

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

/**
 * Microsoft sends the approving admin back here from the admin-consent link, with `admin_consent=True&tenant=…` and
 * the `state` the link carried, or an `error`. A success is recorded once, so the Microsoft card can say so; either way the query is cleared, so a
 * reload does not record it again.
 */
function useAdminConsentReturn(): string | null {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const toast = useToast();
  const handled = useRef(false);
  const [notice, setNotice] = useState<string | null>(null);
  const record = useMutation({
    mutationFn: ({ tenantId, state }: { tenantId: string; state: string }) =>
      integrationsApi.recordMicrosoftAdminConsent(tenantId, state),
    onSuccess: (updated) => {
      queryClient.setQueryData(integrationsApi.INTEGRATIONS_KEY, updated);
      toast("Uncava is approved for your organisation");
    },
    onError: (error) => setNotice(messageFor(error)),
  });

  const consented = searchParams.get("admin_consent");
  const tenant = searchParams.get("tenant");
  const state = searchParams.get("state");
  const error = searchParams.get("error");

  useEffect(() => {
    if (handled.current || (consented === null && error === null)) return;
    handled.current = true;
    if (consented?.toLowerCase() === "true" && tenant && state) {
      record.mutate({ tenantId: tenant, state });
    } else {
      setNotice("Microsoft did not approve Uncava for your organisation. Your IT department can try the link again.");
    }
    setSearchParams({}, { replace: true });
  }, [consented, tenant, state, error, record, setSearchParams]);

  return notice;
}
