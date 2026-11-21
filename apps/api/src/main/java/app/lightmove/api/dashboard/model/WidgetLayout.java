package app.lightmove.api.dashboard.model;

/** Where a chart sits on the dashboard's twelve-column grid: column {@code x}, row {@code y}, {@code w} columns wide, {@code h} rows tall. */
public record WidgetLayout(int x, int y, int w, int h) {

    public static final int COLUMNS = 12;
    public static final int MAX_HEIGHT = 24;
    public static final int MAX_ROW = 1000;

    public boolean fitsTheGrid() {
        return x >= 0 && w >= 1 && x + w <= COLUMNS && y >= 0 && y <= MAX_ROW && h >= 1 && h <= MAX_HEIGHT;
    }
}
