package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.email.service.EmailAddressNormaliser;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.workspace.model.InviteCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

/** One invitee, from signup's invite step or Settings. No role is MEMBER. */
public record InviteRequest(
        @JsonDeserialize(converter = EmailAddressNormaliser.class)
        @NotBlank(message = "Enter an email address")
        @Email(message = "That doesn't look like a valid email")
        String email,

        WorkspaceRole role
) {

    public InviteCommand toCommand() {
        return new InviteCommand(email, role);
    }
}
