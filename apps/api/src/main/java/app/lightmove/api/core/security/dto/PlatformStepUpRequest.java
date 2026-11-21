package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The fresh code a high-impact platform action asks for: six digits from the app, or a recovery code. */
public record PlatformStepUpRequest(
        @NotBlank(message = "Enter the code from your authenticator app")
        @Size(max = 32, message = "That code is too long")
        String code
) {
}
