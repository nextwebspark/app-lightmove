package app.lightmove.api.core.platformconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.platformconfig.constant.PlatformSetting;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformSettingTest {

    @Test
    @DisplayName("a whole number inside the bounds parses, and one outside is refused naming the key")
    void wholeNumbersAreBounded() {
        assertThat(PlatformSetting.SOURCING_PICKS_PER_COMPANY.parse(" 4 ")).isEqualTo(4L);

        assertRefused(PlatformSetting.SOURCING_PICKS_PER_COMPANY, "11", "Must be between 1 and 10");
        assertRefused(PlatformSetting.SOURCING_PICKS_PER_COMPANY, "0", "Must be between 1 and 10");
        assertRefused(PlatformSetting.SOURCING_PICKS_PER_COMPANY, "3.5", "Must be a whole number");
        assertRefused(PlatformSetting.SOURCING_PICKS_PER_COMPANY, "", "Must be a whole number");
    }

    @Test
    @DisplayName("a fair-use ceiling of 0 is allowed, because 0 is no ceiling")
    void fairUseAcceptsZero() {
        assertThat(PlatformSetting.FAIR_USE_ASSISTANT_ASK_PER_SEAT.parse("0")).isEqualTo(0L);
    }

    @Test
    @DisplayName("a temperature is a decimal from 0 to 1, never NaN")
    void temperatureIsBounded() {
        assertThat(PlatformSetting.ASSISTANT_TEMPERATURE.parse("0.35")).isEqualTo(0.35);
        assertRefused(PlatformSetting.ASSISTANT_TEMPERATURE, "1.2", "Must be between 0 and 1");
        assertRefused(PlatformSetting.ASSISTANT_TEMPERATURE, "NaN", "Must be between 0 and 1");
        assertRefused(PlatformSetting.ASSISTANT_TEMPERATURE, "warm", "Must be a number");
    }

    @Test
    @DisplayName("a trial length is read as Spring reads a duration, held to whole days' bounds and written back as days")
    void trialLengthIsDays() {
        Object parsed = PlatformSetting.TRIAL_LENGTH.parse("21d");
        assertThat(parsed).isEqualTo(Duration.ofDays(21));
        assertThat(PlatformSetting.TRIAL_LENGTH.format(parsed)).isEqualTo("21d");

        assertRefused(PlatformSetting.TRIAL_LENGTH, "12h", "Must be between 1d and 90d");
        assertRefused(PlatformSetting.TRIAL_LENGTH, "91d", "Must be between 1d and 90d");
        assertRefused(PlatformSetting.TRIAL_LENGTH, "a fortnight", "Must be a number of days, such as 14d");
    }

    @Test
    @DisplayName("a model name must be a Gemini model, so nothing else can be sent as one")
    void modelNameIsAGeminiModel() {
        assertThat(PlatformSetting.ASSISTANT_MODEL.parse("gemini-3.5-flash")).isEqualTo("gemini-3.5-flash");
        assertRefused(PlatformSetting.ASSISTANT_MODEL, "gpt-5", "Must be a Gemini model name, such as gemini-3.5-flash");
        assertRefused(PlatformSetting.ASSISTANT_MODEL, "gemini-3.5 flash", "Must be a Gemini model name, such as gemini-3.5-flash");
    }

    @Test
    @DisplayName("no allowlisted key reaches a secret, an identity provider, encryption or sign-in")
    void allowlistHoldsNoSecurityKey() {
        assertThat(Arrays.stream(PlatformSetting.values()).map(PlatformSetting::key))
                .allSatisfy(key -> assertThat(key)
                        .doesNotStartWith("auth.").doesNotStartWith("crypto.")
                        .doesNotContainIgnoringCase("secret").doesNotContainIgnoringCase("key")
                        .doesNotContainIgnoringCase("token").doesNotContainIgnoringCase("password"))
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a key is found by its yml spelling, and an unknown one is not")
    void keysResolve() {
        assertThat(PlatformSetting.byKey("mcp.max-result-chars")).contains(PlatformSetting.MCP_MAX_RESULT_CHARS);
        assertThat(PlatformSetting.byKey("auth.jwt.private-key")).isEmpty();
    }

    private static void assertRefused(PlatformSetting setting, String raw, String message) {
        assertThatThrownBy(() -> setting.parse(raw))
                .isInstanceOfSatisfying(ApiException.class, refused -> {
                    assertThat(refused.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(refused.getFieldErrors()).isEqualTo(Map.of(setting.key(), message));
                });
    }
}
