package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.security.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Accept an invitation by creating the invited account (the invitee has no account yet). No email
 * field: the address is the invitation's, resolved from the token server-side — a client-supplied
 * email is exactly what this flow must not trust.
 */
public record AcceptInvitationSignupRequest(
        @NotBlank(message = "Missing invitation token")
        String token,

        @NotBlank(message = "Enter your full name")
        @Size(max = 160, message = "That name is too long")
        String fullName,

        @NotBlank(message = "Choose a password")
        @Size(min = PasswordPolicy.MIN_LENGTH, message = "Use at least 8 characters")
        @Pattern(regexp = ".*\\d.*", message = "Include at least one number")
        String password
) {}
