package app.lightmove.api.workspace.dto;

import app.lightmove.api.common.time.constant.DateFormatStyle;
import app.lightmove.api.common.time.constant.TimeFormatStyle;
import jakarta.validation.constraints.Size;

/** Settings → General's Date &amp; time. A null is "not chosen": the country's zone, the standard formats. */
public record UpdateWorkspaceDateTimeRequest(
        @Size(max = 64, message = "That is not a time zone")
        String timeZone,
        DateFormatStyle dateFormat,
        TimeFormatStyle timeFormat
) {}
