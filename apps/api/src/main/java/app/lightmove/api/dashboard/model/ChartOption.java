package app.lightmove.api.dashboard.model;

import app.lightmove.api.dashboard.constant.ChartOptionKey;
import java.util.List;

/** An option a library query offers, its allowed values and the one a new chart starts with. */
public record ChartOption(ChartOptionKey key, String label, List<ChartChoice> choices, String defaultValue) {

    public ChartOption {
        choices = List.copyOf(choices);
    }

    public boolean allows(String value) {
        return choices.stream().anyMatch(choice -> choice.value().equals(value));
    }

    public static ChartOption range() {
        return new ChartOption(ChartOptionKey.RANGE, "Period",
                List.of(new ChartChoice("all", "Since kickoff"), new ChartChoice("12", "Last 12 weeks"),
                        new ChartChoice("8", "Last 8 weeks"), new ChartChoice("4", "Last 4 weeks")), "all");
    }

    public static ChartOption top() {
        return new ChartOption(ChartOptionKey.TOP, "Show",
                List.of(new ChartChoice("all", "All"), new ChartChoice("10", "Top 10"), new ChartChoice("5", "Top 5"),
                        new ChartChoice("3", "Top 3")), "all");
    }
}
