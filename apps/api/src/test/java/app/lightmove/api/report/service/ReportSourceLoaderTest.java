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

import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.model.PipelineProgressAnchors;
import app.lightmove.api.candidate.service.CandidatePipelineCatalog;
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
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One read's company budget: the universe held to one cap, and an honest total. */
class ReportSourceLoaderTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final ProjectAudience READER = ProjectAudience.CLIENT;

    private final TriageCompanyReadService triage = mock(TriageCompanyReadService.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final CandidateService candidates = mock(CandidateService.class);
    private final PositionService positions = mock(PositionService.class);
    private final CandidatePipelineCatalog pipeline = mock(CandidatePipelineCatalog.class);

    @BeforeEach
    void aMandateWithNobodyMapped() {
        when(projects.findByIdAndWorkspaceId(PROJECT, WORKSPACE)).thenReturn(Optional.of(mock(Project.class)));
        when(candidates.listAllOfProject(eq(WORKSPACE), eq(PROJECT), anyInt(), eq(READER)))
                .thenReturn(new CandidatesResponse(List.of(), 0, 0, 0));
        when(pipeline.progressAnchors(WORKSPACE)).thenReturn(new PipelineProgressAnchors(1, 3));
    }

    @Test
    @DisplayName("a universe past the cap is read up to it, counted whole, and the read says it was cut")
    void aUniversePastTheCapIsCut() {
        universe(3, companies(3), 7);

        ReportSources sources = loaderCappedAt(3).load(WORKSPACE, PROJECT, READER);

        assertThat(sources.universe()).hasSize(3);
        assertThat(sources.universeTotal()).isEqualTo(7);
        assertThat(sources.isTruncated()).isTrue();
    }

    @Test
    @DisplayName("the band is read through the seam that never drafts a brief: a client seat can open the report")
    void theBriefIsNeverDraftedByARead() {
        universe(10, companies(0), 0);

        loaderCappedAt(10).load(WORKSPACE, PROJECT, READER);

        verify(positions).compensationOf(WORKSPACE, PROJECT);
        verify(positions, never()).get(WORKSPACE, PROJECT);
    }

    @Test
    @DisplayName("a mandate inside the cap is read whole")
    void insideTheCapNothingIsCut() {
        universe(10, companies(4), 4);

        ReportSources sources = loaderCappedAt(10).load(WORKSPACE, PROJECT, READER);

        assertThat(sources.universe()).hasSize(4);
        assertThat(sources.isTruncated()).isFalse();
    }

    private ReportSourceLoader loaderCappedAt(int maxCompanies) {
        return new ReportSourceLoader(projects, triage, candidates, pipeline, positions,
                capped(new ReportSettings(maxCompanies, 5000, 6, 8, 9, 3, 12)));
    }

    private void universe(int askedCap, List<TriageCompanyResponse> answered, long total) {
        when(triage.listAllOfStage(WORKSPACE, PROJECT, TriageCompanyStatus.IN_UNIVERSE, TriageCompanyFilters.none(),
                askedCap, READER))
                .thenReturn(new TriageCompaniesResponse(answered, total, 0, askedCap, null));
    }

    private static List<TriageCompanyResponse> companies(int count) {
        return IntStream.range(0, count).mapToObj(index -> company("Company " + index, "retail")).toList();
    }
}
