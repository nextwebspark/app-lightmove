package app.lightmove.api.core.security.dto;

import java.time.Instant;

/**
 * Settings → Security's two-factor card. Never the secret or a code: those are shown once, at setup.
 *
 * @param offered whether a person may turn it on here; one who already has it may still turn it off
 */
public record MfaStatusResponse(boolean offered, boolean enabled, Instant enabledAt, long recoveryCodesLeft) {
}
