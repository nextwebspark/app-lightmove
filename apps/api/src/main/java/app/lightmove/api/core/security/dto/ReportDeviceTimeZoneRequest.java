package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The zone the caller's browser is in, reported so an Automatic clock follows it. */
public record ReportDeviceTimeZoneRequest(
        @NotBlank(message = "That is not a time zone")
        @Size(max = 64, message = "That is not a time zone")
        String timeZone
) {}
