package app.lightmove.api.publicapi.service;

import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.ExportSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.apikey.PublicReader;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.pairing.model.PairedStage;
import app.lightmove.api.pairing.service.StagePairingService;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.service.ProjectService;
import app.lightmove.api.publicapi.dto.PublicCandidate;
import app.lightmove.api.publicapi.dto.PublicCompany;
import app.lightmove.api.publicapi.dto.PublicPage;
import app.lightmove.api.publicapi.dto.PublicProject;
import app.lightmove.api.publicapi.dto.PublicUniverse;
import app.lightmove.api.publicapi.dto.PublicUniverseCompany;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyListCriteria;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.List;
import java.util.Locale;
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
    private final LightMoveProperties properties;

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
                : readableByUser(reader, projects.list(reader.userId(), reader.workspaceId()));
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
        return PublicProject.of(projects.get(reader.workspaceId(), projectId));
    }

    public PublicPage<PublicCompany> companies(PublicReader reader, UUID projectId, String stage, Integer page,
                                               Integer size) {
        TriageCompaniesResponse found = companies.list(reader.workspaceId(), projectId,
                new TriageCompanyListCriteria(stage, null, null, null, null, null, page, size));
        List<PublicCompany> rows = found.companies().stream().map(PublicCompany::of).toList();
        return new PublicPage<>(rows, found.page(), found.size(), found.totalCount());
    }

    public PublicPage<PublicCandidate> candidates(PublicReader reader, UUID projectId, String status,
                                                  UUID companyId, Integer page, Integer size) {
        int pageSize = size == null ? properties.company().list().defaultPageSize() : size;
        CandidatesResponse found = candidates.list(reader.workspaceId(), projectId, new CandidateListCriteria(
                companyId == null ? null : List.of(companyId), null, null, status, page, pageSize));
        boolean withContacts = reader.holds(ApiKeyScope.CANDIDATE_CONTACTS_READ);
        boolean withCompensation = reader.holds(ApiKeyScope.CANDIDATE_COMPENSATION_READ);
        List<PublicCandidate> rows = found.candidates().stream()
                .map(candidate -> PublicCandidate.of(candidate, withContacts, withCompensation))
                .toList();
        return new PublicPage<>(rows, found.page(), found.size(), found.totalCount());
    }

    /** Refused past the export caps rather than truncated: a partial universe reads as a whole one. */
    public PublicUniverse universe(PublicReader reader, UUID projectId, String stage) {
        TriageCompanyStatus status = TriageCompanyStatus.parseOrInUniverse(stage);
        ExportSettings caps = properties.export();
        PairedStage paired = pairing.pair(reader.workspaceId(), projectId, status, TriageCompanyFilters.none(),
                caps.maxCompanies(), caps.maxCandidates());
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

    private List<ProjectResponse> readableByUser(PublicReader reader, List<ProjectResponse> listed) {
        Set<UUID> readable = projectAccess.projectsWithAction(reader.userId(), reader.workspaceId(),
                listed.stream().map(ProjectResponse::id).toList(), ProjectAction.WORK_VIEW);
        return listed.stream().filter(project -> readable.contains(project.id())).toList();
    }
}
