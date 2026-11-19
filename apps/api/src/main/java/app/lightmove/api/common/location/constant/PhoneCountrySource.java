package app.lightmove.api.common.location.constant;

import app.lightmove.api.core.constant.ApiValueEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Where a phone's country code came from: written with the number, or added from where the person or the firm is. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PhoneCountrySource implements ApiValueEnum {

    GIVEN("given"),
    LOCATION("location"),
    WORKSPACE("workspace");

    @JsonValue
    private final String value;
}
