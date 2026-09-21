package app.lightmove.api.report.service;

import static app.lightmove.api.report.service.ReportFixtures.capped;
import static app.lightmove.api.report.service.ReportFixtures.company;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.report.model.ReportSources;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One read's company budget: a single cap across the shortlist and the universe, and an honest total. */
class ReportSourceLoaderTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();

    private final TriageCompanyService triage = mock(TriageCompanyService.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final CandidateService candidates = mock(CandidateService.class);
    private final PositionService positions = mock(PositionService.class);

    @BeforeEach
    void aMandateWithNobodyMapped() {
        when(projects.findByIdAndWorkspaceId(PROJECT, WORKSPACE)).thenReturn(Optional.of(mock(Project.class)));
        when(candidates.listAllOfProject(eq(WORKSPACE), eq(PROJECT), anyInt()))
                .thenReturn(new CandidatesResponse(List.of(), 0, 0, 0));
    }

    @Test
    @DisplayName("the universe is given what the shortlist left of the cap, and the read says it was cut")
    void shortlistSpendsTheBudgetFirst() {
        stage(TriageCompanyStatus.SHORTLISTED, 3, companies(2), 2);
        stage(TriageCompanyStatus.IN_UNIVERSE, 1, companies(1), 5);

        ReportSources sources = loaderCappedAt(3).load(WORKSPACE, PROJECT);

        assertThat(sources.universe()).hasSize(3);
        assertThat(sources.universeTotal()).isEqualTo(7);
        assertThat(sources.isTruncated()).isTrue();
    }

    @Test
    @DisplayName("a shortlist that fills the cap leaves the universe counted but unread")
    void aSpentBudgetKeepsNoUniverseRow() {
        stage(TriageCompanyStatus.SHORTLISTED, 2, companies(2), 4);
        stage(TriageCompanyStatus.IN_UNIVERSE, 1, companies(1), 9);

        ReportSources sources = loaderCappedAt(2).load(WORKSPACE, PROJECT);

        assertThat(sources.universe()).hasSize(2);
        assertThat(sources.universeTotal()).isEqualTo(13);
        assertThat(sources.isTruncated()).isTrue();
        verify(triage).listAllOfStage(WORKSPACE, PROJECT, TriageCompanyStatus.IN_UNIVERSE,
                TriageCompanyFilters.none(), 1);
    }

    @Test
    @DisplayName("the band is read through the seam that never drafts a brief: a client seat can open the report")
    void theBriefIsNeverDraftedByARead() {
        stage(TriageCompanyStatus.SHORTLISTED, 10, companies(0), 0);
        stage(TriageCompanyStatus.IN_UNIVERSE, 10, companies(0), 0);

        loaderCappedAt(10).load(WORKSPACE, PROJECT);

        verify(positions).compensationOf(WORKSPACE, PROJECT);
        verify(positions, never()).get(WORKSPACE, PROJECT);
    }

    @Test
    @DisplayName("a mandate inside the cap is read whole")
    void insideTheCapNothingIsCut() {
        stage(TriageCompanyStatus.SHORTLISTED, 10, companies(1), 1);
        stage(TriageCompanyStatus.IN_UNIVERSE, 9, companies(4), 4);

        ReportSources sources = loaderCappedAt(10).load(WORKSPACE, PROJECT);

        assertThat(sources.universe()).hasSize(5);
        assertThat(sources.isTruncated()).isFalse();
    }

    private ReportSourceLoader loaderCappedAt(int maxCompanies) {
        return new ReportSourceLoader(projects, triage, candidates, positions,
                capped(new ReportSettings(maxCompanies, 5000, 6, 8, 9, 3, 12)));
    }

    private void stage(TriageCompanyStatus status, int askedCap, List<TriageCompanyResponse> answered, long total) {
        when(triage.listAllOfStage(WORKSPACE, PROJECT, status, TriageCompanyFilters.none(), askedCap))
                .thenReturn(new TriageCompaniesResponse(answered, total, 0, askedCap, null));
    }

    private static List<TriageCompanyResponse> companies(int count) {
        return IntStream.range(0, count).mapToObj(index -> company("Company " + index, "retail")).toList();
    }
}
