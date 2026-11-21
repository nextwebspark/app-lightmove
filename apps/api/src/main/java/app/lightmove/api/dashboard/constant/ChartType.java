package app.lightmove.api.dashboard.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** How a dashboard draws a query's answer; the token is what the SPA and the stored widget spell. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ChartType implements ApiValueEnum {
    KPI("kpi"),
    BAR("bar"),
    COLUMN("column"),
    PIE("pie"),
    DONUT("donut"),
    LINE("line"),
    AREA("area"),
    STACKED("stacked"),
    TABLE("table");

    private final String value;

    public static ChartType fromValue(String token) {
        return ApiValueEnum.fromValue(ChartType.class, token);
    }
}
