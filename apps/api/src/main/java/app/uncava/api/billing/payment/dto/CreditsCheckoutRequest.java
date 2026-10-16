package app.uncava.api.billing.payment.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code pack} is a code of {@code uncava.billing.packs}, such as {@code contact-100}. */
public record CreditsCheckoutRequest(
        @NotBlank String pack
) {}
