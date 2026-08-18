package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.security.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Settings → Security. The confirm-password field is client-only, like signup's and the reset's.
 *
 * <p>Neither field is normalised on the way in: trimming an address is a courtesy, trimming a
 * password changes the secret.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password")
        String currentPassword,

        @NotBlank(message = "Choose a password")
        @Size(min = PasswordPolicy.MIN_LENGTH, message = "Use at least 8 characters")
        @Pattern(regexp = ".*\\d.*", message = "Include at least one number")
        String newPassword
) {}
