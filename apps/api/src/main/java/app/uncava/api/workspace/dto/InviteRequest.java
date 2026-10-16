package app.uncava.api.workspace.dto;

import app.uncava.api.core.email.service.EmailAddressNormaliser;
import app.uncava.api.core.security.rbac.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

/** One row of signup step 3. */
public record InviteRequest(
        @JsonDeserialize(converter = EmailAddressNormaliser.class)
        @NotBlank(message = "Enter an email address")
        @Email(message = "That doesn't look like a valid email")
        String email,

        WorkspaceRole role
) {}
