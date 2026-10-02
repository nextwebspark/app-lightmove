package app.lightmove.api.outreach.dto;

import java.util.List;

/**
 * Settings → Integrations.
 *
 * @param ownAppsOffered false where this deployment has no key to encrypt a workspace's own secret with
 * @param recallOffered  false where this deployment has no Recall account, so calendars are read directly
 */
public record WorkspaceIntegrationsResponse(
        List<WorkspaceIntegrationResponse> providers,
        boolean ownAppsOffered,
        boolean recallOffered
) {}
