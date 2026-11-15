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
        String policy = SpaContentSecurityPolicy.of(" https://api.eu.nylas.com/v3/ ");

        assertThat(policy).contains("https://*.tiles.mapbox.com https://api.eu.nylas.com;")
                .doesNotContain("/v3");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "api.us.nylas.com", "/scheduler"})
    @DisplayName("refuses to boot on a scheduler base URL it cannot name as an origin")
    void refusesAnUnnameableSchedulerUrl(String schedulerBaseUrl) {
        assertThatThrownBy(() -> SpaContentSecurityPolicy.of(schedulerBaseUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lightmove.outreach.nylas.base-url");
    }
}
