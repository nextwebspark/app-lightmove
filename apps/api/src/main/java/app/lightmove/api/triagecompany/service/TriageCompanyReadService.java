package app.lightmove.api.triagecompany.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.SuppliedText;
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

/** Reads a mandate's triaged companies a stage at a time: the grid's filtered page, or the whole stage. */
@Service
@RequiredArgsConstructor
public class TriageCompanyReadService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    /** Deliberately outside {@link TriageCompanySortField}: that ranking lives in {@code candidate}. */
    private static final String EXECUTIVE_STATUS_SORT_TOKEN = "executiveStatus";

    /**
     * Wire token to stored {@code CandidateStatus} name, duplicated because {@code triagecompany} must
     * not depend on {@code candidate}. Mirrored in {@code CandidateRepository}'s ranking {@code CASE}
     * and {@code MappedExecutiveLookupAdapter} — a rename must grep for all three.
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

        String companyName = SuppliedText.blankToNull(criteria.nameQuery());
        String executiveName = SuppliedText.blankToNull(criteria.executiveQuery());
        List<String> executiveStatuses = resolveExecutiveStatuses(criteria.executiveStatuses());
        Page<TriageCompany> found = EXECUTIVE_STATUS_SORT_TOKEN.equals(criteria.sort())
                ? findOrderedByExecutiveStatus(projectId, status, companyName, executiveName,
                        executiveStatuses, resolveDirection(criteria.direction()), PageRequest.of(page, size))
                : findWithFilters(projectId, status, companyName, executiveName, executiveStatuses,
                        PageRequest.of(page, size, resolveSort(criteria)));

        return new TriageCompaniesResponse(
                found.getContent().stream().map(TriageCompanyResponseMapper::toDto).toList(),
                found.getTotalElements(), page, size, countsFor(projectId));
    }

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

    /** Both filters are independent: each resolves to a company set and the two are intersected. */
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

    /** Ranked ids come from {@code candidate}'s side of the boundary; the rows are re-fetched here. */
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
        // A ranked id can vanish between the two reads; dropped, not a null `toDto` would 500 on.
        List<TriageCompany> ordered = ranked.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return new PageImpl<>(ordered, pageRequest, ranked.getTotalElements());
    }

    /** An unknown token is a 400; an empty result means "no filter", not "match nothing". */
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
     * One whole stage, unpaged, for {@code talentmap} and {@code dataexport}. Answers the total so a
     * caller past {@code cap} can tell; name order keeps the cut deterministic.
     */
    @Transactional(readOnly = true)
    public TriageCompaniesResponse listAllOfStage(UUID workspaceId, UUID projectId,
                                                  TriageCompanyStatus status,
                                                  TriageCompanyFilters filters, int cap) {
        projects.requireInWorkspace(projectId, workspaceId);
        PageRequest wholeStage = PageRequest.of(0, cap, Sort.by(Sort.Direction.ASC, "companyName")
                .and(NEWEST_FIRST));
        Page<TriageCompany> found = findWithFilters(projectId, status,
                SuppliedText.blankToNull(filters.companyName()), SuppliedText.blankToNull(filters.executiveName()),
                resolveExecutiveStatuses(filters.executiveStatuses()), wholeStage);
        return new TriageCompaniesResponse(
                found.getContent().stream().map(TriageCompanyResponseMapper::toDto).toList(),
                found.getTotalElements(), 0, cap, countsFor(projectId));
    }

    private TriageCountsDto countsFor(UUID projectId) {
        return new TriageCountsDto(
                triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.IN_UNIVERSE),
                triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.SHORTLISTED),
                triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.DECLINED));
    }

    /**
     * {@code NULLS LAST} either way: Apollo leaves revenue blank on ~9 rows in 10, which an ascending
     * sort would open on. The {@code createdAt} tie-break keeps paging stable.
     */
    private static Sort resolveSort(TriageCompanyListCriteria criteria) {
        // Both resolved up front: returning the default early let ?direction=sideways through with a 200.
        SortDirection direction = resolveDirection(criteria.direction());
        TriageCompanySortField field = resolveSortField(criteria.sort());
        if (field == null) {
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
