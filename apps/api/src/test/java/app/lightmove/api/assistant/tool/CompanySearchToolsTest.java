package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.AssistantStepEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/** What a search tells the person waiting: what it looked for, then how much it found. */
class CompanySearchToolsTest {

    private final MarketSearch market = mock(MarketSearch.class);
    private final List<AssistantStepEvent> sent = new ArrayList<>();
    private final TurnRecorder recorder = new TurnRecorder(sent::add);

    @Test
    @DisplayName("a search is announced as it starts and finished with its count")
    void reportsTheSearchAsAStep() {
        when(market.matching(any())).thenReturn(new CompanyMatches(342, 25, List.of()));

        new CompanySearchTools(market).searchCompanyUniverse("United Arab Emirates", "retail", null,
                null, null, null, context());

        assertThat(sent).extracting(AssistantStepEvent::done).containsExactly(false, true);
        assertThat(recorder.steps()).singleElement().satisfies(step -> {
            assertThat(step.label()).isEqualTo("Searching retail companies in United Arab Emirates");
            assertThat(step.detail()).isEqualTo("342 matched, showing the top 25");
        });
    }

    @Test
    @DisplayName("an empty search says so rather than reporting zero of zero")
    void saysWhenNothingMatched() {
        assertThat(CompanySearchTools.describeMatches(new CompanyMatches(0, 0, List.of())))
                .isEqualTo("No companies matched");
        assertThat(CompanySearchTools.describeMatches(new CompanyMatches(12, 12, List.of())))
                .isEqualTo("12 matched");
    }

    @Test
    @DisplayName("the label reads only the constraints the search was given")
    void describesOnlyWhatWasAsked() {
        assertThat(CompanySearchTools.describeSearch("Qatar", null, null, null, null, null))
                .isEqualTo("Searching companies in Qatar");
        assertThat(CompanySearchTools.describeSearch("Qatar", "construction", null, null, 500L, 5_000L))
                .isEqualTo("Searching construction companies in Qatar with 500–5,000 staff");
        assertThat(CompanySearchTools.describeSearch(null, null, "solar", "ACWA", 1_000L, null))
                .isEqualTo("Searching \"solar\" companies named ACWA with at least 1,000 staff");
    }

    private ToolContext context() {
        return new ToolContext(new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), recorder).asMap());
    }
}
