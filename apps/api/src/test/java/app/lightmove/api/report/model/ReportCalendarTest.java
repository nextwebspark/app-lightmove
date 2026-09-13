package app.lightmove.api.report.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The buckets the progress chapter counts into: from kickoff to today, nothing outside either end. */
class ReportCalendarTest {

    private static final Instant KICKOFF = Instant.parse("2026-07-21T10:00:00Z");
    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-09-08T08:00:00Z"), ZoneOffset.UTC);

    private final ReportCalendar calendar = ReportCalendar.of(KICKOFF, LocalDate.of(2026, 9, 1), TODAY);

    @Test
    @DisplayName("day 0 and week 0 are the kickoff's own, and the last bucket is today's")
    void bucketsRunFromKickoffToToday() {
        assertThat(calendar.kickoff()).isEqualTo(LocalDate.of(2026, 7, 21));
        assertThat(calendar.asOf()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(calendar.dayCount()).isEqualTo(50);
        assertThat(calendar.weekCount()).isEqualTo(8);
        assertThat(calendar.dayIndexOf(Instant.parse("2026-07-21T23:59:00Z"))).isZero();
        assertThat(calendar.weekIndexOf(Instant.parse("2026-07-28T00:00:00Z"))).isEqualTo(1);
        assertThat(calendar.endOfWeek(0)).isEqualTo(LocalDate.of(2026, 7, 27));
    }

    @Test
    @DisplayName("a moment outside the mandate's span lands in the nearest end bucket")
    void momentsAreClampedIntoTheSpan() {
        assertThat(calendar.dayIndexOf(Instant.parse("2026-01-01T00:00:00Z"))).isZero();
        assertThat(calendar.dayIndexOf(Instant.parse("2027-01-01T00:00:00Z"))).isEqualTo(49);
        assertThat(calendar.daysBetween(Instant.parse("2026-09-02T12:00:00Z"), calendar.asOf())).isEqualTo(6);
    }

    @Test
    @DisplayName("a mandate created today spans one day and one week")
    void aFreshMandateIsOneBucket() {
        ReportCalendar fresh = ReportCalendar.of(Instant.parse("2026-09-08T09:00:00Z"), null, TODAY);

        assertThat(fresh.dayCount()).isEqualTo(1);
        assertThat(fresh.weekCount()).isEqualTo(1);
        assertThat(fresh.targetDate()).isNull();
    }
}
