package app.lightmove.api.dashboard.model;

/** A single figure: {@code value} as drawn, an optional {@code unit} beside it, a line under it, and whether it warns. */
public record ChartKpi(String value, String unit, String sub, boolean alarm) {}
