package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** A first provider sign-in's terms step; the identity it completes rides the {@code lm_pending_registration} cookie. */
public record CompleteRegistrationRequest(
        @NotNull(message = "Tick the box to accept the Terms and Privacy Policy")
        @AssertTrue(message = "Tick the box to accept the Terms and Privacy Policy")
        Boolean termsAccepted
) {}
