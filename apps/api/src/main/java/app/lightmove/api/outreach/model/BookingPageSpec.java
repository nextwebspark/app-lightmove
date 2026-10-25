package app.lightmove.api.outreach.model;

import java.time.ZoneId;

/** One consultant's booking page: who is booked, for how long, and the working week it offers, in their zone. */
public record BookingPageSpec(String organizerAddress, String organizerName, String eventTitle, int minutes,
                              ZoneId zone, WeeklyHours hours) {}
