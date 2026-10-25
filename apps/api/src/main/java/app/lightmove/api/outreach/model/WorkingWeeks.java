package app.lightmove.api.outreach.model;

import app.lightmove.api.common.location.service.Countries;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * The week a workspace works until an admin chooses one: the one its firm's country keeps. Most of the Arab world and
 * Israel rest Friday and Saturday; the UAE moved to Saturday and Sunday in 2022, as most of the world rests.
 */
public final class WorkingWeeks {

    public static final LocalTime DEFAULT_START = LocalTime.of(8, 0);
    public static final LocalTime DEFAULT_END = LocalTime.of(18, 0);

    static final Set<String> SUNDAY_TO_THURSDAY_COUNTRIES = Set.of(
            "SA", "QA", "KW", "BH", "OM", "EG", "JO", "IQ", "YE", "DZ", "LY", "SD", "SY", "IL");

    private static final Set<DayOfWeek> SUNDAY_TO_THURSDAY = EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);

    private WorkingWeeks() {}

    /** {@code otherwiseDays} where the country is unknown or rests at the weekend most of the world keeps. */
    public static WeeklyHours defaultFor(String countrySpelling, Set<DayOfWeek> otherwiseDays) {
        String code = countrySpelling == null ? null : Countries.codeOf(countrySpelling);
        Set<DayOfWeek> days = code != null && SUNDAY_TO_THURSDAY_COUNTRIES.contains(code)
                ? SUNDAY_TO_THURSDAY : otherwiseDays;
        return WeeklyHours.uniform(days, DEFAULT_START, DEFAULT_END);
    }
}
