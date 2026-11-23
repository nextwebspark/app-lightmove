package app.lightmove.api.core.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PlatformSecuritySettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.mfa.MfaCodeVerifier;
import app.lightmove.api.core.security.mfa.MfaFactor;
import app.lightmove.api.core.security.mfa.MfaFactorRepository;
import app.lightmove.api.core.security.mfa.MfaRecoveryCodeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The step-up window against the application's clock, at its edges. */
class PlatformTwoFactorTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final UUID userId = UUID.randomUUID();
    private final MfaFactorRepository factors = mock(MfaFactorRepository.class);
    private final PlatformStepUpStore stepUps = mock(PlatformStepUpStore.class);

    @Test
    @DisplayName("a code entered exactly a window ago still counts, and one a second older does not")
    void theWindowIsMeasuredOnTheClock() {
        enrolledAt(NOW.minus(Duration.ofDays(1)));

        when(stepUps.verifiedAt(userId)).thenReturn(Optional.of(NOW.minus(WINDOW)));
        assertThatCode(() -> twoFactor(true).requireRecentCode(userId)).doesNotThrowAnyException();

        when(stepUps.verifiedAt(userId)).thenReturn(Optional.of(NOW.minus(WINDOW).minusSeconds(1)));
        assertRefused(ErrorCode.PLATFORM_STEP_UP_REQUIRED);
    }

    @Test
    @DisplayName("a code entered before the factor was turned on does not count")
    void aCodeOlderThanTheFactorDoesNotCount() {
        enrolledAt(NOW.minusSeconds(30));
        when(stepUps.verifiedAt(userId)).thenReturn(Optional.of(NOW.minusSeconds(60)));

        assertRefused(ErrorCode.PLATFORM_STEP_UP_REQUIRED);
    }

    @Test
    @DisplayName("with no factor the answer is to set one up, and the factor is read once")
    void noFactorAsksForOne() {
        when(factors.findByUserId(userId)).thenReturn(Optional.empty());

        assertRefused(ErrorCode.PLATFORM_TWO_FACTOR_REQUIRED);
        verify(factors, times(1)).findByUserId(userId);
    }

    @Test
    @DisplayName("with the requirement off nothing is read")
    void offAsksNothing() {
        assertThatCode(() -> twoFactor(false).requireRecentCode(userId)).doesNotThrowAnyException();
        verify(factors, times(0)).findByUserId(userId);
    }

    private void enrolledAt(Instant enabledAt) {
        MfaFactor factor = mock(MfaFactor.class);
        when(factor.isEnabled()).thenReturn(true);
        when(factor.getEnabledAt()).thenReturn(enabledAt);
        when(factors.findByUserId(userId)).thenReturn(Optional.of(factor));
    }

    private void assertRefused(ErrorCode code) {
        assertThatThrownBy(() -> twoFactor(true).requireRecentCode(userId))
                .isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getCode()).isEqualTo(code));
    }

    private PlatformTwoFactor twoFactor(boolean required) {
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.platform()).thenReturn(new PlatformSecuritySettings(required, WINDOW, null));
        return new PlatformTwoFactor(factors, mock(MfaCodeVerifier.class), mock(MfaRecoveryCodeRepository.class),
                stepUps, mock(AuditService.class), properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
