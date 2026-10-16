package app.uncava.api.workspace.dto;

import app.uncava.api.workspace.constant.CalendarSync;
import jakarta.validation.constraints.NotNull;

/** Settings → Integrations' calendar sync. */
public record UpdateWorkspaceCalendarSyncRequest(
        @NotNull(message = "Choose how calendars are read")
        CalendarSync calendarSync
) {}
