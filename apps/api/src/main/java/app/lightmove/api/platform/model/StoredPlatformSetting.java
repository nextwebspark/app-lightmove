package app.lightmove.api.platform.model;

import java.time.Instant;

/** An override as stored, with the name of whoever last wrote it (null once their account is gone). */
public record StoredPlatformSetting(String key, String value, String updatedByName, Instant updatedAt) {}
