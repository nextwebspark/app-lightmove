package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.constant.StarterKind;
import app.lightmove.api.assistant.dto.AssistantStarter;
import app.lightmove.api.assistant.dto.AssistantStartersResponse;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import app.lightmove.api.workspace.model.FirmFacts;
import app.lightmove.api.workspace.model.WorkspacePersona;
import app.lightmove.api.workspace.service.FirmService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The empty chat's starters follow the firm: its sector, the sectors beside it, and its size. */
class AssistantStartersTest {

    private static final UUID WORKSPACE = UUID.randomUUID();

    private final FirmService firms = mock(FirmService.class);
    private final IndustryAdjacency adjacency = mock(IndustryAdjacency.class);
    private final AssistantStarters starters = new AssistantStarters(firms, adjacency);

    @Test
    @DisplayName("a picked company gets its sector, two adjacent prompts and a size prompt, in that order")
    void offersTheFirmsOwnStarters() {
        firmIs(new FirmFacts("Kalem Group", "oil & energy", "Riyadh", "Saudi Arabia", null, 1_200,
                WorkspacePersona.empty()));
        when(adjacency.neighboursOf("oil & energy")).thenReturn(List.of("mining & metals", "utilities"));

        AssistantStartersResponse response = starters.forWorkspace(WORKSPACE);

        assertThat(response.sectorAssumed()).isFalse();
        assertThat(response.starters()).extracting(AssistantStarter::kind)
                .containsExactly(StarterKind.SECTOR, StarterKind.ADJACENT, StarterKind.ADJACENT, StarterKind.SIZE);
        assertThat(response.starters()).extracting(AssistantStarter::prompt).containsExactly(
                "Top 10 Oil & Energy companies in Saudi Arabia",
                "Companies in the sectors next to Oil & Energy in Saudi Arabia whose executives move well into Oil & Energy",
                "Top Mining & Metals companies in Saudi Arabia with executives who could move into Oil & Energy",
                "Oil & Energy companies in Saudi Arabia with 500 to 2,000 staff, similar in size to Kalem Group");
    }

    @Test
    @DisplayName("the persona's sector beats the company's universe industry, and its other sectors lead the adjacent ones")
    void prefersThePersonasSectors() {
        firmIs(new FirmFacts("Kalem Group", "online media", null, null, null, null,
                new WorkspacePersona(null, List.of("Retail", "Supermarkets"), List.of(), List.of(), null)));
        when(adjacency.neighboursOf("Retail")).thenReturn(List.of("apparel & fashion", "supermarkets"));

        List<AssistantStarter> offered = starters.forWorkspace(WORKSPACE).starters();

        assertThat(offered).extracting(AssistantStarter::prompt).containsExactly(
                "Top 10 Retail companies",
                "Companies in the sectors next to Retail whose executives move well into Retail",
                "Top Supermarkets companies with executives who could move into Retail");
    }

    @Test
    @DisplayName("a very large firm is compared with anyone above 5,000 staff")
    void opensTheBandForALargeFirm() {
        firmIs(new FirmFacts("Kalem Group", "retail", null, "United Arab Emirates", null, 40_000,
                WorkspacePersona.empty()));

        assertThat(starters.forWorkspace(WORKSPACE).starters()).last().extracting(AssistantStarter::prompt)
                .isEqualTo("Retail companies in United Arab Emirates with more than 5,000 staff, "
                        + "similar in size to Kalem Group");
    }

    @Test
    @DisplayName("a firm typed in by hand falls back to its persona's sector and geography")
    void readsThePersonaWithoutACompany() {
        firmIs(new FirmFacts("Kalem Group", null, null, null, null, null,
                new WorkspacePersona(null, List.of("Real Estate"), List.of(), List.of("GCC"), null)));

        AssistantStartersResponse response = starters.forWorkspace(WORKSPACE);

        assertThat(response.sectorAssumed()).isFalse();
        assertThat(response.starters()).extracting(AssistantStarter::prompt)
                .containsExactly("Top 10 Real Estate companies in GCC");
    }

    @Test
    @DisplayName("a firm with no sector anywhere is offered retail, and told so")
    void assumesRetail() {
        firmIs(new FirmFacts("Kalem Group", null, null, null, null, 800, WorkspacePersona.empty()));
        when(adjacency.neighboursOf(anyString())).thenReturn(List.of());
        when(adjacency.neighboursOf("retail")).thenReturn(List.of("supermarkets"));

        AssistantStartersResponse response = starters.forWorkspace(WORKSPACE);

        assertThat(response.sectorAssumed()).isTrue();
        assertThat(response.starters()).extracting(AssistantStarter::kind)
                .containsExactly(StarterKind.SECTOR, StarterKind.ADJACENT, StarterKind.ADJACENT);
        assertThat(response.starters().getFirst().prompt()).isEqualTo("Top 10 Retail companies");
    }

    private void firmIs(FirmFacts firm) {
        when(firms.firmOf(WORKSPACE)).thenReturn(firm);
    }
}
