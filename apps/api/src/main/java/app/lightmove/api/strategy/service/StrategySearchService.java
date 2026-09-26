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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A mandate's saved searches. What gets saved is the strategy's stored filter, never a client payload.
 * A {@code PRIVATE} search answers only to its author, and every refusal on someone else's is a 404,
 * not a 403 — admitting the row exists is what the tier prevents.
 */
@Service
@RequiredArgsConstructor
public class StrategySearchService {

    private static final int MAX_SHARED_SEARCHES_PER_PROJECT = 50;

    /** Counted apart from the shared cap, so neither list can lock the other out. */
    private static final int MAX_PRIVATE_SEARCHES_PER_USER = 50;

    private final StrategySearchRepository searches;
    private final StrategyRepository strategies;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> list(UUID userId, UUID workspaceId, UUID projectId) {
        projects.requireInWorkspace(projectId, workspaceId);
        List<StrategySearch> visible = searches.findVisibleTo(projectId, userId);
        Map<UUID, String> authors = authorNames(visible);
        return visible.stream().map(search -> toDto(search, authors)).toList();
    }

    @Transactional
    public SavedSearchResponse save(UUID userId, UUID workspaceId, UUID projectId,
                                    SaveSearchRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        SearchVisibility visibility = request.visibility();
        requireRoomFor(projectId, userId, visibility);

        StrategyFilter filter = strategies.findByProjectId(projectId)
                .map(Strategy::getFilter)
                .orElseGet(StrategyFilter::empty);

        // The partial unique indexes are the real guard against two saves racing on one name;
        // GlobalExceptionHandler maps both to STRATEGY_SEARCH_NAME_TAKEN.
        StrategySearch saved = searches.save(
                StrategySearch.of(projectId, request.name().trim(), filter, visibility, userId));
        durable();

        audit.projectEvent(ProjectEventType.STRATEGY_SEARCH_SAVED, userId, workspaceId, projectId, httpRequest)
                .detail("searchId", saved.getId().toString())
                .detail("visibility", visibility.name())
                .record();
        return toDto(saved);
    }

    @Transactional
    public SavedSearchResponse update(UUID userId, UUID workspaceId, UUID projectId, UUID searchId,
                                      UpdateSearchRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        StrategySearch search = requireEditable(searchId, projectId, userId);
        SearchVisibility target = request.visibility();
        boolean movesTier = target != null && target != search.getVisibility();
        String newName = trimmedName(request);

        // Decide first, mutate after.
        if (movesTier) {
            if (!search.getCreatedBy().equals(userId)) {
                throw ApiException.userFacing(ErrorCode.FORBIDDEN,
                        "Only the person who saved a search can change who it is shared with.");
            }
            requireRoomFor(projectId, userId, target);
        }

        boolean renames = newName != null && !newName.equals(search.getName());
        if (renames) {
            search.rename(newName);
        }
        if (movesTier) {
            search.changeVisibility(target);
        }
        durable();

        // Two edits, two events: folding them into one lost whichever half was not chosen.
        if (renames) {
            audit.projectEvent(ProjectEventType.STRATEGY_SEARCH_RENAMED, userId, workspaceId, projectId, httpRequest)
                    .detail("searchId", searchId.toString())
                    .detail("name", search.getName())
                    .record();
        }
        if (movesTier) {
            audit.projectEvent(ProjectEventType.STRATEGY_SEARCH_VISIBILITY_CHANGED,
                    userId, workspaceId, projectId, httpRequest)
                    .detail("searchId", searchId.toString())
                    .detail("visibility", search.getVisibility().name())
                    .record();
        }
        return toDto(search);
    }

    @Transactional
    public SavedSearchResponse updateFilter(UUID userId, UUID workspaceId, UUID projectId, UUID searchId,
                                            HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        StrategySearch search = requireEditable(searchId, projectId, userId);
        search.replaceFilter(strategies.findByProjectId(projectId)
                .map(Strategy::getFilter)
                .orElseGet(StrategyFilter::empty));

        durable();

        audit.projectEvent(ProjectEventType.STRATEGY_SEARCH_FILTER_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("searchId", searchId.toString())
                .record();
        return toDto(search);
    }

    @Transactional
    public void delete(UUID userId, UUID workspaceId, UUID projectId, UUID searchId,
                       HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        searches.delete(requireEditable(searchId, projectId, userId));

        audit.projectEvent(ProjectEventType.STRATEGY_SEARCH_DELETED, userId, workspaceId, projectId, httpRequest)
                .detail("searchId", searchId.toString())
                .record();
    }

    private StrategySearch requireEditable(UUID searchId, UUID projectId, UUID userId) {
        StrategySearch search = searches.findByIdAndProjectId(searchId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (search.isHiddenFrom(userId)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return search;
    }

    private void requireRoomFor(UUID projectId, UUID userId, SearchVisibility visibility) {
        if (visibility == SearchVisibility.SHARED) {
            if (searches.countByProjectIdAndVisibility(projectId, SearchVisibility.SHARED)
                    >= MAX_SHARED_SEARCHES_PER_PROJECT) {
                throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "This mandate already has the maximum number of shared searches.");
            }
        } else if (searches.countByProjectIdAndCreatedByAndVisibility(projectId, userId,
                SearchVisibility.PRIVATE) >= MAX_PRIVATE_SEARCHES_PER_USER) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "You already have the maximum number of private searches on this mandate.");
        }
    }

    /**
     * Must run <b>before</b> the audit event: the {@code @Async REQUIRES_NEW} audit write survives a
     * rollback and would report a name collision as a success, and {@code updated_at} is only set at flush.
     */
    private void durable() {
        searches.flush();
    }

    /** Absent leaves the name alone; blank is refused. */
    private static String trimmedName(UpdateSearchRequest request) {
        if (request.name() == null) {
            return null;
        }
        String trimmed = request.name().trim();
        if (trimmed.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "A name is required");
        }
        return trimmed;
    }

    private Map<UUID, String> authorNames(List<StrategySearch> visible) {
        return users
                .findAllById(visible.stream().map(StrategySearch::getCreatedBy).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private SavedSearchResponse toDto(StrategySearch search) {
        return toDto(search, authorNames(List.of(search)));
    }

    private static SavedSearchResponse toDto(StrategySearch search, Map<UUID, String> authors) {
        return new SavedSearchResponse(search.getId(), search.getName(),
                StrategyFilterDto.of(search.getFilter()), search.getVisibility(),
                search.getCreatedBy(), authors.get(search.getCreatedBy()),
                search.getCreatedAt(), search.getUpdatedAt());
    }
}
