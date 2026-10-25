package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.WorkingHoursSource;
import java.time.LocalDate;
import java.util.List;

/**
 * Book a call's calendar view: the days from {@code from}, read in the consultant's own zone, when their calendar is
 * taken (free/busy alone, never what an event is), their working hours and where those came from, the first and last
 * days a call may be booked on, the lengths a call may run to, and whether their own Zoom can put a link on the
 * invite. Working hours only label a time; the consultant may book outside them.
 */
public record MeetingCalendarResponse(String address, String timeZone, String provider, LocalDate from, int days,
                                      LocalDate earliestDate, LocalDate latestDate, List<TimeSpanResponse> busy,
                                      List<TimeSpanResponse> workingHours, WorkingHoursSource workingHoursSource,
                                      int minMinutes, int maxMinutes, boolean zoomOffered) {}
