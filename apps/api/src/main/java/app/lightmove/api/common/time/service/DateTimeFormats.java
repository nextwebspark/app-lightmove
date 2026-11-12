package app.lightmove.api.common.time.service;

import app.lightmove.api.common.time.model.DateTimePreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A date or time as an email or a line of text states it, in someone's own zone and formats. */
public final class DateTimeFormats {

    private final DateTimePreferences preferences;
    private final DateTimeFormatter date;
    private final DateTimeFormatter time;

    private DateTimeFormats(DateTimePreferences preferences) {
        this.preferences = preferences;
        this.date = DateTimeFormatter.ofPattern(preferences.dateFormat().pattern(), Locale.ENGLISH);
        this.time = DateTimeFormatter.ofPattern(preferences.timeFormat().pattern(), Locale.ENGLISH);
    }

    public static DateTimeFormats of(DateTimePreferences preferences) {
        return new DateTimeFormats(preferences);
    }

    /** A calendar date has no zone: it reads as the day it names. */
    public String date(LocalDate day) {
        return date.format(day);
    }

    public String date(Instant moment) {
        return date.format(moment.atZone(preferences.zone()));
    }

    public String time(Instant moment) {
        return time.format(moment.atZone(preferences.zone()));
    }

    public String dateTime(Instant moment) {
        return date(moment) + ", " + time(moment);
    }
}
