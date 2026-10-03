package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What Microsoft's admin-consent return carried: the approving directory's id, and the state our link sent. */
public record RecordAdminConsentRequest(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        String tenantId,
        @NotBlank @Size(max = 128) String state
) {}
