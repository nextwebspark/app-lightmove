package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Turning two-factor on or off, or new recovery codes: the current password and a code, both. The password stops a
 * stolen session enrolling its own phone and locking the owner out; the code proves the factor is in hand.
 */
public record MfaChangeRequest(
        @NotBlank(message = "Enter your current password")
        String currentPassword,

        @NotBlank(message = "Enter the code from your authenticator app")
        @Size(max = 32, message = "Enter the code from your authenticator app")
        String code
) {

    @Override
    public String toString() {
        return "MfaChangeRequest[currentPassword=<redacted>, code=<redacted>]";
    }
}
