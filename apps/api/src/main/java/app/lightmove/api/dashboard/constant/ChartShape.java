package app.lightmove.api.dashboard.constant;

/** What a library query answers, which decides the chart types that can draw it ({@code ChartRules}). */
public enum ChartShape {
    /** One figure. */
    KPI,
    /** One count or measure per label. */
    CATEGORICAL,
    /** One value per period, oldest first. */
    TIME_SERIES,
    /** One value per label per series. */
    MATRIX,
    /** Rows of text, already ordered. */
    RANKED
}
