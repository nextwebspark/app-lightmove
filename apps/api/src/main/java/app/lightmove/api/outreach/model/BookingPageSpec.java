package app.lightmove.api.outreach.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/** One consultant's booking page: who is booked, for how long, and the working day it offers, in their zone. */
public record BookingPageSpec(String organizerAddress, String organizerName, String eventTitle, int minutes,
                              ZoneId zone, LocalTime dayStart, LocalTime dayEnd, Set<DayOfWeek> days) {

    public BookingPageSpec {
        days = Set.copyOf(days);
    }
}
