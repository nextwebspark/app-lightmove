package app.lightmove.api.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegalSettingsTest {

    @Test
    @DisplayName("Uncava's notice to executives is linked only once its version and https link are both set")
    void theCandidateNoticeIsAPair() {
        assertThat(LegalSettings.none().linksCandidateNotice()).isFalse();
        assertThat(new LegalSettings(null, null, " 2026-12 ", "https://uncava.com/executive-privacy "))
                .satisfies(legal -> {
                    assertThat(legal.linksCandidateNotice()).isTrue();
                    assertThat(legal.candidateNoticeVersion()).isEqualTo("2026-12");
                    assertThat(legal.candidateNoticeUrl()).isEqualTo("https://uncava.com/executive-privacy");
                });
        assertThatThrownBy(() -> new LegalSettings(null, null, "2026-12", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegalSettings(null, null, "2026-12", "http://uncava.com/executive-privacy"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a notice version longer than the person's column holds fails the boot, not every delivery")
    void theCandidateNoticeVersionFitsItsColumn() {
        String longest = "v".repeat(LegalSettings.CANDIDATE_NOTICE_VERSION_MAX);
        assertThat(new LegalSettings(null, null, " " + longest + " ", "https://uncava.com/executive-privacy")
                .candidateNoticeVersion()).isEqualTo(longest);
        assertThatThrownBy(() -> new LegalSettings(null, null, longest + "v", "https://uncava.com/executive-privacy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate-notice-version");
    }
}
