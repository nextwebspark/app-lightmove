package app.lightmove.api.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.dashboard.constant.ChartShape;
import app.lightmove.api.dashboard.constant.ChartType;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.model.ChartOption;
import app.lightmove.api.dashboard.model.ChartQuery;
import app.lightmove.api.dashboard.model.ChartResult;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChartShapingTest {

    private static ChartQuery breakdown(boolean noSum) {
        ChartQuery query = ChartQuery.breakdown(DashboardScope.PROJECT, "q", "c", "l", "d", ChartType.BAR,
                List.of(ChartOption.top()), (context, options) -> null);
        return noSum ? query.asNotAdditive() : query;
    }

    @Test
    void aPieDrawsPartsOfOneWholeOfEightSlicesAtMost() {
        assertThat(ChartRules.allowed(ChartShape.CATEGORICAL, false, 8)).contains(ChartType.PIE, ChartType.DONUT);
        assertThat(ChartRules.allowed(ChartShape.CATEGORICAL, false, 9)).doesNotContain(ChartType.PIE);
        assertThat(ChartRules.allowed(ChartShape.CATEGORICAL, true, 3)).doesNotContain(ChartType.PIE, ChartType.DONUT);
        assertThat(ChartRules.allowed(ChartShape.TIME_SERIES, false, 3)).doesNotContain(ChartType.PIE, ChartType.BAR);
        assertThat(ChartRules.allowed(ChartShape.MATRIX, false, 3)).containsExactly(ChartType.STACKED, ChartType.TABLE);
    }

    @Test
    void topKeepsTheLargestAndFoldsTheRestIntoOtherUnlessTheyDoNotAdd() {
        ChartResult raw = ChartResult.categorical("Sector", "Companies", "", List.of("A", "B", "C", "D"),
                List.of(1d, 5d, 3d, 2d));
        ChartResult top = ChartShaping.shape(breakdown(false), raw, Map.of("top", "3"));
        assertThat(top.labels()).containsExactly("B", "C", "D", "Other");
        assertThat(top.values()).containsExactly(5d, 3d, 2d, 1d);
        assertThat(top.truncated()).isFalse();
        assertThat(top.folded()).isTrue();

        ChartResult medians = ChartShaping.shape(breakdown(true), raw, Map.of("top", "3"));
        assertThat(medians.labels()).containsExactly("B", "C", "D");
        assertThat(medians.folded()).isFalse();
    }

    @Test
    void anAnswerThatAlreadyFoldedItsTailDrawsOneOther() {
        ChartResult raw = ChartResult.categorical("Sector", "Companies", "", List.of("A", "B", "C", "D", "Other"),
                List.of(6d, 5d, 3d, 2d, 9d));
        ChartResult top = ChartShaping.shape(breakdown(false), raw, Map.of("top", "3"));
        assertThat(top.labels()).containsExactly("A", "B", "C", "Other");
        assertThat(top.values()).containsExactly(6d, 5d, 3d, 11d);
    }

    @Test
    void aBreakdownIsHeldToTwentyLabels() {
        List<String> labels = IntStream.range(0, 30).mapToObj(index -> "L" + index).toList();
        List<Double> values = IntStream.range(0, 30).mapToObj(index -> (double) index).toList();
        ChartResult shaped = ChartShaping.shape(breakdown(false),
                ChartResult.categorical("x", "y", "", labels, values), Map.of("top", "all"));
        assertThat(shaped.labels()).hasSize(21).endsWith("Other");
        assertThat(shaped.truncated()).isTrue();
    }

    @Test
    void aPeriodKeepsTheLatestWeeks() {
        ChartResult series = ChartResult.timeSeries("Week", "Executives", List.of("a", "b", "c", "d", "e"),
                List.of(1d, 2d, 3d, 4d, 5d));
        ChartQuery query = ChartQuery.overTime(DashboardScope.PROJECT, "s", "c", "l", "d", ChartType.LINE,
                List.of(ChartOption.range()), (context, options) -> null);
        assertThat(ChartShaping.shape(query, series, Map.of("range", "4")).labels()).containsExactly("b", "c", "d", "e");
        assertThat(ChartShaping.shape(query, series, Map.of("range", "all")).labels()).hasSize(5);
    }
}
