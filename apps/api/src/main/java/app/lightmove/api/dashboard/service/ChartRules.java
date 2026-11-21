package app.lightmove.api.dashboard.service;

import app.lightmove.api.dashboard.constant.ChartShape;
import app.lightmove.api.dashboard.constant.ChartType;
import java.util.List;

/**
 * Which chart types can honestly draw an answer. Mirrored by the SPA's {@code chartRules.ts}, which greys out the
 * rest; the server refuses them on save. A pie needs parts of one whole, so never a median or a rate, and stops
 * reading past eight slices.
 */
public final class ChartRules {

    public static final int MAX_PIE_SLICES = 8;

    private ChartRules() {
    }

    public static List<ChartType> allowed(ChartShape shape, boolean noSum, int labelCount) {
        return switch (shape) {
            case KPI -> List.of(ChartType.KPI);
            case CATEGORICAL -> noSum || labelCount > MAX_PIE_SLICES
                    ? List.of(ChartType.BAR, ChartType.COLUMN, ChartType.TABLE)
                    : List.of(ChartType.BAR, ChartType.COLUMN, ChartType.PIE, ChartType.DONUT, ChartType.TABLE);
            case TIME_SERIES -> List.of(ChartType.LINE, ChartType.AREA, ChartType.COLUMN, ChartType.TABLE);
            case MATRIX -> List.of(ChartType.STACKED, ChartType.TABLE);
            case RANKED -> List.of(ChartType.TABLE);
        };
    }

    /** What a saved chart may be, before its data is known: a pie stays allowed while the slices might fit. */
    public static List<ChartType> allowedBeforeData(ChartShape shape, boolean noSum) {
        return allowed(shape, noSum, 0);
    }
}
