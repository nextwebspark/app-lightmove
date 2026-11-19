package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.time.constant.DateFormatStyle;
import app.lightmove.api.core.time.constant.TimeFormatStyle;
import app.lightmove.api.core.time.model.DateTimePreferences;

/** A time zone and two formats; on a person's own choice each is null where they keep the workspace's. */
public record DateTimeChoiceResponse(String timeZone, DateFormatStyle dateFormat, TimeFormatStyle timeFormat) {

    public static DateTimeChoiceResponse of(DateTimePreferences preferences) {
        return new DateTimeChoiceResponse(preferences.zone().getId(), preferences.dateFormat(),
                preferences.timeFormat());
    }
}
