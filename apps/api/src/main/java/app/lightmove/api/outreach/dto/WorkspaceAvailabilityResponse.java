package app.lightmove.api.outreach.dto;

import java.util.List;

/**
 * Settings → Meetings & email: the workspace's working week and daily email cap as they apply now, whether an admin
 * chose each, the country the week was read from while nobody has ({@code null} once chosen or where none is known),
 * and the highest cap the deployment allows.
 */
public record WorkspaceAvailabilityResponse(List<WorkingDayResponse> weeklyHours, boolean weeklyHoursChosen,
                                            String suggestedFromCountry, int dailyEmailCap,
                                            boolean dailyEmailCapChosen, int maxDailyEmailCap) {}
