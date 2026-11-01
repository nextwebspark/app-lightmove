package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One provider on Settings → Integrations. The client secret is write-only: {@code secretSet} says one is held,
 * {@code secretHint} names its last four characters, and nothing here, or anywhere, carries the rest.
 *
 * @param adminConsentUrl Microsoft's one-time approval link for Uncava's app; null at the other providers, or
 *                        where this deployment has no shared Microsoft app
 * @param adminConsentedAt when an admin came back from that link having approved it; informational, it gates nothing
 * @param connectionsThroughApp live mailboxes (Zoom accounts, for Zoom) connected through the app in use, each of which
 *                              connects again if the workspace changes app
 * @param secretSavedBy the name of whoever saved the secret held; null for one saved before it was recorded
 * @param keysCheckedAt when the provider was last asked about these keys; null until it is
 * @param keysRefusedWith the provider's code when that answer refused them; null when it accepted
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
