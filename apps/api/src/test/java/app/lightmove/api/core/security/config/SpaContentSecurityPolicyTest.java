package app.lightmove.api.core.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SpaContentSecurityPolicyTest {

    @Test
    @DisplayName("names the scheduler by its origin alone, whatever path the base URL carries")
    void namesTheSchedulerByOrigin() {
        String policy = SpaContentSecurityPolicy.of(" https://api.eu.nylas.com/v3/ ", false);

        assertThat(policy).contains("https://*.tiles.mapbox.com https://api.eu.nylas.com;")
                .doesNotContain("/v3");
    }

    @Test
    @DisplayName("names Cloudflare's challenge origin for scripts and frames only where the human check is on")
    void namesTheHumanCheckOnlyWhenOn() {
        String on = SpaContentSecurityPolicy.of("https://api.us.nylas.com", true);
        String off = SpaContentSecurityPolicy.of("https://api.us.nylas.com", false);

        assertThat(on).contains("script-src 'self' https://challenges.cloudflare.com;")
                .contains("frame-src 'self' blob: https://challenges.cloudflare.com;");
        assertThat(off).doesNotContain("challenges.cloudflare.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "api.us.nylas.com", "/scheduler"})
    @DisplayName("refuses to boot on a scheduler base URL it cannot name as an origin")
    void refusesAnUnnameableSchedulerUrl(String schedulerBaseUrl) {
        assertThatThrownBy(() -> SpaContentSecurityPolicy.of(schedulerBaseUrl, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lightmove.outreach.nylas.base-url");
    }
}
