package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotetakerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");

    @Test
    @DisplayName("a bot is asked in a minute before the call, or at once when the call is that close")
    void joinTime() {
        assertThat(NotetakerService.joinAtFor(Instant.parse("2026-10-22T11:00:00Z"), NOW))
                .isEqualTo(Instant.parse("2026-10-22T10:59:00Z"));
        assertThat(NotetakerService.joinAtFor(Instant.parse("2026-10-22T09:01:30Z"), NOW)).isNull();
        assertThat(NotetakerService.joinAtFor(Instant.parse("2026-10-22T08:50:00Z"), NOW)).isNull();
    }

    @Test
    @DisplayName("a bot can still be sent into a call running up to twenty minutes past its booked end")
    void overrunGrace() {
        assertThat(NotetakerService.isStillJoinable(Instant.parse("2026-10-22T09:30:00Z"), NOW)).isTrue();
        assertThat(NotetakerService.isStillJoinable(Instant.parse("2026-10-22T08:45:00Z"), NOW)).isTrue();
        assertThat(NotetakerService.isStillJoinable(Instant.parse("2026-10-22T08:35:00Z"), NOW)).isFalse();
    }

    @Test
    @DisplayName("only an https link with a host goes to Recall")
    void joinableLinks() {
        assertThat(NotetakerService.joinableOrNull(" https://meet.google.com/abc-defg-hij "))
                .isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(NotetakerService.joinableOrNull("https://teams.microsoft.com/l/meetup-join/19%3a"))
                .isNotNull();
        assertThat(NotetakerService.joinableOrNull("http://zoom.us/j/1")).isNull();
        assertThat(NotetakerService.joinableOrNull("tel:+971400000")).isNull();
        assertThat(NotetakerService.joinableOrNull("https://user:pw@zoom.us/j/1")).isNull();
        assertThat(NotetakerService.joinableOrNull("not a link")).isNull();
        assertThat(NotetakerService.joinableOrNull(null)).isNull();
    }
}
