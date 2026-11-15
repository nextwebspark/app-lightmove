package app.lightmove.api.common.location.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Whether a number rings a mobile, a landline, or could be either; only a landline is never offered WhatsApp. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PhoneLineType implements ApiValueEnum {

    MOBILE("mobile"),
    LANDLINE("landline"),
    UNKNOWN("unknown");

    private final String value;
}
