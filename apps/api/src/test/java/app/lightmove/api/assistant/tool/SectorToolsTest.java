package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.AssistantStepEvent;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/** Adjacent sectors come from the firm's own list and nowhere else. */
class SectorToolsTest {

    private final IndustryAdjacency adjacency = mock(IndustryAdjacency.class);
    private final List<AssistantStepEvent> sent = new ArrayList<>();
    private final TurnRecorder recorder = new TurnRecorder(sent::add);

    @Test
    @DisplayName("an industry's neighbours are reported as a step")
    void listsTheNeighbours() {
        when(adjacency.neighboursOf("real estate")).thenReturn(List.of("construction", "insurance"));

        AdjacentIndustries answer = new SectorTools(adjacency).adjacentIndustries("Real Estate", context());

        assertThat(answer.adjacent()).containsExactly("construction", "insurance");
        assertThat(recorder.steps()).singleElement().satisfies(step -> {
            assertThat(step.label()).isEqualTo("Finding sectors next to real estate");
            assertThat(step.detail()).isEqualTo("2 adjacent");
        });
    }

    @Test
    @DisplayName("an industry written plainly is read as the universe spells it")
    void readsAPlainSpelling() {
        when(adjacency.neighboursOf("oil & energy")).thenReturn(List.of("utilities"));

        AdjacentIndustries answer = new SectorTools(adjacency).adjacentIndustries("Oil and Gas", context());

        assertThat(answer.industry()).isEqualTo("oil & energy");
        assertThat(answer.adjacent()).containsExactly("utilities");
    }

    @Test
    @DisplayName("an industry the list does not hold gives nothing to offer")
    void answersEmptyForAnUnknownIndustry() {
        when(adjacency.neighboursOf("Property")).thenReturn(List.of());

        AdjacentIndustries answer = new SectorTools(adjacency).adjacentIndustries("Property", context());

        assertThat(answer.adjacent()).isEmpty();
        assertThat(recorder.steps().getFirst().detail()).isEqualTo("None listed");
    }

    private ToolContext context() {
        return new ToolContext(new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), recorder).asMap());
    }
}
