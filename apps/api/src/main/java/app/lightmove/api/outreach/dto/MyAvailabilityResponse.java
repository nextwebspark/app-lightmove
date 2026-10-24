package app.lightmove.api.outreach.dto;

import java.util.List;

/**
 * Settings → Availability: the consultant's own working week and daily email cap ({@code null} where they use the
 * workspace's; a cap never above the workspace's), the workspace's beside them, the time zone both are read in, and
 * whether their calendar is one that may state working hours (Outlook), which apply while they have set none here.
 */
public record MyAvailabilityResponse(List<WorkingDayResponse> weeklyHours,
                                     List<WorkingDayResponse> workspaceWeeklyHours, Integer dailyEmailCap,
                                     int workspaceDailyEmailCap, String timeZone, boolean calendarStatesHours) {}
