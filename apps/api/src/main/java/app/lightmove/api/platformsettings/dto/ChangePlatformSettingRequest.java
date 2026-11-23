package app.lightmove.api.platformsettings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A variable's new value and why. The value arrives as typed, so that one that is not a number is refused on its
 * field with the variable's own range, rather than as an unreadable request.
 */
public record ChangePlatformSettingRequest(
        @NotBlank @Size(max = 20) String value,
        @NotBlank @Size(max = 500) String note
) {
}
