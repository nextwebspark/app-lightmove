package app.lightmove.api.publicapi.service;

import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.ExportSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.apikey.ApiKeyKind;
import app.lightmove.api.core.security.apikey.ApiKeyPrincipal;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
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
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The public API's reads, each through the service the screens read with, so every rule those apply
 * still holds, then narrowed to the public shape. Every read is audited: rows leaving through a key are
 * an export.
 */
@Service
@RequiredArgsConstructor
public class PublicReadService {

    private final ProjectService projects;
    private final ProjectAccess projectAccess;
    private final TriageCompanyReadService companies;
    private final CandidateService candidates;
    private final AuditService audit;
    private final StagePairingService pairing;
    private final LightMoveProperties properties;

    /**
     * A personal key lists only the positions its owner may open; a workspace key lists every one. Paged
     * after assembly, which is a fixed number of batched queries however many positions there are.
     */
    public PublicPage<PublicProject> projects(ApiKeyPrincipal key, String title, Integer page, Integer size,
                                              HttpServletRequest request) {
        CompanyListSettings paging = properties.company().list();
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? paging.defaultPageSize() : size;
        paging.requireValidPage(pageNumber, pageSize);
        List<ProjectResponse> visible = key.kind() == ApiKeyKind.SERVICE
                ? projects.listInWorkspace(key.workspaceId())
                : readableByOwner(key, projects.list(key.ownerUserId(), key.workspaceId()));
        String wanted = title == null ? "" : title.strip().toLowerCase(Locale.ROOT);
        List<ProjectResponse> matching = visible.stream()
                .filter(project -> project.positionTitle().toLowerCase(Locale.ROOT).contains(wanted))
                .toList();
        List<PublicProject> rows = matching.stream()
                .skip((long) pageNumber * pageSize)
                .limit(pageSize)
                .map(PublicProject::of)
                .toList();
        record(key, null, request, rows.size());
        return new PublicPage<>(rows, pageNumber, pageSize, matching.size());
    }

    public PublicProject project(ApiKeyPrincipal key, UUID projectId, HttpServletRequest request) {
        PublicProject project = PublicProject.of(projects.get(key.workspaceId(), projectId));
        record(key, projectId, request, 1);
        return project;
    }

    public PublicPage<PublicCompany> companies(ApiKeyPrincipal key, UUID projectId, String stage, Integer page,
                                               Integer size, HttpServletRequest request) {
        TriageCompaniesResponse found = companies.list(key.workspaceId(), projectId,
                new TriageCompanyListCriteria(stage, null, null, null, null, null, page, size));
        List<PublicCompany> rows = found.companies().stream().map(PublicCompany::of).toList();
        record(key, projectId, request, rows.size());
        return new PublicPage<>(rows, found.page(), found.size(), found.totalCount());
    }

    public PublicPage<PublicCandidate> candidates(ApiKeyPrincipal key, UUID projectId, String status,
                                                  UUID companyId, Integer page, Integer size,
                                                  HttpServletRequest request) {
        int pageSize = size == null ? properties.company().list().defaultPageSize() : size;
        CandidatesResponse found = candidates.list(key.workspaceId(), projectId, new CandidateListCriteria(
                companyId == null ? null : List.of(companyId), null, null, status, page, pageSize));
        boolean withContacts = key.holds(ApiKeyScope.CANDIDATE_CONTACTS_READ);
        boolean withCompensation = key.holds(ApiKeyScope.CANDIDATE_COMPENSATION_READ);
        List<PublicCandidate> rows = found.candidates().stream()
                .map(candidate -> PublicCandidate.of(candidate, withContacts, withCompensation))
                .toList();
        record(key, projectId, request, rows.size());
        return new PublicPage<>(rows, found.page(), found.size(), found.totalCount());
    }

    /** Refused past the export caps rather than truncated: a partial universe reads as a whole one. */
    public PublicUniverse universe(ApiKeyPrincipal key, UUID projectId, String stage, HttpServletRequest request) {
        TriageCompanyStatus status = TriageCompanyStatus.parseOrInUniverse(stage);
        ExportSettings caps = properties.export();
        PairedStage paired = pairing.pair(key.workspaceId(), projectId, status, TriageCompanyFilters.none(),
                caps.maxCompanies(), caps.maxCandidates());
        refuseIfPast("companies", paired.companies().totalCount(), caps.maxCompanies());
        refuseIfPast("executives", paired.totalCandidates(), caps.maxCandidates());

        boolean withContacts = key.holds(ApiKeyScope.CANDIDATE_CONTACTS_READ);
        boolean withCompensation = key.holds(ApiKeyScope.CANDIDATE_COMPENSATION_READ);
        Function<CandidateResponse, PublicCandidate> toPublic =
                candidate -> PublicCandidate.of(candidate, withContacts, withCompensation);
        List<PublicUniverseCompany> rows = paired.companies().companies().stream()
                .map(company -> new PublicUniverseCompany(PublicCompany.of(company),
                        paired.peopleAt(company.id()).stream().map(toPublic).toList()))
                .toList();
        record(key, projectId, request, rows.size() + paired.people().size());
        return new PublicUniverse(status.value(), rows, paired.unassigned().stream().map(toPublic).toList());
    }

    private static void refuseIfPast(String what, long total, int cap) {
        if (total > cap) {
            throw ApiException.userFacing(ErrorCode.PUBLIC_API_UNIVERSE_TOO_LARGE,
                    "This position has " + total + " " + what + ", past the limit of " + cap
                            + " for one call. Page through the companies and candidates routes instead.");
        }
    }

    private List<ProjectResponse> readableByOwner(ApiKeyPrincipal key, List<ProjectResponse> listed) {
        Set<UUID> readable = projectAccess.projectsWithAction(key.ownerUserId(), key.workspaceId(),
                listed.stream().map(ProjectResponse::id).toList(), ProjectAction.WORK_VIEW);
        return listed.stream().filter(project -> readable.contains(project.id())).toList();
    }

    private void record(ApiKeyPrincipal key, UUID projectId, HttpServletRequest request, int rows) {
        AuditService.Builder event = audit.event(ProjectEventType.PUBLIC_API_READ)
                .actor(key.ownerUserId())
                .workspace(key.workspaceId())
                .from(request)
                .detail("keyId", key.keyId().toString())
                .detail("kind", key.kind().name())
                .detail("endpoint", request.getRequestURI())
                .detail("rows", rows);
        if (projectId != null) {
            event.target(AuditService.PROJECT_TARGET, projectId);
        }
        event.record();
    }
}
