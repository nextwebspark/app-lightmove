package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The week a workspace works before an admin chooses one, read from its firm's country. */
class WorkingWeeksTest {

    private static final Set<DayOfWeek> MONDAY_TO_FRIDAY = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    @Test
    @DisplayName("Saudi Arabia, Qatar, Kuwait, Bahrain and Oman work Sunday to Thursday, however the country is spelled")
    void theGulfOutsideTheEmiratesWorksSundayToThursday() {
        String[] countries = {"Saudi Arabia", "saudi arabia", "Qatar", "Kuwait", "Bahrain", "Oman", "Egypt"};
        for (String country : countries) {
            assertThat(WorkingWeeks.defaultFor(country, MONDAY_TO_FRIDAY).days())
                    .as(country)
                    .containsExactlyInAnyOrder(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
        }
    }

    @Test
    @DisplayName("the Emirates, the rest of the world and an unknown or missing country keep the fallback week")
    void everywhereElseKeepsTheFallback() {
        for (String country : new String[] {"United Arab Emirates", "United Kingdom", "India", "Atlantis", null}) {
            assertThat(WorkingWeeks.defaultFor(country, MONDAY_TO_FRIDAY).days()).as(String.valueOf(country))
                    .containsExactlyInAnyOrderElementsOf(MONDAY_TO_FRIDAY);
        }
    }

    @Test
    @DisplayName("every working day runs 08:00 to 18:00")
    void theDefaultHours() {
        WeeklyHours week = WorkingWeeks.defaultFor("Qatar", MONDAY_TO_FRIDAY);

        assertThat(week.on(DayOfWeek.SUNDAY)).contains(new DayHours(LocalTime.of(8, 0), LocalTime.of(18, 0)));
        assertThat(week.isOpenOn(DayOfWeek.FRIDAY)).isFalse();
    }
}
