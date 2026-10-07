package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.TriageCompanyMatches;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;

class CandidateToolsTest {

    private final CandidateService candidates = mock(CandidateService.class);
    private final TriageCompanyReadService triaged = mock(TriageCompanyReadService.class);
    private final TurnRecorder recorder = new TurnRecorder(step -> { });
    private final AssistantToolContext context = new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(),
            recorder);

    @Test
    void offersEveryStatusTheCandidateListTakes() {
        assertThat(CandidateTools.STATUSES.split(", "))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(CandidateStatus.values()).map(CandidateStatus::value).toList());
    }

    @Test
    void theSummaryTheModelReadsCarriesNoContactPayOrCustomField() {
        assertThat(Arrays.stream(MappedExecutiveSummary.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("contacts", "compensation", "customFields", "nationality", "gender", "email");
    }

    @Test
    void aCompanyNameThatMatchesNothingListsNobodyRatherThanEveryone() {
        when(triaged.named(any(), any(), any(), anyInt())).thenReturn(new TriageCompanyMatches(List.of(), 0));
        when(candidates.list(any(), any(), any())).thenReturn(new CandidatesResponse(List.of(), 0, 0, 25));

        MappedExecutives found = tools().listMappedExecutives("Nowhere Ltd", null, "not-a-status", null,
                toolContext());

        ArgumentCaptor<CandidateListCriteria> criteria = ArgumentCaptor.forClass(CandidateListCriteria.class);
        verify(candidates).list(eq(context.workspaceId()), eq(context.projectId()), criteria.capture());
        assertThat(criteria.getValue().triageCompanyIds()).isEmpty();
        assertThat(criteria.getValue().status()).isNull();
        assertThat(criteria.getValue().size()).isEqualTo(25);
        assertThat(found.matched()).isZero();
        assertThat(found.companyNameTooBroad()).isFalse();
        assertThat(recorder.steps()).singleElement()
                .satisfies(step -> assertThat(step.label()).isEqualTo("Listing executives at Nowhere Ltd"));
    }

    @Test
    void aProfileIdThatIsNotAnIdReadsNothing() {
        assertThat(tools().readExecutiveProfile("Fatima", toolContext())).isNull();
        verify(candidates, never()).dossierOf(any(), any(), any());
    }

    @Test
    void aProfileIsReadOnlyWithinTheCallersWorkspace() {
        UUID candidateId = UUID.randomUUID();
        when(candidates.dossierOf(context.workspaceId(), context.projectId(), candidateId))
                .thenReturn(Optional.empty());

        assertThat(tools().readExecutiveProfile(candidateId.toString(), toolContext())).isNull();
        verify(candidates).dossierOf(context.workspaceId(), context.projectId(), candidateId);
    }

    @Test
    void aCompanyNameMatchingMoreThanOneReadTakesSaysSo() {
        TriageCompanyResponse company = mock(TriageCompanyResponse.class);
        when(triaged.named(any(), any(), eq("Group"), anyInt()))
                .thenReturn(new TriageCompanyMatches(List.of(company), 80));
        when(candidates.list(any(), any(), any())).thenReturn(new CandidatesResponse(List.of(), 0, 0, 25));

        assertThat(tools().listMappedExecutives("Group", null, null, null, toolContext()).companyNameTooBroad())
                .isTrue();
    }

    @Test
    void unmappedCompaniesAreReadPastTheMappedOnesRatherThanFilteredInMemory() {
        Set<UUID> mapped = Set.of(UUID.randomUUID());
        TriageCompanyResponse company = mock(TriageCompanyResponse.class);
        when(company.companyName()).thenReturn("Lulu Group");
        when(candidates.companiesWithExecutivesOf(context.workspaceId(), context.projectId())).thenReturn(mapped);
        when(triaged.ofStageExcluding(context.workspaceId(), context.projectId(), TriageCompanyStatus.IN_UNIVERSE,
                mapped, 25)).thenReturn(new TriageCompanyMatches(List.of(company), 40));
        when(triaged.countOfStage(context.workspaceId(), context.projectId(), TriageCompanyStatus.IN_UNIVERSE))
                .thenReturn(41L);

        UnmappedCompanies unmapped = tools().companiesWithoutExecutives(toolContext());

        assertThat(unmapped).isEqualTo(new UnmappedCompanies(40, 41, List.of("Lulu Group")));
    }

    private CandidateTools tools() {
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.assistant()).thenReturn(new AssistantSettings("gemini-2.5-flash", 0.2, 512, 12, 25, 250,
                4, 10, 5, Duration.ofSeconds(25), 15, 3, 50, 75, List.of()));
        return new CandidateTools(candidates, triaged, properties);
    }

    private ToolContext toolContext() {
        return new ToolContext(Map.copyOf(context.asMap()));
    }
}
