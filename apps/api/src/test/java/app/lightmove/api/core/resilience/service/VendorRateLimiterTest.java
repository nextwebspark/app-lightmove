package app.lightmove.api.core.resilience.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A vendor slower than a call a second is paced by its period, and the first pacing it is given holds. */
class VendorRateLimiterTest {

    private static final Duration NO_WAIT = Duration.ofMillis(1);

    private final VendorRateLimiter limiter = new VendorRateLimiter();

    @Test
    @DisplayName("one call a period: the next waits its turn, and a caller unwilling to wait is refused")
    void aSlowVendorIsPacedByItsPeriod() {
        limiter.pace("slow-vendor", 1, Duration.ofMillis(300));

        assertThat(limiter.tryAcquire("slow-vendor", NO_WAIT)).isTrue();
        assertThat(limiter.tryAcquire("slow-vendor", NO_WAIT)).isFalse();
        assertThat(limiter.tryAcquire("slow-vendor", Duration.ofSeconds(2))).isTrue();
    }

    @Test
    @DisplayName("an adapter pacing itself before its client is built keeps that pace over the client's per-second one")
    void theFirstPacingHolds() {
        limiter.pace("slow-vendor", 1, Duration.ofMinutes(1));
        limiter.pace("slow-vendor", 10);

        assertThat(limiter.tryAcquire("slow-vendor", NO_WAIT)).isTrue();
        assertThat(limiter.tryAcquire("slow-vendor", NO_WAIT)).isFalse();
    }
}
