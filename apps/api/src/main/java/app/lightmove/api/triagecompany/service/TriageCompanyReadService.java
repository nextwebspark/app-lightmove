package app.lightmove.api.triagecompany.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.TextUtils;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.triagecompany.constant.TriageCompanySortField;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyListCriteria;
import app.lightmove.api.triagecompany.dto.TriageCountsDto;
import app.lightmove.api.triagecompany.model.TriageCompany;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.repository.TriageCompanyRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading a mandate's triaged companies a stage at a time: the Companies grid's page with its three
 * header filters and sorts, and the unpaged whole stage the other features read.
 */
@Service
@RequiredArgsConstructor
public class TriageCompanyReadService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    /**
     * The one sort token deliberately kept outside {@link TriageCompanySortField}'s allowlist — see
     * {@link #findOrderedByExecutiveStatus} and the repository methods it calls.
     */
    private static final String EXECUTIVE_STATUS_SORT_TOKEN = "executiveStatus";

    /**
     * The Status column filter's wire tokens, mapped to the enum names {@code app_lm_project_candidate}
     * stores them under. Duplicated here rather than reusing {@code candidate}'s own
     * {@code CandidateStatus} enum — the same reason the rank query below embeds its own {@code CASE}
     * literals instead of importing it: {@code triagecompany} does not depend on {@code candidate}, by
     * the rule {@code candidate} itself states from the other side. The same spelling is mirrored again
     * in {@code CandidateRepository}'s ranking {@code CASE} and in
     * {@code MappedExecutiveLookupAdapter#triageCompanyIdsWithExecutiveStatusIn} — a rename should grep
     * for all three; {@code CandidateRepositoryStatusOrderTest} guards the one of those three that would
     * otherwise degrade silently.
     */
    private static final Map<String, String> EXECUTIVE_STATUS_TOKENS = Map.of(
            "identified", "IDENTIFIED",
            "contacted", "CONTACTED",
            "engaged", "ENGAGED",
            "interested", "INTERESTED",
            "notInterested", "NOT_INTERESTED",
            "offLimits", "OFF_LIMITS",
            "outOfScope", "OUT_OF_SCOPE");

    private final TriageCompanyRepository triaged;
    private final ProjectRepository projects;
    private final LightMoveProperties properties;
    private final MappedExecutiveLookup executives;

    /** One stage, with all three counts: the stage switcher is always visible, so a badge cannot lag. */
    @Transactional(readOnly = true)
    public TriageCompaniesResponse list(UUID workspaceId, UUID projectId,
                                        TriageCompanyListCriteria criteria) {
        CompanyListSettings listConfig = properties.company().list();
        int page = criteria.page() == null ? 0 : criteria.page();
        int size = criteria.size() == null ? listConfig.defaultPageSize() : criteria.size();
        listConfig.requireValidPage(page, size);
        TriageCompanyStatus status = TriageCompanyStatus.parseOrInUniverse(criteria.status());
        projects.requireInWorkspace(projectId, workspaceId);

        String companyName = TextUtils.blankToNull(criteria.nameQuery());
        String executiveName = TextUtils.blankToNull(criteria.executiveQuery());
        List<String> executiveStatuses = resolveExecutiveStatuses(criteria.executiveStatuses());
        Page<TriageCompany> found = EXECUTIVE_STATUS_SORT_TOKEN.equals(criteria.sort())
                ? findOrderedByExecutiveStatus(projectId, status, companyName, executiveName,
                        executiveStatuses, resolveDirection(criteria.direction()), PageRequest.of(page, size))
                : findWithFilters(projectId, status, companyName, executiveName, executiveStatuses,
                        PageRequest.of(page, size, resolveSort(criteria)));

        return new TriageCompaniesResponse(
                found.getContent().stream().map(TriageCompanyService::toDto).toList(),
                found.getTotalElements(), page, size, countsFor(projectId));
    }

    /**
     * The ordinary path: the server's own ORDER BY, over whichever of the grid's three header filters —
     * company name, executive name, executive status, any combination or none — the caller supplied.
     * The executive-based two are resolved to a company id set through {@link #executives} before this
     * ever reaches the repository, which is why {@code triagecompany}'s own queries need nothing more
     * than that set and the plain company-name filter they already had.
     */
    private Page<TriageCompany> findWithFilters(UUID projectId, TriageCompanyStatus status,
                                                String companyName, String executiveName,
                                                List<String> executiveStatuses,
                                                PageRequest pageRequest) {
        if (executiveName == null && executiveStatuses.isEmpty()) {
            return companyName == null
                    ? triaged.findByProjectIdAndStatus(projectId, status, pageRequest)
                    : triaged.findByProjectIdAndStatusAndCompanyNameContainingIgnoreCase(
                            projectId, status, companyName, pageRequest);
        }
        Set<UUID> matchingIds = matchingExecutiveIds(projectId, executiveName, executiveStatuses);
        if (matchingIds.isEmpty()) {
            return Page.empty(pageRequest);
        }
        return triaged.findByProjectIdAndStatusAndIdInAndCompanyNameFilter(
                projectId, status, matchingIds, companyName, pageRequest);
    }

    /**
     * Companies with a mapped executive answering the Executive-name filter, the Status checkbox
     * filter, or — when both are supplied — their intersection: a company is kept only by an executive
     * satisfying both at once is not what two independent header filters mean, so each is resolved on
     * its own and the two sets are narrowed together rather than asking either lookup to know about
     * the other.
     */
    private Set<UUID> matchingExecutiveIds(UUID projectId, String executiveName, List<String> executiveStatuses) {
        Set<UUID> byName = executiveName == null
                ? null : executives.triageCompanyIdsMatchingExecutiveName(projectId, executiveName);
        Set<UUID> byStatus = executiveStatuses.isEmpty()
                ? null : executives.triageCompanyIdsWithExecutiveStatusIn(projectId, executiveStatuses);
        if (byName == null) {
            return byStatus;
        }
        if (byStatus == null) {
            return byName;
        }
        Set<UUID> intersection = new HashSet<>(byName);
        intersection.retainAll(byStatus);
        return intersection;
    }

    /**
     * The one sort {@link #resolveSort} cannot express — see {@link MappedExecutiveLookup}. No
     * {@code Sort} on the {@code Pageable}: the ordering is baked into the query {@link #executives}
     * runs on the other side of the boundary, which is also why this asks for ids and re-fetches the
     * rows rather than asking {@code triaged} to rank its own page — that data lives in {@code candidate}.
     */
    private Page<TriageCompany> findOrderedByExecutiveStatus(UUID projectId, TriageCompanyStatus status,
                                                              String companyName, String executiveName,
                                                              List<String> executiveStatuses,
                                                              SortDirection direction, PageRequest pageRequest) {
        Page<UUID> ranked = executives.triageCompanyIdsRankedByExecutiveStatus(projectId, status,
                companyName, executiveName, executiveStatuses, direction == SortDirection.ASC, pageRequest);
        if (ranked.isEmpty()) {
            return new PageImpl<>(List.of(), pageRequest, ranked.getTotalElements());
        }
        Map<UUID, TriageCompany> byId = triaged.findAllById(ranked.getContent()).stream()
                .collect(Collectors.toMap(TriageCompany::getId, company -> company));
        // A ranked id can vanish between the two reads — another request deleted or moved it out of
        // this stage after the rank query saw it and before this one did. Dropped rather than left as
        // a null `toDto` would throw on: a row that no longer qualifies is exactly what a stale read
        // should leave out, not a 500 for whoever happened to page at the wrong moment.
        List<TriageCompany> ordered = ranked.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return new PageImpl<>(ordered, pageRequest, ranked.getTotalElements());
    }

    /**
     * Validates and translates the Status column's checkbox filter — a caller-supplied wire token the
     * client did not invent is a 400, exactly as {@link TriageCompanyStatus#parseOrInUniverse} treats an unknown stage. An
     * absent or empty list resolves to empty, which {@link #findWithFilters} and
     * {@link #findOrderedByExecutiveStatus} both read as "no opinion" rather than "match nothing".
     */
    private static List<String> resolveExecutiveStatuses(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        return tokens.stream().map(token -> {
            String resolved = EXECUTIVE_STATUS_TOKENS.get(token);
            if (resolved == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown executive status: " + token);
            }
            return resolved;
        }).collect(Collectors.toList());
    }

    /**
     * The whole of one stage, unpaged — the seam {@code talentmap} and {@code dataexport} read
     * companies through, because neither a globe nor a file has a page two. Takes a cap the caller
     * states and answers the total, so a caller past it can tell rather than quietly showing less.
     *
     * <p>{@code filters} narrows it through the same three header filters the paged read honours, for
     * an export that must carry what the screen was showing rather than more than it.
     * {@link TriageCompanyFilters#none()} is the whole stage.
     *
     * <p>Name order, not newest first: a stable order keeps the cut at the cap deterministic.
     */
    @Transactional(readOnly = true)
    public TriageCompaniesResponse listAllOfStage(UUID workspaceId, UUID projectId,
                                                  TriageCompanyStatus status,
                                                  TriageCompanyFilters filters, int cap) {
        projects.requireInWorkspace(projectId, workspaceId);
        PageRequest wholeStage = PageRequest.of(0, cap, Sort.by(Sort.Direction.ASC, "companyName")
                .and(NEWEST_FIRST));
        Page<TriageCompany> found = findWithFilters(projectId, status,
                TextUtils.blankToNull(filters.companyName()), TextUtils.blankToNull(filters.executiveName()),
                resolveExecutiveStatuses(filters.executiveStatuses()), wholeStage);
        return new TriageCompaniesResponse(
                found.getContent().stream().map(TriageCompanyService::toDto).toList(),
                found.getTotalElements(), 0, cap, countsFor(projectId));
    }

    private TriageCountsDto countsFor(UUID projectId) {
        return new TriageCountsDto(
                triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.IN_UNIVERSE),
                triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.SHORTLISTED),
                triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.DECLINED));
    }

    /**
     * Newest first unless the grid asked otherwise.
     *
     * <p>{@code NULLS LAST} regardless of direction: Apollo publishes a revenue figure on about one
     * row in ten and those blanks travel into the snapshot, so without it an ascending revenue sort
     * opens on the very rows the ordering means to bury.
     *
     * <p>The secondary sort on {@code createdAt} keeps paging stable — the snapshot columns are full
     * of ties, and Postgres is free to order tied rows differently per query.
     */
    private static Sort resolveSort(TriageCompanyListCriteria criteria) {
        // Both tokens are resolved before either is used, so a bad direction is a 400 whether or not
        // a field came with it. Returning the default early would have let ?direction=sideways
        // through with a 200.
        SortDirection direction = resolveDirection(criteria.direction());
        TriageCompanySortField field = resolveSortField(criteria.sort());
        if (field == null) {
            // No field named: the default ordering, which the caller may still have reversed.
            return newestFirstIn(direction == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC);
        }
        Sort.Order order = Sort.Order
                .by(field.property())
                .with(direction == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC)
                .nullsLast();
        // "Added" is createdAt itself, so tie-breaking on it again would be the same term twice.
        return field == TriageCompanySortField.ADDED ? Sort.by(order) : Sort.by(order).and(NEWEST_FIRST);
    }

    /** Null when the caller named no field, which is not the same as naming an unknown one. */
    private static TriageCompanySortField resolveSortField(String token) {
        return ApiValueEnum.parse(TriageCompanySortField.class, token, null, "sort field");
    }

    private static Sort newestFirstIn(Sort.Direction direction) {
        return direction == Sort.Direction.DESC ? NEWEST_FIRST : Sort.by(Sort.Direction.ASC, "createdAt");
    }

    /** Omitted means DESC: the grid opens newest-first, and an absent direction must not reverse it. */
    private static SortDirection resolveDirection(String token) {
        return ApiValueEnum.parse(SortDirection.class, token, SortDirection.DESC, "sort direction");
    }
}
