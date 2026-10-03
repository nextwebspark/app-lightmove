import { request } from "../../../lib/apiClient";
import type { IntegrationProvider, UpdateIntegrationRequest, WorkspaceIntegrations } from "./types";

/** Settings → Integrations. Every call answers the whole page, so a save redraws all three cards at once. */

export const INTEGRATIONS_KEY = ["workspace", "integrations"] as const;

export function integrations(signal?: AbortSignal): Promise<WorkspaceIntegrations> {
  return request<WorkspaceIntegrations>("/workspace/integrations", { signal });
}

export function updateIntegration(
  provider: IntegrationProvider,
  payload: UpdateIntegrationRequest,
): Promise<WorkspaceIntegrations> {
  return request<WorkspaceIntegrations>(`/workspace/integrations/${provider}`, { method: "PUT", body: payload });
}

/** Discards the workspace's own keys for the provider. */
export function returnToSharedApp(provider: IntegrationProvider): Promise<WorkspaceIntegrations> {
  return request<WorkspaceIntegrations>(`/workspace/integrations/${provider}`, { method: "DELETE" });
}

/**
 * Records Microsoft's admin-consent return: the directory the approving admin consented for, and the `state` our link
 * carried, without which the server records nothing.
 */
export function recordMicrosoftAdminConsent(tenantId: string, state: string): Promise<WorkspaceIntegrations> {
  return request<WorkspaceIntegrations>("/workspace/integrations/MICROSOFT/admin-consent", {
    method: "POST",
    body: { tenantId, state },
  });
}
