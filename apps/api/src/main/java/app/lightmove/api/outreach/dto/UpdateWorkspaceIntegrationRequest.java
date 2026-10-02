package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.CredentialMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Chooses the app a provider is connected through. On {@code OWN}, a blank {@code clientSecret} keeps the one
 * already held for the same client id. The client and tenant ids are restricted to the characters providers
 * issue them in, because both are written into authorisation URLs.
 */
public record UpdateWorkspaceIntegrationRequest(
        @NotNull(message = "Choose the shared app or your own")
        CredentialMode mode,

        @Size(max = 255, message = "That client ID is too long")
        @Pattern(regexp = "[A-Za-z0-9._-]*", message = "That doesn't look like a client ID")
        String clientId,

        @Size(max = 2048, message = "That secret is too long")
        String clientSecret,

        @Size(max = 64, message = "That tenant ID is too long")
        @Pattern(regexp = "[A-Za-z0-9.-]*", message = "That doesn't look like a tenant ID")
        String tenantId,

        LocalDate secretExpiresOn
) {

    @Override
    public String toString() {
        return "UpdateWorkspaceIntegrationRequest[mode=" + mode + ", clientId=" + clientId + ", tenantId=" + tenantId
                + ", secretExpiresOn=" + secretExpiresOn + ", clientSecret=<redacted>]";
    }
}
