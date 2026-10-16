package app.uncava.api.publicapi.service;

import app.uncava.api.candidate.dto.CandidateListCriteria;
import app.uncava.api.candidate.dto.CandidateResponse;
import app.uncava.api.candidate.dto.CandidatesResponse;
import app.uncava.api.candidate.service.CandidateService;
import app.uncava.api.core.config.CompanyListSettings;
import app.uncava.api.core.config.ExportSettings;
import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.core.security.apikey.ApiKeyScope;
import app.uncava.api.core.security.apikey.PublicReader;
import app.uncava.api.core.security.rbac.ProjectAccess;
import app.uncava.api.core.security.rbac.ProjectAction;
import app.uncava.api.core.security.rbac.ProjectAudience;
import app.uncava.api.pairing.model.PairedStage;
import app.uncava.api.pairing.service.StagePairingService;
import app.uncava.api.project.constant.ProjectListScope;
import app.uncava.api.project.dto.ProjectResponse;
import app.uncava.api.project.service.ProjectService;
import app.uncava.api.publicapi.dto.PublicCandidate;
import app.uncava.api.publicapi.dto.PublicCompany;
import app.uncava.api.publicapi.dto.PublicCompanyDetail;
import app.uncava.api.publicapi.dto.PublicPage;
import app.uncava.api.publicapi.dto.PublicPositionSummary;
import app.uncava.api.publicapi.dto.PublicProject;
import app.uncava.api.publicapi.dto.PublicStageCounts;
import app.uncava.api.publicapi.dto.PublicTimeline;
import app.uncava.api.publicapi.dto.PublicUniverse;
import app.uncava.api.publicapi.dto.PublicUniverseCompany;
import app.uncava.api.triagecompany.constant.TriageCompanyStatus;
import app.uncava.api.triagecompany.dto.TriageCompaniesResponse;
import app.uncava.api.triagecompany.dto.TriageCompanyListCriteria;
import app.uncava.api.triagecompany.dto.TriageCountsDto;
import app.uncava.api.triagecompany.model.TriageCompanyFilters;
import app.uncava.api.triagecompany.service.TriageCompanyReadService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The public reads, each through the service the screens read with, so every rule those apply still holds, then
 * narrowed to the public shape. Shared by the public API and the MCP tools; each audits its own reads, since rows
 * leaving through either are an export.
 */
@Service
@RequiredArgsConstructor
public class PublicReadService {

    private final ProjectService projects;
    private final ProjectAccess projectAccess;
    private final TriageCompanyReadService companies;
    private final CandidateService candidates;
    private final StagePairingService pairing;
    private final UncavaProperties properties;

    /**
     * A personal reader lists only the positions its user may open; a workspace key lists every one. Paged
     * after assembly, which is a fixed number of batched queries however many positions there are.
     */
    public PublicPage<PublicProject> projects(PublicReader reader, String title, Integer page, Integer size) {
        CompanyListSettings paging = properties.company().list();
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? paging.defaultPageSize() : size;
        paging.requireValidPage(pageNumber, pageSize);
        List<ProjectResponse> visible = reader.readsWholeWorkspace()
                ? projects.listInWorkspace(reader.workspaceId())
                : readableByUser(reader,
                        projects.list(reader.userId(), reader.workspaceId(), ProjectListScope.LIVE));
        String wanted = title == null ? "" : title.strip().toLowerCase(Locale.ROOT);
        List<ProjectResponse> matching = visible.stream()
                .filter(project -> project.positionTitle().toLowerCase(Locale.ROOT).contains(wanted))
                .toList();
        List<PublicProject> rows = matching.stream()
                .skip((long) pageNumber * pageSize)
                .limit(pageSize)
                .map(PublicProject::of)
                .toList();
        return new PublicPage<>(rows, pageNumber, pageSize, matching.size());
    }

    public PublicProject project(PublicReader reader, UUID projectId) {
        return PublicProject.of(projects.get(reader.workspaceId(), projectId, audienceOf(reader, projectId)));
    }

    public PublicPage<PublicCompany> companies(PublicReader reader, UUID projectId, String stage, Integer page,
                                               Integer size) {
        TriageCompaniesResponse found = companies.list(reader.workspaceId(), projectId,
                new TriageCompanyListCriteria(stage, null, null, null, null, null, page, size),
                audienceOf(reader, projectId));
        List<PublicCompany> rows = found.companies().stream().map(PublicCompany::of).toList();
        return new PublicPage<>(rows, found.page(), found.size(), found.totalCount());
    }

    /**
     * A position's executives, narrowed by status, by company, by text in a name, title or employer, and by the stage
     * of the company they are mapped at — an executive at no company is at no stage.
     */
    public PublicPage<PublicCandidate> candidates(PublicReader reader, UUID projectId, String status,
                                                  UUID companyId, String query, String stage, Integer page,
                                                  Integer size) {
        return candidates(reader, projectId, status, companyId, query, stage, page, size,
                audienceOf(reader, projectId));
    }

    private PublicPage<PublicCandidate> candidates(PublicReader reader, UUID projectId, String status,
                                                   UUID companyId, String query, String stage, Integer page,
                                                   Integer size, ProjectAudience audience) {
        int pageSize = size == null ? properties.company().list().defaultPageSize() : size;
        Set<UUID> stageCompanies = stage == null || stage.isBlank() ? null
                : companies.idsOfStage(reader.workspaceId(), projectId, TriageCompanyStatus.parseOrInUniverse(stage));
        CandidatesResponse found = candidates.list(reader.workspaceId(), projectId, new CandidateListCriteria(
                companyId == null ? null : List.of(companyId), null, null, query, stageCompanies, status, null,
                page, pageSize), audience);
        boolean withContacts = reader.holds(ApiKeyScope.CANDIDATE_CONTACTS_READ);
        boolean withCompensation = reader.holds(ApiKeyScope.CANDIDATE_COMPENSATION_READ);
        List<PublicCandidate> rows = found.candidates().stream()
                .map(candidate -> PublicCandidate.of(candidate, withContacts, withCompensation))
                .toList();
        return new PublicPage<>(rows, found.page(), found.size(), found.totalCount());
    }

    /** Where a position stands, in a fixed number of queries however large it is. */
    public PublicPositionSummary summary(PublicReader reader, UUID projectId) {
        ProjectAudience audience = audienceOf(reader, projectId);
        ProjectResponse project = projects.get(reader.workspaceId(), projectId, audience);
        TriageCountsDto stages = companies.stageCountsOf(reader.workspaceId(), projectId);
        Map<String, Long> executives = new LinkedHashMap<>();
        Map<String, Long> pipelineStages = new LinkedHashMap<>();
        candidates.statusCountsOf(reader.workspaceId(), projectId, audience).forEach((status, total) -> {
            executives.put(status.getStatusKey(), total);
            if (!status.getStage().isRetired() || total > 0) {
                pipelineStages.merge(status.getStage().getStageKey(), total, Long::sum);
            }
        });
        return new PublicPositionSummary(PublicProject.of(project),
                new PublicStageCounts(stages.inUniverse(), stages.shortlisted(), stages.declined()),
                executives, pipelineStages, project.mappedCompanies(),
                new PublicTimeline(project.startDate(), project.mappingTargetDate(), project.deliveryDate(),
                        project.targetDate()));
    }

    public PublicCandidate candidate(PublicReader reader, UUID projectId, UUID candidateId) {
        return PublicCandidate.of(candidates.get(reader.workspaceId(), projectId, candidateId,
                        audienceOf(reader, projectId)),
                reader.holds(ApiKeyScope.CANDIDATE_CONTACTS_READ),
                reader.holds(ApiKeyScope.CANDIDATE_COMPENSATION_READ));
    }

    /** The company, and its executives only for a reader who may read executives. */
    public PublicCompanyDetail company(PublicReader reader, UUID projectId, UUID companyId, Integer page,
                                       Integer size) {
        PublicCompany company = companyOf(reader, projectId, companyId);
        PublicPage<PublicCandidate> executives = reader.holds(ApiKeyScope.CANDIDATES_READ)
                ? candidates(reader, projectId, null, companyId, null, null, page, size,
                        audienceOf(reader, projectId))
                : null;
        return new PublicCompanyDetail(company, executives);
    }

    public PublicCompany companyOf(PublicReader reader, UUID projectId, UUID companyId) {
        return PublicCompany.of(companies.get(reader.workspaceId(), projectId, companyId));
    }

    /** Refused past the export caps rather than truncated: a partial universe reads as a whole one. */
    public PublicUniverse universe(PublicReader reader, UUID projectId, String stage) {
        TriageCompanyStatus status = TriageCompanyStatus.parseOrInUniverse(stage);
        ExportSettings caps = properties.export();
        PairedStage paired = pairing.pair(reader.workspaceId(), projectId, status, TriageCompanyFilters.none(),
                caps.maxCompanies(), caps.maxCandidates(), audienceOf(reader, projectId));
        refuseIfPast("companies", paired.companies().totalCount(), caps.maxCompanies());
        refuseIfPast("executives", paired.totalCandidates(), caps.maxCandidates());

        boolean withContacts = reader.holds(ApiKeyScope.CANDIDATE_CONTACTS_READ);
        boolean withCompensation = reader.holds(ApiKeyScope.CANDIDATE_COMPENSATION_READ);
        Function<CandidateResponse, PublicCandidate> toPublic =
                candidate -> PublicCandidate.of(candidate, withContacts, withCompensation);
        List<PublicUniverseCompany> rows = paired.companies().companies().stream()
                .map(company -> new PublicUniverseCompany(PublicCompany.of(company),
                        paired.peopleAt(company.id()).stream().map(toPublic).toList()))
                .toList();
        return new PublicUniverse(status.value(), rows, paired.unassigned().stream().map(toPublic).toList());
    }

    private static void refuseIfPast(String what, long total, int cap) {
        if (total > cap) {
            throw ApiException.userFacing(ErrorCode.PUBLIC_API_UNIVERSE_TOO_LARGE,
                    "This position has " + total + " " + what + ", past the limit of " + cap
                            + " for one call. Page through the companies and candidates routes instead.");
        }
    }

    /**
     * A workspace key is staff's by construction; anyone else reads a position as their own seat on it
     * does, so a member holding only its client seat never reads an executive hidden from the client.
     */
    private ProjectAudience audienceOf(PublicReader reader, UUID projectId) {
        return reader.readsWholeWorkspace() ? ProjectAudience.STAFF
                : projectAccess.audienceOf(reader.userId(), reader.workspaceId(), projectId);
    }

    private List<ProjectResponse> readableByUser(PublicReader reader, List<ProjectResponse> listed) {
        Set<UUID> readable = projectAccess.projectsWithAction(reader.userId(), reader.workspaceId(),
                listed.stream().map(ProjectResponse::id).toList(), ProjectAction.WORK_VIEW);
        return listed.stream().filter(project -> readable.contains(project.id())).toList();
    }
}
