package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One provider on Settings → Integrations. The secret is write-only: {@code secretSet} and its last four in
 * {@code secretHint}, never the rest.
 *
 * @param adminConsentUrl Microsoft's approval link for Uncava's app; null elsewhere or with no shared Microsoft app
 * @param adminConsentedAt informational; gates nothing
 * @param connectionsThroughApp live connections through the app in use, each reconnecting if the workspace changes app
 * @param secretSavedBy null for a secret saved before it was recorded
 * @param keysRefusedWith the provider's code when it refused the keys; null when it accepted
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
        String adminConsentTenantId,
        int connectionsThroughApp,
        String secretHint,
        Instant secretSavedAt,
        String secretSavedBy,
        Instant keysCheckedAt,
        String keysRefusedWith
) {}
