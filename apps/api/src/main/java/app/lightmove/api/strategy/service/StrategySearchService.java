package app.lightmove.api.strategy.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.strategy.constant.SearchVisibility;
import app.lightmove.api.strategy.dto.SaveSearchRequest;
import app.lightmove.api.strategy.dto.SavedSearchResponse;
import app.lightmove.api.strategy.dto.StrategyFilterDto;
import app.lightmove.api.strategy.dto.UpdateSearchRequest;
import app.lightmove.api.strategy.model.Strategy;
import app.lightmove.api.strategy.model.StrategyFilter;
import app.lightmove.api.strategy.model.StrategySearch;
import app.lightmove.api.strategy.repository.StrategyRepository;
import app.lightmove.api.strategy.repository.StrategySearchRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * under a filter the mandate never actually ran. Re-capturing onto an existing search
 * ({@link #updateFilter}) reads the same source for the same reason.
 *
 * <p>Loading a search is deliberately not an endpoint. The client applies the returned filter and lets
 * the ordinary autosave persist it, so loading a search and editing chips are one code path with one
 * set of invalidations rather than two that can drift.
 *
 * <p>Two tiers, one gate. {@code PROJECT_EDIT} decides who may leave a search behind at all; within
 * that, a {@code PRIVATE} search answers only to its author. Every refusal on someone else's private
 * search is a 404 rather than a 403 — telling a teammate the row exists is the one thing the tier
 * exists to prevent.
 */
@Service
@RequiredArgsConstructor
public class StrategySearchService {

    /** A working ceiling on the team's list. Past this a dropdown stops being a way to find anything. */
    private static final int MAX_SHARED_SEARCHES_PER_PROJECT = 50;

    /**
     * The same ceiling on one person's scratch list. Counted separately so that filling your own list
     * cannot lock the mandate out of saving a shared search, or the reverse.
     */
    private static final int MAX_PRIVATE_SEARCHES_PER_USER = 50;

    private final StrategySearchRepository searches;
    private final StrategyRepository strategies;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final AuditService audit;

    /** A project's saved searches, by name — part of the Strategy screen's first read. */
    @Transactional(readOnly = true)
    public List<SavedSearchResponse> list(UUID userId, UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        List<StrategySearch> visible = searches.findVisibleTo(projectId, userId);
        Map<UUID, String> authors = authorNames(visible);
        return visible.stream().map(search -> toDto(search, authors)).toList();
    }

    @Transactional
    public SavedSearchResponse save(UUID userId, UUID workspaceId, UUID projectId,
                                    SaveSearchRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        SearchVisibility visibility = request.visibilityOrShared();
        requireRoomFor(projectId, userId, visibility);

        StrategyFilter filter = strategies.findByProjectId(projectId)
                .map(Strategy::getFilter)
                .orElseGet(StrategyFilter::empty);

        // The partial unique indexes are the real guard — two saves racing on the same name both pass
        // any pre-check and only one can pass the index. GlobalExceptionHandler maps both of them to
        // STRATEGY_SEARCH_NAME_TAKEN, so a race and the ordinary case answer the same way.
        StrategySearch saved = searches.save(
                StrategySearch.of(projectId, request.name().trim(), filter, visibility, userId));

        audit.event(ProjectEventType.STRATEGY_SEARCH_SAVED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("searchId", saved.getId().toString())
                .detail("visibility", visibility.name())
                .record();
        return toDto(saved, authorNames(List.of(saved)));
    }

    @Transactional
    public SavedSearchResponse update(UUID userId, UUID workspaceId, UUID projectId, UUID searchId,
                                      UpdateSearchRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        StrategySearch search = requireEditable(searchId, projectId, userId);
        search.rename(request.name().trim());

        SearchVisibility target = request.visibility();
        if (target != null && target != search.getVisibility()) {
            // Only the author moves a search between tiers. A teammate pulling a shared search private
            // would take the mandate's work out of the mandate's hands, which is not collaboration.
            if (!search.getCreatedBy().equals(userId)) {
                throw ApiException.userFacing(ErrorCode.FORBIDDEN,
                        "Only the person who saved a search can change who it is shared with.");
            }
            requireRoomFor(projectId, userId, target);
            search.changeVisibility(target);
        }

        audit.event(ProjectEventType.STRATEGY_SEARCH_RENAMED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("searchId", searchId.toString())
                .detail("visibility", search.getVisibility().name())
                .record();
        return toDto(search, authorNames(List.of(search)));
    }

    /** Re-capture the mandate's current filter onto a search that already has a name. */
    @Transactional
    public SavedSearchResponse updateFilter(UUID userId, UUID workspaceId, UUID projectId, UUID searchId,
                                            HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        StrategySearch search = requireEditable(searchId, projectId, userId);
        search.replaceFilter(strategies.findByProjectId(projectId)
                .map(Strategy::getFilter)
                .orElseGet(StrategyFilter::empty));

        audit.event(ProjectEventType.STRATEGY_SEARCH_FILTER_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("searchId", searchId.toString())
                .record();
        return toDto(search, authorNames(List.of(search)));
    }

    @Transactional
    public void delete(UUID userId, UUID workspaceId, UUID projectId, UUID searchId,
                       HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        searches.delete(requireEditable(searchId, projectId, userId));

        audit.event(ProjectEventType.STRATEGY_SEARCH_DELETED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("searchId", searchId.toString())
                .record();
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private StrategySearch requireEditable(UUID searchId, UUID projectId, UUID userId) {
        StrategySearch search = searches.findByIdAndProjectId(searchId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (search.isPrivateTo(userId)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return search;
    }

    private void requireRoomFor(UUID projectId, UUID userId, SearchVisibility visibility) {
        long held = visibility == SearchVisibility.SHARED
                ? searches.countByProjectIdAndVisibility(projectId, SearchVisibility.SHARED)
                : searches.countByProjectIdAndCreatedByAndVisibility(projectId, userId,
                        SearchVisibility.PRIVATE);
        int ceiling = visibility == SearchVisibility.SHARED
                ? MAX_SHARED_SEARCHES_PER_PROJECT
                : MAX_PRIVATE_SEARCHES_PER_USER;
        if (held >= ceiling) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    visibility == SearchVisibility.SHARED
                            ? "This mandate already has the maximum number of shared searches."
                            : "You already have the maximum number of private searches on this mandate.");
        }
    }

    private Map<UUID, String> authorNames(List<StrategySearch> visible) {
        Map<UUID, String> byId = new HashMap<>();
        // Not Collectors.toMap: a federated account can reach us without a name, and a null value
        // there throws rather than leaving the row unattributed.
        users.findAllById(visible.stream().map(StrategySearch::getCreatedBy).distinct().toList())
                .forEach(user -> byId.put(user.getId(), user.getFullName()));
        return byId;
    }

    private static SavedSearchResponse toDto(StrategySearch search, Map<UUID, String> authors) {
        return new SavedSearchResponse(search.getId(), search.getName(),
                StrategyFilterDto.of(search.getFilter()), search.getVisibility(),
                search.getCreatedBy(), authors.get(search.getCreatedBy()),
                search.getCreatedAt(), search.getUpdatedAt());
    }
}
