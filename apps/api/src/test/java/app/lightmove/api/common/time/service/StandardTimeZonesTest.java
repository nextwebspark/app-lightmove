package app.lightmove.api.common.time.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.time.model.OfferedTimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StandardTimeZonesTest {

    @Test
    @DisplayName("the list is the fixed set: UTC and every Gulf capital among it, nothing else of the JDK's")
    void offersTheFixedList() {
        assertThat(StandardTimeZones.all()).hasSize(42);
        assertThat(StandardTimeZones.all()).extracting(OfferedTimeZone::zone)
                .doesNotHaveDuplicates()
                .contains("UTC", "Asia/Dubai", "Asia/Riyadh", "Asia/Qatar", "Asia/Kuwait", "Asia/Bahrain",
                        "Asia/Muscat");
        assertThat(StandardTimeZones.isOffered("Europe/London")).isTrue();
        assertThat(StandardTimeZones.isOffered("Asia/Tashkent")).isFalse();
        assertThat(StandardTimeZones.isOffered("EST")).isFalse();
        assertThat(StandardTimeZones.isOffered(null)).isFalse();
    }
}
