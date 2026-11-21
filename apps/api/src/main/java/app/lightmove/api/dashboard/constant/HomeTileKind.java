package app.lightmove.api.dashboard.constant;

/** What a tile on Home draws: one of its sections, or a chart from the workspace's library. */
public enum HomeTileKind {
    NEEDS,
    TODAY,
    POSITIONS,
    CHART;

    public static HomeTileKind fromValue(String value) {
        for (HomeTileKind kind : values()) {
            if (kind.name().equals(value)) {
                return kind;
            }
        }
        return null;
    }
}
