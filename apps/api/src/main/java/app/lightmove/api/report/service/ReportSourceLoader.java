package app.lightmove.api.report.service;

import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidatePipelineCatalog;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.report.model.ReportSources;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
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
 * <p>The band is read through {@code PositionService.compensationOf}, never {@code get}: that one
 * drafts and saves a brief for a mandate without one, and a client seat — read-only by definition —
 * can open the report. A mandate with no brief simply reports no band.
 */
@Component
class ReportSourceLoader {

    private final ProjectRepository projects;
    private final TriageCompanyReadService triage;
    private final CandidateService candidates;
    private final CandidatePipelineCatalog pipeline;
    private final PositionService positions;
    private final ReportSettings caps;

    ReportSourceLoader(ProjectRepository projects, TriageCompanyReadService triage, CandidateService candidates,
                       CandidatePipelineCatalog pipeline, PositionService positions, LightMoveProperties properties) {
        this.projects = projects;
        this.triage = triage;
        this.candidates = candidates;
        this.pipeline = pipeline;
        this.positions = positions;
        this.caps = properties.report();
    }

    ReportSources load(UUID workspaceId, UUID projectId, ProjectAudience audience) {
        Project project = projects.requireInWorkspace(projectId, workspaceId);

        TriageCompaniesResponse shortlisted = stage(workspaceId, projectId, TriageCompanyStatus.SHORTLISTED,
                caps.maxCompanies(), audience);
        int budgetLeft = caps.maxCompanies() - shortlisted.companies().size();
        TriageCompaniesResponse inUniverse = stage(workspaceId, projectId, TriageCompanyStatus.IN_UNIVERSE,
                Math.max(budgetLeft, 1), audience);
        List<TriageCompanyResponse> universe = new ArrayList<>(
                inUniverse.companies().stream().limit(Math.max(budgetLeft, 0)).toList());
        universe.addAll(shortlisted.companies());

        CandidatesResponse people = candidates.listAllOfProject(workspaceId, projectId, caps.maxCandidates(),
                audience);
        int interestedFrom = pipeline.progressAnchors(workspaceId).interestedFrom();
        List<ExecutiveRow> executives = pair(people, universe, interestedFrom);

        return new ReportSources(project, universe, inUniverse.totalCount() + shortlisted.totalCount(),
                executives, people.totalCount(), positions.compensationOf(workspaceId, projectId));
    }

    /**
     * One cap for the whole universe, not one per stage. The shortlist is read first because it is
     * the smaller and the more worked; the rest of the budget goes to the companies still in universe.
     * A page cannot be empty, so a spent budget still reads one row — for the stage's total — and keeps none.
     */
    private TriageCompaniesResponse stage(UUID workspaceId, UUID projectId, TriageCompanyStatus status, int cap,
                                          ProjectAudience audience) {
        return triage.listAllOfStage(workspaceId, projectId, status, TriageCompanyFilters.none(), cap, audience);
    }

    private static List<ExecutiveRow> pair(CandidatesResponse people, List<TriageCompanyResponse> universe,
                                           int interestedFrom) {
        Map<UUID, TriageCompanyResponse> companyById = universe.stream()
                .collect(Collectors.toMap(TriageCompanyResponse::id, Function.identity()));
        return people.candidates().stream()
                .map(person -> new ExecutiveRow(person, companyById.get(person.triageCompanyId()), interestedFrom))
                .toList();
    }
}
