package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One provider on Settings → Integrations. The client secret is write-only: {@code secretSet} says one is held,
 * and nothing here, or anywhere, carries it.
 *
 * @param adminConsentUrl Microsoft's one-time approval link for Uncava's app; null at the other providers, or
 *                        where this deployment has no shared Microsoft app
 * @param adminConsentedAt when an admin came back from that link having approved it; informational, it gates nothing
 */
public record WorkspaceIntegrationResponse(
        IntegrationProvider provider,
        CredentialMode mode,
        String clientId,
        String tenantId,
        LocalDate secretExpiresOn,
        boolean secretSet,
        boolean sharedOffered,
        String redirectUri,
        List<String> scopes,
        String adminConsentUrl,
        String ownAppGuideUrl,
        String sharedAppGuideUrl,
        Instant updatedAt,
        Instant adminConsentedAt,
        String adminConsentTenantId
) {}
