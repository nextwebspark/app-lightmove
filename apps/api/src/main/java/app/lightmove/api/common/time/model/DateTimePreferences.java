package app.lightmove.api.common.time.model;

import app.lightmove.api.common.time.constant.DateFormatStyle;
import app.lightmove.api.common.time.constant.TimeFormatStyle;
import java.time.ZoneId;
import java.util.Objects;

/** How dates and times read for one person in one workspace, every value settled — nothing here is "not chosen". */
public record DateTimePreferences(ZoneId zone, DateFormatStyle dateFormat, TimeFormatStyle timeFormat) {

    /** Where nothing is known: no workspace and no choice of one's own. */
    public static final DateTimePreferences STANDARD = new DateTimePreferences(ZoneId.of("Asia/Dubai"),
            DateFormatStyle.STANDARD, TimeFormatStyle.STANDARD);

    /** Each value chosen, or else {@code fallback}'s: a person's over their workspace's, a workspace's over its country's. */
    public static DateTimePreferences settle(String zone, DateFormatStyle dateFormat, TimeFormatStyle timeFormat,
                                             DateTimePreferences fallback) {
        return new DateTimePreferences(zone == null ? fallback.zone() : ZoneId.of(zone),
                dateFormat == null ? fallback.dateFormat() : dateFormat,
                timeFormat == null ? fallback.timeFormat() : timeFormat);
    }

    public DateTimePreferences {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(dateFormat, "dateFormat");
        Objects.requireNonNull(timeFormat, "timeFormat");
    }
}
