package app.lightmove.api.publicapi.service;

import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.security.apikey.ApiKeyKind;
import app.lightmove.api.core.security.apikey.ApiKeyPrincipal;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.service.ProjectService;
import app.lightmove.api.publicapi.dto.PublicCandidate;
import app.lightmove.api.publicapi.dto.PublicCompany;
import app.lightmove.api.publicapi.dto.PublicList;
import app.lightmove.api.publicapi.dto.PublicPage;
import app.lightmove.api.publicapi.dto.PublicProject;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyListCriteria;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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

    /** A personal key lists only the positions its owner may open; a workspace key lists every one. */
    public PublicList<PublicProject> projects(ApiKeyPrincipal key, String title, HttpServletRequest request) {
        List<ProjectResponse> visible = key.kind() == ApiKeyKind.SERVICE
                ? projects.listInWorkspace(key.workspaceId())
                : readableByOwner(key, projects.list(key.ownerUserId(), key.workspaceId()));
        String wanted = title == null ? "" : title.strip().toLowerCase(Locale.ROOT);
        List<PublicProject> rows = visible.stream()
                .filter(project -> project.positionTitle().toLowerCase(Locale.ROOT).contains(wanted))
                .map(PublicProject::of)
                .toList();
        record(key, null, request, rows.size());
        return new PublicList<>(rows);
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
        CandidatesResponse found = candidates.list(key.workspaceId(), projectId, new CandidateListCriteria(
                companyId == null ? null : List.of(companyId), null, null, status, page, size));
        boolean withContacts = key.holds(ApiKeyScope.CANDIDATE_CONTACTS_READ);
        boolean withCompensation = key.holds(ApiKeyScope.CANDIDATE_COMPENSATION_READ);
        List<PublicCandidate> rows = found.candidates().stream()
                .map(candidate -> PublicCandidate.of(candidate, withContacts, withCompensation))
                .toList();
        record(key, projectId, request, rows.size());
        return new PublicPage<>(rows, found.page(), found.size(), found.totalCount());
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
