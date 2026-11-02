package app.lightmove.api.platform.dto;

import java.time.Instant;

/**
 * The stored version every save raises, and the one the instance that answered has loaded. They differ only
 * between a save on another instance and this one hearing of it.
 */
public record PlatformConfigVersionResponse(long currentVersion, long loadedVersion, Instant loadedAt) {}
