package app.lightmove.api.outreach.model;

import app.lightmove.api.core.crypto.model.EncryptionContext;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One workspace's choice of OAuth app at one provider (V106). The client secret is held only as ciphertext bound
 * to this workspace and provider; returning to the shared app discards the workspace's keys rather than keeping
 * them for later.
 */
@Entity
@Table(name = "app_lm_workspace_mail_integration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMailIntegration extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 16)
    private IntegrationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private CredentialMode mode;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_secret_encrypted")
    private String clientSecretEncrypted;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "secret_expires_on")
    private LocalDate secretExpiresOn;

    /** The fewest days left a warning was already sent for, ahead of {@link #secretExpiresOn} (V111). */
    @Column(name = "secret_expiry_warned_days")
    private Integer secretExpiryWarnedDays;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /** When Microsoft returned an admin having approved Uncava's shared app for their directory (V108). */
    @Column(name = "admin_consented_at")
    private Instant adminConsentedAt;

    @Column(name = "admin_consent_tenant_id", length = 64)
    private String adminConsentTenantId;

    @Column(name = "admin_consented_by")
    private UUID adminConsentedBy;

    public static WorkspaceMailIntegration sharedApp(UUID workspaceId, IntegrationProvider provider) {
        WorkspaceMailIntegration integration = new WorkspaceMailIntegration();
        integration.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        integration.provider = Objects.requireNonNull(provider, "provider");
        integration.mode = CredentialMode.SHARED;
        return integration;
    }

    /** What the client secret is encrypted under: this workspace, this provider, this column. */
    public static EncryptionContext clientSecretContext(UUID workspaceId, IntegrationProvider provider) {
        return new EncryptionContext(workspaceId, "integration-client-secret:" + provider.name());
    }

    public EncryptionContext clientSecretContext() {
        return clientSecretContext(workspaceId, provider);
    }

    /**
     * {@code clientSecretEncrypted} null keeps the secret already held, which only an app already in use has.
     *
     * @return whether anything changed, so a save that changed nothing is not recorded as a change
     */
    public boolean useOwnApp(String clientId, String clientSecretEncrypted, String tenantId, LocalDate secretExpiresOn,
                             UUID actorId) {
        if (clientSecretEncrypted == null && !holdsSecretFor(clientId)) {
            throw new IllegalStateException("A new app needs its secret");
        }
        boolean changed = !isOwnApp() || clientSecretEncrypted != null || !Objects.equals(this.clientId, clientId)
                || !Objects.equals(this.tenantId, tenantId) || !Objects.equals(this.secretExpiresOn, secretExpiresOn);
        if (!changed) {
            return false;
        }
        this.mode = CredentialMode.OWN;
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        if (clientSecretEncrypted != null) {
            this.clientSecretEncrypted = clientSecretEncrypted;
        }
        this.tenantId = tenantId;
        if (!Objects.equals(this.secretExpiresOn, secretExpiresOn)) {
            this.secretExpiryWarnedDays = null;
        }
        this.secretExpiresOn = secretExpiresOn;
        this.updatedBy = actorId;
        return true;
    }

    public void useSharedApp(UUID actorId) {
        this.mode = CredentialMode.SHARED;
        this.clientId = null;
        this.clientSecretEncrypted = null;
        this.tenantId = null;
        this.secretExpiresOn = null;
        this.secretExpiryWarnedDays = null;
        this.updatedBy = actorId;
    }

    /** The threshold a warning is due for today, or empty when none is: each one warns once per expiry date. */
    public OptionalInt expiryWarningDue(LocalDate today, List<Integer> thresholds) {
        if (!isOwnApp() || secretExpiresOn == null) {
            return OptionalInt.empty();
        }
        long daysLeft = ChronoUnit.DAYS.between(today, secretExpiresOn);
        return thresholds.stream()
                .filter(threshold -> daysLeft <= threshold)
                .mapToInt(Integer::intValue)
                .min()
                .stream()
                .filter(threshold -> secretExpiryWarnedDays == null || threshold < secretExpiryWarnedDays)
                .findFirst();
    }


    /** Kept whatever the mode: the approval is the shared app's, and a return to it finds it still in place. */
    public void recordAdminConsent(String tenantId, UUID actorId, Instant now) {
        if (provider != IntegrationProvider.MICROSOFT) {
            throw new IllegalStateException("Only Microsoft has an organisation-wide admin consent");
        }
        this.adminConsentTenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.adminConsentedBy = actorId;
        this.adminConsentedAt = now;
    }

    public boolean isOwnApp() {
        return mode == CredentialMode.OWN;
    }

    public boolean hasSecret() {
        return clientSecretEncrypted != null;
    }

    /** A secret belongs to the app it was issued for: a new client id needs a new secret. */
    public boolean holdsSecretFor(String candidateClientId) {
        return isOwnApp() && hasSecret() && Objects.equals(clientId, candidateClientId);
    }
}
