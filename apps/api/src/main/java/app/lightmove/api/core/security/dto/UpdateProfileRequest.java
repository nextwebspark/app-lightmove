package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Settings → Profile edits: how the caller appears across the workspace.
 *
 * <p>No email field, deliberately. The address is the identity — unique, verification-gated, and the
 * signal for which firm someone works at — so changing it is a flow of its own, not a form field.
 * Nor is there a role: authority is granted by an admin, and a request cannot ask for it.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Enter your full name")
        @Size(max = 160, message = "That name is too long")
        String fullName,

        /** Optional and free text — blank means "no title", which the service stores as null. */
        @Size(max = 120, message = "That title is too long")
        String title,

        @NotBlank(message = "Pick a timezone")
        @Size(max = 64, message = "That is not a timezone")
        String timezone,

        @NotBlank(message = "Pick a language")
        @Size(max = 16, message = "That is not a language")
        String locale
) {}
