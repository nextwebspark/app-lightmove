package app.lightmove.api.workspace.dto;

import app.lightmove.api.common.time.constant.DateFormatStyle;
import app.lightmove.api.common.time.constant.TimeFormatStyle;

/** The admin's choices, each null where none was made, beside what an unchosen one reads as. */
public record WorkspaceDateTimeResponse(
        String timeZone,
        DateFormatStyle dateFormat,
        TimeFormatStyle timeFormat,

        /** The country's zone, or the standard where the firm names no country we know. */
        String suggestedTimeZone,

        /** The country the suggested zone is, by name, or null where it is the standard. */
        String suggestedFrom,

        /** Active members whose time zone follows their device: they pinned none. */
        long membersAutomatic
) {}
