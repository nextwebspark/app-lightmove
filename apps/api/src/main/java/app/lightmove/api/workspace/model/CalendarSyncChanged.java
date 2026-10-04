package app.lightmove.api.workspace.model;

import app.lightmove.api.workspace.constant.CalendarSync;
import java.util.UUID;

/**
 * An admin moved the workspace's calendar sync; {@code outreach} makes or deletes its Recall calendars once this
 * commits. Lives on the publisher's side so the dependency runs outreach → workspace, never the reverse.
 */
public record CalendarSyncChanged(UUID workspaceId, CalendarSync calendarSync) {}
