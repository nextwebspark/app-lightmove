package app.lightmove.api.core.text.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LooseTextMatchTest {

    @Test
    @DisplayName("case, punctuation and spacing never count")
    void normalisingKeepsLettersAndDigitsOnly() {
        assertThat(LooseTextMatch.normalised("  P&L — Ownership (EMEA), 2026 ")).isEqualTo("p l ownership emea 2026");
        assertThat(LooseTextMatch.normalised(null)).isEmpty();
    }

    @Test
    @DisplayName("a proposal that shortens the expected line matches it, and an unrelated or empty one does not")
    void aShortenedLineMatches() {
        String expected = LooseTextMatch.normalised("Owns the group's treasury and banking relationships");

        assertThat(LooseTextMatch.matches(expected, LooseTextMatch.normalised("Treasury and banking"))).isTrue();
        assertThat(LooseTextMatch.matches(expected, LooseTextMatch.normalised("Owns group treasury, daily")))
                .isTrue();
        assertThat(LooseTextMatch.matches(expected, LooseTextMatch.normalised("Leads the sales team"))).isFalse();
        assertThat(LooseTextMatch.matches(expected, "")).isFalse();
    }
}
