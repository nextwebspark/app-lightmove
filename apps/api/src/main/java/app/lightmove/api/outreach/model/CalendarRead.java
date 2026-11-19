package app.lightmove.api.outreach.model;

import java.util.List;

/**
 * The timed events a calendar read found, and whether it read the whole window: a read cut short at its page cap
 * holds only some of them, so nothing missing from it may be taken as gone.
 */
public record CalendarRead(List<CalendarEvent> events, boolean complete) {

    public static CalendarRead whole(List<CalendarEvent> events) {
        return new CalendarRead(List.copyOf(events), true);
    }

    public static CalendarRead cutShort(List<CalendarEvent> events) {
        return new CalendarRead(List.copyOf(events), false);
    }
}
