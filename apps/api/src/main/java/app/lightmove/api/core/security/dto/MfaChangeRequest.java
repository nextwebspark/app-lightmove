package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Turning two-factor on or off, or new recovery codes: the current password, where the account has one, and a code.
 * The password stops a stolen session enrolling its own phone and locking the owner out; the code proves the factor
 * is in hand. An account with no password has none to give, and is asked for a recent sign-in instead.
 */
public record MfaChangeRequest(
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
