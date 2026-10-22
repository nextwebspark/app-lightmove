package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import java.util.Set;

/**
 * Settings → Integrations → Notetaker. {@code offered}: this deployment sends a bot at all; {@code platforms}: the
 * calls it can join.
 */
public record WorkspaceNotetakerResponse(boolean offered, boolean enabled, Set<NotetakerPlatform> platforms) {}
