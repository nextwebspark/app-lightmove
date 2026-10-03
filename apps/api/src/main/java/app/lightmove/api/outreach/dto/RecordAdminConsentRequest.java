package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The {@code tenant} Microsoft's admin-consent return carried: the directory's id, or one of its domains. */
public record RecordAdminConsentRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9.-]{1,64}") String tenantId
) {}
