package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.security.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The confirm-password field is client-only, like signup's — two matching copies prove nothing to a server. */
public record ResetPasswordRequest(
        @NotBlank String token,

        @NotBlank(message = "Choose a password")
        @Size(min = PasswordPolicy.MIN_LENGTH, message = "Use at least 8 characters")
        @Pattern(regexp = ".*\\d.*", message = "Include at least one number")
        String password
) {}
