package app.lightmove.api.platformsettings.dto;

import app.lightmove.api.platformsettings.model.PlatformSettingChange;
import java.time.Instant;

/** One line of a variable's history. {@code changedBy} is null once that account is erased. */
public record PlatformSettingChangeResponse(
        String oldValue,
        String newValue,
        String note,
        String changedBy,
        Instant changedAt
) {

    public static PlatformSettingChangeResponse of(PlatformSettingChange change) {
        return new PlatformSettingChangeResponse(change.oldValue(), change.newValue(), change.note(),
                change.changedBy(), change.changedAt());
    }
}
