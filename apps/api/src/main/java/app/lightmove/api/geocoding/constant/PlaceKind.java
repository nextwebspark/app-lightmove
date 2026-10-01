package app.lightmove.api.geocoding.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** How wide a suggested place is: a search radius means something around a city or an area, not a country. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PlaceKind implements ApiValueEnum {

    COUNTRY("country"),
    AREA("area"),
    CITY("city");

    private final String value;
}
