package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The times Book a call offers: half-hourly, inside the working day, clear of the calendar, never too soon. */
class FreeSlotsTest {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final FreeSlots SLOTS = new FreeSlots(new SendingWindow(LocalTime.of(8, 0), LocalTime.of(18, 0),
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
            DUBAI);
    private static final Duration HALF_HOUR = Duration.ofMinutes(30);
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("five working days from today, the weekend skipped, every half hour the call fits before close")
    void workingDaysOnTheHalfHour() {
        // Thursday 1 Oct 2026, before the day opens.
        List<SlotDay> days = SLOTS.offered(at(2026, 10, 1, 6, 0), OCT_1, 5, HALF_HOUR, List.of());

        assertThat(days).extracting(SlotDay::date).containsExactly(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 7));
        assertThat(days.getFirst().starts()).hasSize(20);
        assertThat(days.getFirst().starts().getFirst()).isEqualTo(at(2026, 10, 1, 8, 0));
        assertThat(days.getFirst().starts().getLast()).isEqualTo(at(2026, 10, 1, 17, 30));

        List<SlotDay> long45 = SLOTS.offered(at(2026, 10, 1, 6, 0), OCT_1, 1, Duration.ofMinutes(45), List.of());
        assertThat(long45.getFirst().starts().getLast()).isEqualTo(at(2026, 10, 1, 17, 0));
    }

    @Test
    @DisplayName("nothing sooner than half an hour from now, and nothing the calendar holds even partly")
    void leadTimeAndBusyTimes() {
        Instant now = at(2026, 10, 1, 9, 10);
        BusyInterval lunch = new BusyInterval(at(2026, 10, 1, 12, 15), at(2026, 10, 1, 13, 0));

        List<Instant> today = SLOTS.offered(now, OCT_1, 1, HALF_HOUR, List.of(lunch)).getFirst().starts();

        assertThat(today.getFirst()).isEqualTo(at(2026, 10, 1, 10, 0));
        assertThat(today).contains(at(2026, 10, 1, 11, 30), at(2026, 10, 1, 13, 0))
                .doesNotContain(at(2026, 10, 1, 12, 0), at(2026, 10, 1, 12, 30));
    }

    @Test
    @DisplayName("a fully booked day is offered empty rather than left out")
    void aFullDayIsEmpty() {
        BusyInterval allDay = new BusyInterval(at(2026, 10, 2, 0, 0), at(2026, 10, 3, 0, 0));

        List<SlotDay> days = SLOTS.offered(at(2026, 10, 1, 6, 0), OCT_1, 2, HALF_HOUR, List.of(allDay));

        assertThat(days.get(1).date()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(days.get(1).starts()).isEmpty();
    }

    @Test
    @DisplayName("a later week starts on the day asked for, and a past day starts today")
    void pagesFromTheDayAskedFor() {
        Instant now = at(2026, 10, 1, 9, 10);

        List<SlotDay> later = SLOTS.offered(now, LocalDate.of(2026, 12, 5), 5, HALF_HOUR, List.of());
        assertThat(later).extracting(SlotDay::date).containsExactly(LocalDate.of(2026, 12, 7),
                LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 9), LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 11));
        assertThat(later.getFirst().starts()).hasSize(20);
        assertThat(SLOTS.startOf(now, LocalDate.of(2026, 12, 5), 5)).isEqualTo(at(2026, 12, 7, 8, 0));
        assertThat(SLOTS.endOf(now, LocalDate.of(2026, 12, 5), 5)).isEqualTo(at(2026, 12, 11, 18, 0));

        List<SlotDay> past = SLOTS.offered(now, LocalDate.of(2026, 9, 1), 1, HALF_HOUR, List.of());
        assertThat(past.getFirst().date()).isEqualTo(OCT_1);
        assertThat(SLOTS.startOf(now, LocalDate.of(2026, 9, 1), 1)).isEqualTo(now);
    }

    @Test
    @DisplayName("a requested start counts only when it is one the grid could have offered")
    void offersMatchesTheGrid() {
        Instant now = at(2026, 10, 1, 9, 0);

        assertThat(SLOTS.offers(now, at(2026, 10, 1, 10, 0), HALF_HOUR)).isTrue();
        assertThat(SLOTS.offers(now, at(2026, 10, 1, 17, 30), HALF_HOUR)).isTrue();
        assertThat(SLOTS.offers(now, at(2026, 10, 1, 17, 30), Duration.ofMinutes(45))).isFalse();
        assertThat(SLOTS.offers(now, at(2026, 10, 1, 10, 10), HALF_HOUR)).isFalse();
        assertThat(SLOTS.offers(now, at(2026, 10, 1, 9, 0), HALF_HOUR)).isFalse();
        assertThat(SLOTS.offers(now, at(2026, 10, 3, 10, 0), HALF_HOUR)).isFalse();
        assertThat(SLOTS.offers(now, at(2026, 10, 1, 7, 30), HALF_HOUR)).isFalse();
    }

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, DUBAI).toInstant();
    }
}
