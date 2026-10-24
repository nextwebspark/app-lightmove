package app.lightmove.api.outreach.dto;

import java.util.List;

/**
 * Settings → Availability: the consultant's own working week and daily email cap ({@code null} where they use the
 * workspace's), the workspace's beside them, the time zone both are read in, and whether their calendar states its
 * own working hours, which then come first.
 */
public record MyAvailabilityResponse(List<WorkingDayResponse> weeklyHours,
                                     List<WorkingDayResponse> workspaceWeeklyHours, Integer dailyEmailCap,
                                     int workspaceDailyEmailCap, String timeZone, boolean calendarStatesHours) {}
