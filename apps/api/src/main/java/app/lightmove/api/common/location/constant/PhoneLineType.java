package app.lightmove.api.common.location.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What a number rings. A landline and a service number (toll-free, premium rate, shared cost) are never offered
 * WhatsApp; unknown covers the numbers that may be either, every US and Canadian one among them.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PhoneLineType implements ApiValueEnum {

    MOBILE("mobile"),
    LANDLINE("landline"),
    SERVICE("service"),
    UNKNOWN("unknown");

    @JsonValue
    private final String value;
}
