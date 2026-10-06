package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
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

    @Test
    @DisplayName("a follow-up lands on its own time of day, on the working day its delay reaches")
    void followUpDueAtItsSendTime() {
        assertThat(WINDOW.followUpDue(at(2026, 10, 1, 16, 45), DUBAI, 2, LocalTime.of(9, 30)))
                .isEqualTo(at(2026, 10, 5, 9, 30));
        assertThat(WINDOW.followUpDue(at(2026, 10, 1, 16, 45), DUBAI, 2, null)).isEqualTo(at(2026, 10, 5, 16, 45));
    }

    @Test
    @DisplayName("a Sunday-to-Thursday week counts Sunday and skips Friday and Saturday")
    void aGulfWeek() {
        SendingWindow gulf = new SendingWindow(LocalTime.of(9, 0), LocalTime.of(17, 0), Set.of(DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY));
        assertThat(gulf.isOpen(at(2026, 10, 4, 10, 0), DUBAI)).isTrue();
        assertThat(gulf.nextOpening(at(2026, 10, 1, 17, 0), DUBAI)).isEqualTo(at(2026, 10, 4, 9, 0));
        assertThat(gulf.addWorkingDays(at(2026, 10, 1, 10, 0), DUBAI, 1)).isEqualTo(at(2026, 10, 4, 10, 0));
        assertThat(gulf.admits(LocalTime.of(17, 0))).isFalse();
    }

    @Test
    @DisplayName("a follow-up's time may be earlier in the day than the step before went")
    void anEarlierSendTimeIsTheNextWorkingDaysMorning() {
        assertThat(WINDOW.followUpDue(at(2026, 10, 5, 17, 30), DUBAI, 1, LocalTime.of(8, 15)))
                .isEqualTo(at(2026, 10, 6, 8, 15));
    }

    @Test
    @DisplayName("a follow-up keeps its wall-clock time across a daylight-saving change")
    void acrossADaylightSavingChange() {
        ZoneId london = ZoneId.of("Europe/London");
        Instant fridayBeforeTheClocksGoBack = ZonedDateTime.of(2026, 10, 23, 10, 0, 0, 0, london).toInstant();

        assertThat(WINDOW.followUpDue(fridayBeforeTheClocksGoBack, london, 1, LocalTime.of(9, 30)))
                .isEqualTo(ZonedDateTime.of(2026, 10, 26, 9, 30, 0, 0, london).toInstant());
        assertThat(WINDOW.followUpDue(fridayBeforeTheClocksGoBack, london, 1, null))
                .isEqualTo(Instant.parse("2026-10-26T10:00:00Z"));
    }

    @Test
    @DisplayName("spacing past an opening folds into the window, so a late person never lands after it closes")
    void spreadFoldsIntoTheWindow() {
        Instant opening = at(2026, 10, 5, 8, 0);
        assertThat(WINDOW.spread(opening, Duration.ofMinutes(3))).isEqualTo(at(2026, 10, 5, 8, 3));
        assertThat(WINDOW.spread(opening, Duration.ofHours(11))).isEqualTo(at(2026, 10, 5, 9, 0));
    }

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, DUBAI).toInstant();
    }
}
