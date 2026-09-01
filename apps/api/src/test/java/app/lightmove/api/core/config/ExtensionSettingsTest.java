package app.lightmove.api.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;

/**
 * The two paths that produce the extension's TTL — the {@code @DefaultValue} annotation and the
 * fallback used when the whole config branch is absent — must agree, and must keep agreeing if the
 * constant is ever changed to a unit smaller than a day.
 */
class ExtensionSettingsTest {

    @Test
    @DisplayName("the annotation default and the absent-branch fallback are the same duration")
    void bothDefaultPathsAgree() {
        assertThat(ExtensionSettings.defaults().refreshTokenTtl())
                .isEqualTo(DurationStyle.SIMPLE.parse(ExtensionSettings.DEFAULT_REFRESH_TOKEN_TTL));
    }

    @Test
    @DisplayName("the parser survives a sub-day unit, which the hand-rolled ISO-8601 one did not")
    void survivesASubDayUnit() {
        // Duration.parse("P" + "12H".toUpperCase()) throws: ISO-8601 needs the T before a time unit.
        // The point of using Boot's own parser is that changing the constant to "12h" is a config
        // decision rather than a boot failure naming neither this file nor the constant.
        assertThat(DurationStyle.SIMPLE.parse("12h")).isEqualTo(Duration.ofHours(12));
        assertThat(DurationStyle.SIMPLE.parse("14d")).isEqualTo(Duration.ofDays(14));
    }
}
