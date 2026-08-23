package app.lightmove.api.strategy.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.strategy.dto.RenameSearchRequest;
import app.lightmove.api.strategy.dto.SaveSearchRequest;
import app.lightmove.api.strategy.dto.SavedSearchResponse;
import app.lightmove.api.strategy.dto.StrategyFilterDto;
import app.lightmove.api.strategy.model.Strategy;
import app.lightmove.api.strategy.model.StrategyFilter;
import app.lightmove.api.strategy.model.StrategySearch;
import app.lightmove.api.strategy.repository.StrategyRepository;
import app.lightmove.api.strategy.repository.StrategySearchRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The named filters a mandate has saved — the toolbar's "Save Search" and the dropdown that loads
 * them back.
 *
 * <p><b>What gets saved is the strategy's stored filter, not a payload from the client.</b> The screen
 * autosaves every chip click, so the stored filter already is what is on screen, and taking the
 * client's word for it a second time would only create a way for the two to disagree — a search saved
 * under a filter the mandate never actually ran.
 *
 * <p>Loading a search is deliberately not an endpoint. The client applies the returned filter and lets
 * the ordinary autosave persist it, so loading a search and editing chips are one code path with one
 * set of invalidations rather than two that can drift.
 */
@Service
@RequiredArgsConstructor
public class StrategySearchService {

    /** A working ceiling. Past this a dropdown stops being a way to find anything. */
    private static final int MAX_SEARCHES_PER_PROJECT = 50;

    private final StrategySearchRepository searches;
    private final StrategyRepository strategies;
    private final ProjectRepository projects;
    private final AuditService audit;

    /** A project's saved searches, by name — part of the Strategy screen's first read. */
    @Transactional(readOnly = true)
    public List<SavedSearchResponse> list(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        return searches.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(StrategySearchService::toDto)
                .toList();
    }

    @Transactional
    public SavedSearchResponse save(UUID userId, UUID workspaceId, UUID projectId,
                                    SaveSearchRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        if (searches.countByProjectId(projectId) >= MAX_SEARCHES_PER_PROJECT) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "This mandate already has the maximum number of saved searches.");
        }
        StrategyFilter filter = strategies.findByProjectId(projectId)
                .map(Strategy::getFilter)
                .orElseGet(StrategyFilter::empty);

        // The unique index on (project_id, lower(name)) is the real guard — two saves racing on the
        // same name both pass a pre-check and only one can pass the index. GlobalExceptionHandler
        // turns that constraint into the same message, so this path exists for the ordinary case only.
        StrategySearch saved = searches.save(
                StrategySearch.of(projectId, request.name().trim(), filter, userId));

        audit.event(ProjectEventType.STRATEGY_SEARCH_SAVED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("searchId", saved.getId().toString())
                .record();
        return toDto(saved);
    }

    @Transactional
    public SavedSearchResponse rename(UUID userId, UUID workspaceId, UUID projectId, UUID searchId,
                                      RenameSearchRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        StrategySearch search = searches.findByIdAndProjectId(searchId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        search.rename(request.name().trim());

        audit.event(ProjectEventType.STRATEGY_SEARCH_RENAMED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("searchId", searchId.toString())
                .record();
        return toDto(search);
    }

    @Transactional
    public void delete(UUID userId, UUID workspaceId, UUID projectId, UUID searchId,
                       HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        StrategySearch search = searches.findByIdAndProjectId(searchId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        searches.delete(search);

        audit.event(ProjectEventType.STRATEGY_SEARCH_DELETED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("searchId", searchId.toString())
                .record();
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private static SavedSearchResponse toDto(StrategySearch search) {
        return new SavedSearchResponse(search.getId(), search.getName(),
                StrategyFilterDto.of(search.getFilter()), search.getCreatedAt());
    }
}
