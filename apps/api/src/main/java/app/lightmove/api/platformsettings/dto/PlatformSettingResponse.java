package app.lightmove.api.platformsettings.dto;

import java.time.Instant;

/**
 * One application variable as the console lists it. {@code defaultValue} is the deployment's yml value, and
 * {@code value} differs from it only while an override is stored. {@code unit} is null for a plain count.
 * {@code lastChangedAt} is null for a variable nobody has changed; {@code lastChangedBy} is also null once that
 * account is erased.
 */
public record PlatformSettingResponse(
        String key,
        String group,
        String groupKeyPrefix,
        String label,
        String description,
        int value,
        int defaultValue,
        int min,
        int max,
        String unit,
        boolean changed,
        String lastChangedBy,
        Instant lastChangedAt
) {
}
