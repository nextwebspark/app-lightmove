package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The sender's working day in their own zone, and a sequence's delays counted in working days. */
class SendingWindowTest {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final SendingWindow WINDOW = new SendingWindow(LocalTime.of(8, 0), LocalTime.of(18, 0),
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));

    @Test
    @DisplayName("open from the start of the working day up to, not including, its end, in the sender's zone")
    void openOnlyInTheWorkingDay() {
        assertThat(WINDOW.isOpen(at(2026, 10, 5, 8, 0), DUBAI)).isTrue();
        assertThat(WINDOW.isOpen(at(2026, 10, 5, 17, 59), DUBAI)).isTrue();
        assertThat(WINDOW.isOpen(at(2026, 10, 5, 18, 0), DUBAI)).isFalse();
        assertThat(WINDOW.isOpen(at(2026, 10, 5, 7, 59), DUBAI)).isFalse();
        assertThat(WINDOW.isOpen(at(2026, 10, 3, 10, 0), DUBAI)).isFalse();
        // 10:00 in Dubai is 07:00 in London: the same instant is outside a London sender's day.
        assertThat(WINDOW.isOpen(at(2026, 10, 5, 10, 0), ZoneId.of("Europe/London"))).isFalse();
    }

    @Test
    @DisplayName("the next opening is later today, the next working morning, or Monday after a weekend")
    void nextOpening() {
        assertThat(WINDOW.nextOpening(at(2026, 10, 5, 6, 30), DUBAI)).isEqualTo(at(2026, 10, 5, 8, 0));
        assertThat(WINDOW.nextOpening(at(2026, 10, 5, 19, 0), DUBAI)).isEqualTo(at(2026, 10, 6, 8, 0));
        assertThat(WINDOW.nextOpening(at(2026, 10, 2, 18, 30), DUBAI)).isEqualTo(at(2026, 10, 5, 8, 0));
        assertThat(WINDOW.nextOpening(at(2026, 10, 5, 9, 0), DUBAI)).isEqualTo(at(2026, 10, 5, 9, 0));
        assertThat(WINDOW.openingAfterToday(at(2026, 10, 2, 9, 0), DUBAI)).isEqualTo(at(2026, 10, 5, 8, 0));
    }

    @Test
    @DisplayName("working days skip the weekend and keep the time of day")
    void workingDaysSkipTheWeekend() {
        assertThat(WINDOW.addWorkingDays(at(2026, 10, 5, 10, 0), DUBAI, 3)).isEqualTo(at(2026, 10, 8, 10, 0));
        assertThat(WINDOW.addWorkingDays(at(2026, 10, 1, 10, 0), DUBAI, 3)).isEqualTo(at(2026, 10, 6, 10, 0));
        assertThat(WINDOW.addWorkingDays(at(2026, 10, 1, 10, 0), DUBAI, 0)).isEqualTo(at(2026, 10, 1, 10, 0));
    }

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, DUBAI).toInstant();
    }
}
