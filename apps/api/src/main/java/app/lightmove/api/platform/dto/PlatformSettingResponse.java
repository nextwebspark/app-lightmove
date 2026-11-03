package app.lightmove.api.platform.dto;

import app.lightmove.api.core.platformconfig.constant.PlatformSettingGroup;
import app.lightmove.api.core.platformconfig.constant.PlatformSettingType;
import java.time.Instant;

/** One allowlisted value: what is in force, what the deployment started with, and who changed it. */
public record PlatformSettingResponse(
        String key,
        PlatformSettingGroup group,
        PlatformSettingType type,
        String label,
        String description,
        long min,
        long max,
        String defaultValue,
        String value,
        boolean overridden,
        String updatedByName,
        Instant updatedAt
) {}
