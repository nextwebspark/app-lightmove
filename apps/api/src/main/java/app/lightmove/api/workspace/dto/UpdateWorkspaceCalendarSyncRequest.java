package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.constant.CalendarSync;
import jakarta.validation.constraints.NotNull;

/** Settings → Integrations' calendar sync. */
public record UpdateWorkspaceCalendarSyncRequest(
        @NotNull(message = "Choose how calendars are read")
        CalendarSync calendarSync
) {}
