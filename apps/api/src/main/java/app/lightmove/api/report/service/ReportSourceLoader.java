package app.lightmove.api.report.service;

import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.report.model.ReportSources;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Gathers one read's inputs through the seams the package doc names. The mandate itself is resolved
 * by {@code (id, workspaceId)} first, so a foreign project is a 404 before any of its rows are read.
 *
 * <p>The brief is read through {@code PositionService.get}, which drafts one for a mandate that
 * predates the position tables — the same thing opening the Position tab does, so a report read
 * changes nothing that read would not.
 */
@Component
class ReportSourceLoader {

    private final ProjectRepository projects;
    private final TriageCompanyService triage;
    private final CandidateService candidates;
    private final PositionService positions;
    private final ReportSettings caps;

    ReportSourceLoader(ProjectRepository projects, TriageCompanyService triage, CandidateService candidates,
                       PositionService positions, LightMoveProperties properties) {
        this.projects = projects;
        this.triage = triage;
        this.candidates = candidates;
        this.positions = positions;
        this.caps = properties.report();
    }

    ReportSources load(UUID workspaceId, UUID projectId) {
        Project project = projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        TriageCompaniesResponse inUniverse = stage(workspaceId, projectId, TriageCompanyStatus.IN_UNIVERSE);
        TriageCompaniesResponse shortlisted = stage(workspaceId, projectId, TriageCompanyStatus.SHORTLISTED);
        List<TriageCompanyResponse> universe = new ArrayList<>(inUniverse.companies());
        universe.addAll(shortlisted.companies());

        CandidatesResponse people = candidates.listAllOfProject(workspaceId, projectId, caps.maxCandidates());
        List<ExecutiveRow> executives = pair(people, universe);

        return new ReportSources(project, universe, inUniverse.totalCount() + shortlisted.totalCount(),
                executives, people.totalCount(), positions.get(workspaceId, projectId).compensation());
    }

    private TriageCompaniesResponse stage(UUID workspaceId, UUID projectId, TriageCompanyStatus status) {
        return triage.listAllOfStage(workspaceId, projectId, status, caps.maxCompanies());
    }

    private static List<ExecutiveRow> pair(CandidatesResponse people, List<TriageCompanyResponse> universe) {
        Map<UUID, TriageCompanyResponse> companyById = universe.stream()
                .collect(Collectors.toMap(TriageCompanyResponse::id, Function.identity()));
        return people.candidates().stream()
                .map(person -> new ExecutiveRow(person, companyById.get(person.triageCompanyId())))
                .toList();
    }
}
