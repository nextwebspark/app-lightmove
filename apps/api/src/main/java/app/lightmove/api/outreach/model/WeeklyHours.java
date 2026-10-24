package app.lightmove.api.outreach.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The hours someone works on each day of the week, a day with none closed. A {@code weekly_hours} jsonb document as
 * well as a value: {@link JsonValue} and {@link JsonCreator} keep it flat, {@code {"MONDAY": {"start", "end"}}}.
 */
public final class WeeklyHours {

    private static final WeeklyHours CLOSED = new WeeklyHours(new EnumMap<>(DayOfWeek.class));

    private final Map<DayOfWeek, DayHours> days;

    private WeeklyHours(EnumMap<DayOfWeek, DayHours> days) {
        this.days = Collections.unmodifiableMap(days);
    }

    @JsonCreator
    public static WeeklyHours of(Map<DayOfWeek, DayHours> days) {
        if (days == null || days.isEmpty()) {
            return CLOSED;
        }
        EnumMap<DayOfWeek, DayHours> held = new EnumMap<>(DayOfWeek.class);
        days.forEach((day, hours) -> {
            if (day != null && hours != null) {
                held.put(day, hours);
            }
        });
        return new WeeklyHours(held);
    }

    /** The same hours on each of {@code days}. */
    public static WeeklyHours uniform(Set<DayOfWeek> days, LocalTime start, LocalTime end) {
        EnumMap<DayOfWeek, DayHours> held = new EnumMap<>(DayOfWeek.class);
        DayHours hours = new DayHours(start, end);
        days.forEach(day -> held.put(day, hours));
        return new WeeklyHours(held);
    }

    @JsonValue
    public Map<DayOfWeek, DayHours> asMap() {
        return days;
    }

    public Optional<DayHours> on(DayOfWeek day) {
        return Optional.ofNullable(days.get(day));
    }

    public boolean isOpenOn(DayOfWeek day) {
        return days.containsKey(day);
    }

    public Set<DayOfWeek> days() {
        return days.keySet();
    }

    public boolean isClosed() {
        return days.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WeeklyHours that && days.equals(that.days);
    }

    @Override
    public int hashCode() {
        return days.hashCode();
    }

    @Override
    public String toString() {
        return days.toString();
    }
}
