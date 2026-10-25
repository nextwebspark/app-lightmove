package app.lightmove.api.outreach.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Working hours and a daily email cap, replacing whatever was held. Null leaves the field to the level above: a
 * consultant's to the workspace's, a workspace's week to its country's and its cap to the deployment's.
 */
public record UpdateAvailabilityRequest(
        @Size(max = 7, message = "A week has seven days") List<@Valid @NotNull WorkingDayRequest> weeklyHours,
        Integer dailyEmailCap) {}
