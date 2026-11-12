package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The code that completes a sign-in; the challenge it answers rides the {@code lm_mfa_challenge} cookie. */
public record MfaVerifyRequest(
        @NotBlank(message = "Enter the code from your authenticator app")
        @Size(max = 32, message = "Enter the code from your authenticator app")
        String code
) {

    @Override
    public String toString() {
        return "MfaVerifyRequest[code=<redacted>]";
    }
}
