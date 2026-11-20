package app.lightmove.api.dashboard.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** A chart's width on the dashboard's twelve-column grid. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum WidgetSize implements ApiValueEnum {
    SMALL("S"),
    MEDIUM("M"),
    FULL("L");

    private final String value;

    public static WidgetSize fromValue(String token) {
        return ApiValueEnum.fromValue(WidgetSize.class, token);
    }
}
