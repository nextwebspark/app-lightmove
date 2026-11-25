package app.lightmove.api.platformsettings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why a variable goes back to its default: recorded with the reset. */
public record ResetPlatformSettingRequest(
        @NotBlank @Size(max = 500) String note
) {
}
