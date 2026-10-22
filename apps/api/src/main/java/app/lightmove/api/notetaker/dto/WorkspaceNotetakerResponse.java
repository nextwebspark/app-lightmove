package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import java.util.Set;

/**
 * Settings → Integrations → Notetaker. {@code offered}: this deployment sends a bot at all; {@code platforms}: the
 * calls a bot joins here. {@code backupOffered}: the deployment has a backup recorder, a third party the workspace's
 * calls go to only while {@code backupEnabled}, which an admin turns on.
 */
public record WorkspaceNotetakerResponse(boolean offered, boolean enabled, Set<NotetakerPlatform> platforms,
                                         boolean backupOffered, boolean backupEnabled) {}
