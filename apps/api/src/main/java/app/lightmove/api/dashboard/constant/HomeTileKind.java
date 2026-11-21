package app.lightmove.api.dashboard.constant;

import app.lightmove.api.common.constant.ApiValueEnum;

/**
 * What a tile on Home draws: one of its sections, or a chart from the workspace's library. A section carries the
 * smallest size it still reads at; a chart's own minimum is its chart type's, kept by the screen.
 */
public enum HomeTileKind implements ApiValueEnum {
    NEEDS(5, 6),
    TODAY(3, 3),
    POSITIONS(3, 4),
    CHART(1, 1);

    private final int minWidth;
    private final int minHeight;

    HomeTileKind(int minWidth, int minHeight) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    public boolean isChart() {
        return this == CHART;
    }

    public int minWidth() {
        return minWidth;
    }

    public int minHeight() {
        return minHeight;
    }

    @Override
    public String value() {
        return name();
    }
}
