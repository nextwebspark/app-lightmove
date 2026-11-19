package app.lightmove.api.core.time.service;

import app.lightmove.api.core.time.model.DateTimePreferences;
import java.util.UUID;

/** A workspace's date and time defaults, for a module that sits below the workspace that owns them. */
public interface WorkspaceDateTimeDefaults {

    DateTimePreferences workspaceDefaultsOf(UUID workspaceId);
}
