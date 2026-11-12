package app.lightmove.api.workspace.dto;

import app.lightmove.api.common.time.constant.DateFormatStyle;
import app.lightmove.api.common.time.constant.TimeFormatStyle;

/**
 * The workspace's date and time settings as its admin chose them — each null where nothing was chosen — beside what
 * an unchosen one reads as. The zone is a fallback: a member on Automatic reads in their device's once it has reported.
 */
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
