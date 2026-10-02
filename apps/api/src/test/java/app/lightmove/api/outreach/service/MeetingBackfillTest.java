package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How long a calendar that could not be read waits before it is asked again. */
class MeetingBackfillTest {

    @Test
    @DisplayName("a quarter of an hour after the first failure, doubling each time, never more than a day")
    void theWaitDoublesUpToADay() {
        assertThat(MeetingBackfill.retryWaitAfter(1)).isEqualTo(Duration.ofMinutes(15));
        assertThat(MeetingBackfill.retryWaitAfter(2)).isEqualTo(Duration.ofMinutes(30));
        assertThat(MeetingBackfill.retryWaitAfter(4)).isEqualTo(Duration.ofHours(2));
        assertThat(MeetingBackfill.retryWaitAfter(9)).isEqualTo(Duration.ofHours(24));
        assertThat(MeetingBackfill.retryWaitAfter(40)).isEqualTo(Duration.ofHours(24));
    }
}
