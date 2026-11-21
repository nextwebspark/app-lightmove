package app.lightmove.api.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.dto.ChartAnswerDto;
import app.lightmove.api.dashboard.dto.ChartQuestionDto;
import app.lightmove.api.dashboard.model.ChartQuery;
import app.lightmove.api.dashboard.model.ChartQueryContext;
import app.lightmove.api.dashboard.model.ChartResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;

class ChartQueryServiceTest {

    @Test
    void aChartThatFailsLeavesTheRestOfTheDashboardAnswered() {
        ChartQuery slow = ChartQuery.kpi(DashboardScope.WORKSPACE, "slow", "c", "Slow", "d", (context, options) -> {
            throw new QueryTimeoutException("statement timeout");
        });
        ChartQuery quick = ChartQuery.kpi(DashboardScope.WORKSPACE, "quick", "c", "Quick", "d",
                (context, options) -> ChartResult.kpi("4", "", "open", false));
        ChartQueryLibrary library = mock(ChartQueryLibrary.class);
        when(library.find(DashboardScope.WORKSPACE, "slow")).thenReturn(Optional.of(slow));
        when(library.find(DashboardScope.WORKSPACE, "quick")).thenReturn(Optional.of(quick));
        ChartQueryService service = new ChartQueryService(library, mock(PlatformTransactionManager.class));

        List<ChartAnswerDto> answers = service.answers(new ChartQueryContext(UUID.randomUUID(), UUID.randomUUID(), null),
                DashboardScope.WORKSPACE,
                List.of(new ChartQuestionDto("slow", Map.of()), new ChartQuestionDto("quick", Map.of())));

        assertThat(answers.get(0).result()).isNull();
        assertThat(answers.get(0).code()).isEqualTo("CHART_UNAVAILABLE");
        assertThat(answers.get(1).result().kpi().value()).isEqualTo("4");
        assertThat(answers.get(1).code()).isNull();
    }
}
