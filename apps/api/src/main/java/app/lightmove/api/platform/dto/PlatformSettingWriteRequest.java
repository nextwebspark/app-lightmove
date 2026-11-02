package app.lightmove.api.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The value as typed; {@code PlatformSetting.parse} decides whether it fits. */
public record PlatformSettingWriteRequest(
        @NotNull(message = "Enter a value")
        @Size(max = 200, message = "That value is too long")
        String value
) {}
