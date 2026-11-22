package app.lightmove.api.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.dashboard.constant.ChartShape;
import app.lightmove.api.dashboard.constant.ChartType;
import app.lightmove.api.dashboard.model.ChartResult;
import app.lightmove.api.report.dto.MarketShapeDto;
import app.lightmove.api.report.dto.TalentHubDto;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProjectChartQueriesTest {

    private static MarketShapeDto market(int hubs, int elsewhere, int unlocated) {
        List<TalentHubDto> leading = IntStream.range(0, hubs)
                .mapToObj(index -> new TalentHubDto("Country " + index, 100 - index, List.of(), List.of(), 0, 0, 0, 0,
                        null, null))
                .toList();
        return new MarketShapeDto(List.of(), List.of(), List.of(), 0, 0, List.of(), leading, elsewhere, unlocated,
                List.of());
    }

    private static List<ChartType> allowed(ChartResult result) {
        return ChartRules.allowed(ChartShape.CATEGORICAL, false, result.labels().size());
    }

    @Test
    void eightLeadingHubsWithATailAndTheUnlocatedStillFitAPie() {
        ChartResult countries = ProjectChartQueries.countriesOf(market(8, 12, 5));

        assertThat(countries.labels()).hasSize(ChartRules.MAX_PIE_SLICES)
                .endsWith(ChartShaping.OTHER, ChartLabels.NOT_RECORDED);
        assertThat(allowed(countries)).contains(ChartType.PIE, ChartType.DONUT);
        assertThat(countries.values().stream().mapToDouble(Double::doubleValue).sum())
                .isEqualTo(IntStream.range(0, 8).map(index -> 100 - index).sum() + 12 + 5);
    }

    @Test
    void hubsThatFitAreDrawnWholeWithNoOther() {
        ChartResult countries = ProjectChartQueries.countriesOf(market(5, 0, 0));

        assertThat(countries.labels()).containsExactly("Country 0", "Country 1", "Country 2", "Country 3", "Country 4");
        assertThat(allowed(countries)).contains(ChartType.PIE);
    }

    @Test
    void eightHubsAndNothingElseFillThePieExactly() {
        assertThat(ProjectChartQueries.countriesOf(market(8, 0, 0)).labels())
                .hasSize(8).doesNotContain(ChartShaping.OTHER);
    }
}
