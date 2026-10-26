package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.outreach.constant.WorkingHoursSource;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The times a booking link offers: half-hourly, inside each day's working hours, clear of the calendar, never soon. */
class FreeSlotsTest {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final Set<DayOfWeek> SUNDAY_TO_THURSDAY = Set.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
    private static final FreeSlots SLOTS = new FreeSlots(new WorkingHours(
            WeeklyHours.uniform(SUNDAY_TO_THURSDAY, LocalTime.of(8, 0), LocalTime.of(18, 0)), DUBAI,
            WorkingHoursSource.WORKSPACE), DUBAI);
    private static final Duration HALF_HOUR = Duration.ofMinutes(30);
    /** A Thursday. */
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("a week to a page, the days nobody works left off, every half hour the call fits before close")
    void workingDaysOnTheHalfHour() {
        SlotPage page = SLOTS.page(at(2026, 10, 1, 6, 0), OCT_1, HALF_HOUR, List.of());

        assertThat(page.days()).extracting(SlotDay::date).containsExactly(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 7));
        List<Instant> thursday = page.days().getFirst().starts();
        assertThat(thursday).hasSize(20);
        assertThat(thursday.getFirst()).isEqualTo(at(2026, 10, 1, 8, 0));
        assertThat(thursday.getLast()).isEqualTo(at(2026, 10, 1, 17, 30));

        SlotPage long45 = SLOTS.page(at(2026, 10, 1, 6, 0), OCT_1, Duration.ofMinutes(45), List.of());
        assertThat(long45.days().getFirst().starts().getLast()).isEqualTo(at(2026, 10, 1, 17, 0));
    }

    @Test
    @DisplayName("each day keeps its own hours: a short Thursday closes early")
    void eachDayKeepsItsOwnHours() {
        WeeklyHours week = WeeklyHours.of(Map.of(
                DayOfWeek.WEDNESDAY, new DayHours(LocalTime.of(9, 0), LocalTime.of(17, 0)),
                DayOfWeek.THURSDAY, new DayHours(LocalTime.of(9, 0), LocalTime.of(13, 0))));
        FreeSlots slots = new FreeSlots(new WorkingHours(week, DUBAI, WorkingHoursSource.PERSONAL), DUBAI);

        SlotPage page = slots.page(at(2026, 10, 1, 6, 0), OCT_1, HALF_HOUR, List.of());

        assertThat(page.days()).extracting(SlotDay::date).containsExactly(OCT_1, LocalDate.of(2026, 10, 7));
        assertThat(page.days().getFirst().starts().getLast()).isEqualTo(at(2026, 10, 1, 12, 30));
        assertThat(page.days().get(1).starts().getLast()).isEqualTo(at(2026, 10, 7, 16, 30));
    }

    @Test
    @DisplayName("nothing sooner than an hour from now, and nothing the calendar holds even partly")
    void noticeAndBusyTimes() {
        Instant now = at(2026, 10, 1, 9, 10);
        BusyInterval lunch = new BusyInterval(at(2026, 10, 1, 12, 15), at(2026, 10, 1, 13, 0));

        List<Instant> today = SLOTS.page(now, OCT_1, HALF_HOUR, List.of(lunch)).days().getFirst().starts();

        assertThat(today.getFirst()).isEqualTo(at(2026, 10, 1, 10, 30));
        assertThat(today).contains(at(2026, 10, 1, 11, 30), at(2026, 10, 1, 13, 0))
                .doesNotContain(at(2026, 10, 1, 12, 0), at(2026, 10, 1, 12, 30));
    }

    @Test
    @DisplayName("a fully booked working day is offered empty rather than left out")
    void aFullDayIsEmpty() {
        BusyInterval allDay = new BusyInterval(at(2026, 10, 4, 0, 0), at(2026, 10, 5, 0, 0));

        SlotPage page = SLOTS.page(at(2026, 10, 1, 6, 0), OCT_1, HALF_HOUR, List.of(allDay));

        assertThat(page.days().get(1).date()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(page.days().get(1).starts()).isEmpty();
    }

    @Test
    @DisplayName("pages run a week from the day asked for, back to today at the earliest, and stop at the horizon")
    void pagesFromTheDayAskedFor() {
        Instant now = at(2026, 10, 1, 9, 10);

        assertThat(FreeSlots.firstDayOf(now, LocalDate.of(2026, 9, 20), DUBAI)).isEqualTo(OCT_1);
        assertThat(FreeSlots.firstDayOf(now, null, DUBAI)).isEqualTo(OCT_1);
        assertThat(FreeSlots.firstDayOf(now, OCT_1.plusDays(FreeSlots.HORIZON_DAYS), DUBAI)).isNotNull();
        assertThat(FreeSlots.firstDayOf(now, OCT_1.plusDays(FreeSlots.HORIZON_DAYS + 1), DUBAI)).isNull();
        assertThat(FreeSlots.pageStart(OCT_1, DUBAI)).isEqualTo(at(2026, 10, 1, 0, 0));
        assertThat(FreeSlots.pageEnd(OCT_1, DUBAI)).isEqualTo(at(2026, 10, 8, 0, 0));

        assertThat(SLOTS.page(now, OCT_1, HALF_HOUR, List.of()).previousFrom()).isNull();
        LocalDate nextWeek = LocalDate.of(2026, 10, 8);
        SlotPage later = SLOTS.page(now, nextWeek, HALF_HOUR, List.of());
        assertThat(later.previousFrom()).isEqualTo(OCT_1);
        assertThat(later.earliestDate()).isEqualTo(OCT_1);
        assertThat(later.latestDate()).isEqualTo(OCT_1.plusDays(FreeSlots.HORIZON_DAYS));
        assertThat(SLOTS.page(now, LocalDate.of(2026, 10, 3), HALF_HOUR, List.of()).previousFrom()).isEqualTo(OCT_1);
    }

    @Test
    @DisplayName("a booking is held to what a page could offer: the half hour, the working day, the horizon")
    void bookingMatchesWhatIsOffered() {
        Instant now = at(2026, 10, 1, 9, 10);

        assertThat(SLOTS.books(now, at(2026, 10, 4, 10, 0), HALF_HOUR, List.of())).isTrue();
        assertThat(SLOTS.books(now, at(2026, 10, 4, 10, 15), HALF_HOUR, List.of())).isFalse();
        assertThat(SLOTS.books(now, at(2026, 10, 2, 10, 0), HALF_HOUR, List.of())).as("a Friday").isFalse();
        assertThat(SLOTS.books(now, at(2026, 10, 4, 17, 45), HALF_HOUR, List.of())).isFalse();
        assertThat(SLOTS.books(now, at(2026, 10, 1, 9, 30), HALF_HOUR, List.of())).as("within the hour").isFalse();
        assertThat(SLOTS.books(now, at(2026, 10, 4, 10, 0), HALF_HOUR,
                List.of(new BusyInterval(at(2026, 10, 4, 10, 15), at(2026, 10, 4, 10, 45))))).isFalse();
        Instant pastHorizon = OCT_1.plusDays(FreeSlots.HORIZON_DAYS + 7).atTime(10, 0).atZone(DUBAI).toInstant();
        assertThat(SLOTS.books(now, pastHorizon, HALF_HOUR, List.of())).isFalse();
    }

    @Test
    @DisplayName("Outlook hours kept in another zone than the consultant's land on the right instants")
    void hoursInAnotherZone() {
        WorkingHours london = new WorkingHours(WeeklyHours.uniform(Set.of(DayOfWeek.MONDAY), LocalTime.of(9, 0),
                LocalTime.of(10, 0)), ZoneId.of("Europe/London"), WorkingHoursSource.CALENDAR);
        FreeSlots slots = new FreeSlots(london, DUBAI);

        // Monday 5 October 2026, British Summer Time: 09:00 in London is 12:00 in Dubai.
        List<Instant> monday = slots.page(at(2026, 10, 1, 6, 0), LocalDate.of(2026, 10, 5), HALF_HOUR, List.of())
                .days().getFirst().starts();
        assertThat(monday).containsExactly(at(2026, 10, 5, 12, 0), at(2026, 10, 5, 12, 30));
    }

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, DUBAI).toInstant();
    }
}
