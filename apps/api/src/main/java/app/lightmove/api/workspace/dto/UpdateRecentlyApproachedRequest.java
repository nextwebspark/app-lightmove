package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.model.Workspace;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Settings → Meetings & email's Recently approached: how many days back a teammate's approach warns; 0 is off. */
public record UpdateRecentlyApproachedRequest(
        @NotNull(message = "Enter a number of days")
        @Min(value = 0, message = "Enter 0 or more days")
        @Max(value = Workspace.MAX_RECENTLY_APPROACHED_DAYS, message = "Enter 365 days or fewer")
        Integer days
) {}
