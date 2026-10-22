package app.lightmove.api.notetaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.model.OfferedPlatforms;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfferedPlatformsTest {

    @Test
    void readsTheConfiguredPlatformsAndOffersNothingElse() {
        OfferedPlatforms offered = OfferedPlatforms.parse(List.of(" GOOGLE_MEET", ""));
        assertThat(offered.offers(NotetakerPlatform.GOOGLE_MEET)).isTrue();
        assertThat(offered.offers(NotetakerPlatform.MICROSOFT_TEAMS)).isFalse();
        assertThat(offered.offers(null)).isFalse();
    }

    @Test
    void aMisspeltPlatformFailsRatherThanBeingIgnored() {
        assertThatThrownBy(() -> OfferedPlatforms.parse(List.of("GOOGLE_MEETS")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
