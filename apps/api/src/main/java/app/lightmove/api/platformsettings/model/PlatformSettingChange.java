package app.lightmove.api.platformsettings.model;

import java.time.Instant;

/** One recorded change of an application variable. {@code changedBy} is a name, null once that account is erased. */
public record PlatformSettingChange(
        String key,
        String oldValue,
        String newValue,
        String note,
        String changedBy,
        Instant changedAt
) {
}
