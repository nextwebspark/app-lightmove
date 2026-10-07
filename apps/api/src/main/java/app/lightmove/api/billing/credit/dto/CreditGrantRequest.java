package app.lightmove.api.billing.credit.dto;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** {@code filsPerCredit} is required on a MANUAL grant and refused on a free one; {@code externalRef} makes a resend a no-op. */
public record CreditGrantRequest(
        @NotNull CreditGrantSource source,
        @Positive long credits,
        Instant expiresAt,
        @PositiveOrZero BigDecimal filsPerCredit,
        @Size(max = 128) String externalRef,
        @Size(max = 500) String note
) {}
