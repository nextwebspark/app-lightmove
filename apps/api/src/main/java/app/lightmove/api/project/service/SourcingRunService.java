package app.lightmove.api.project.service;

import app.lightmove.api.company.model.CoreSignalCompany;
import app.lightmove.api.company.service.CoreSignalCompanyCache;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.SourcingRunStatus;
import app.lightmove.api.project.dto.SourcingDtos.SourcedCompanyDto;
import app.lightmove.api.project.dto.SourcingDtos.SourcingRunDto;
import app.lightmove.api.project.dto.SourcingDtos.SourcingRunResponse;
import app.lightmove.api.project.model.SourcingRun;
import app.lightmove.api.project.model.Strategy;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.project.repository.SourcingRunRepository;
import app.lightmove.api.project.service.SourcingCriteriaResolver.ResolvedScope;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The CoreSignal sourcing flow behind the Sourcing screen's run endpoints (POC): start a run for
 * the project's stored strategy, poll its state, extend it a batch at a time. Scope is resolved
 * entirely server-side from the stored {@link Strategy} — never from client input — for the same
 * team-only reason {@link SourcingService} documents.
 *
 * <p>Same sanctioned feature→feature seam as {@code SourcingService → CompanyQueryService}: this
 * service and {@link SourcingRunExecutor} call {@code company}'s CoreSignal gateway and cache
 * through their public service methods only.
 *
 * <p>{@link #start} is deliberately NOT transactional: the run row must be committed before the
 * async executor is kicked, or the executor's own transaction may look for a row that does not
 * exist yet — a cousin of the {@code @Async} proxy trap. The save's repository-level transaction
 * is the commit point; the kick happens strictly after it returns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SourcingRunService {

    private final ProjectRepository projects;
    private final SourcingRunRepository runs;
    private final SourcingCriteriaResolver resolver;
    private final CoreSignalCompanyCache cache;
    private final SourcingRunExecutor executor;
    private final LightMoveProperties properties;

    /** Start (or reuse) the run for the current strategy. Reuse costs zero credits — the point. */
    public SourcingRunResponse start(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        ResolvedScope scope = resolveScope(projectId);
        String criteriaHash = resolver.hashOf(scope.toCriteria());

        SourcingRun run = runs.findByProjectId(projectId).orElse(null);
        if (run != null && criteriaHash.equals(run.getCriteriaHash())
                && run.getStatus() != SourcingRunStatus.FAILED) {
            return buildResponse(run, scope, criteriaHash);
        }

        try {
            if (run == null) {
                run = runs.save(SourcingRun.start(projectId, criteriaHash,
                        properties.coresignal().collectBatchSize()));
            } else {
                run.restartWith(criteriaHash, properties.coresignal().collectBatchSize());
                run = runs.save(run);
            }
        } catch (DataIntegrityViolationException ex) {
            // Two starts raced on the unique project_id; the other one's row is the run now.
            log.debug("Concurrent sourcing-run start for project {} — reusing the winner", projectId);
            run = runs.findByProjectId(projectId).orElseThrow(() -> ApiException.of(ErrorCode.CONFLICT));
            return buildResponse(run, scope, criteriaHash);
        }

        executor.execute(run.getId());
        return buildResponse(run, scope, criteriaHash);
    }

    /** The poll. Never 404s for "no run yet" — a null run tells the SPA to auto-start. */
    public SourcingRunResponse current(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        SourcingRun run = runs.findByProjectId(projectId).orElse(null);
        if (run == null) {
            return new SourcingRunResponse(null);
        }
        ResolvedScope scope = resolveScope(projectId);
        return buildResponse(run, scope, resolver.hashOf(scope.toCriteria()));
    }

    /** Pay for one more batch of an exhausted-batch run. A no-op unless the run is READY with more to give. */
    public SourcingRunResponse extend(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        SourcingRun run = runs.findByProjectId(projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        if (run.getStatus() == SourcingRunStatus.READY
                && run.getRequestedCount() < run.getSearchedIds().size()) {
            run.extendBy(properties.coresignal().collectBatchSize());
            run = runs.save(run);
            executor.execute(run.getId());
        }
        ResolvedScope scope = resolveScope(projectId);
        return buildResponse(run, scope, resolver.hashOf(scope.toCriteria()));
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private ResolvedScope resolveScope(UUID projectId) {
        return resolver.resolveForProject(projectId);
    }

    /**
     * Assembles the poll view: the cached rows among the requested ids, kept in the search's
     * revenue-desc order. Collected-count is cache membership, not a stored counter — the
     * executor's parallel writers never touch the run row mid-collect.
     */
    private SourcingRunResponse buildResponse(SourcingRun run, ResolvedScope scope, String currentHash) {
        // A PENDING run has not searched yet: requestedCount says what it will pay for, but the id
        // list is still empty — clamp rather than trust the count.
        List<Long> requested = run.getSearchedIds()
                .subList(0, Math.min(run.getRequestedCount(), run.getSearchedIds().size()));
        Map<Long, CoreSignalCompany> byId = cache.findAllByIds(requested).stream()
                .collect(Collectors.toMap(CoreSignalCompany::getCoresignalId, Function.identity()));

        Set<String> directSectors = lowered(scope.directSectors());
        Set<String> adjacentSectors = lowered(scope.adjacentSectors());
        List<SourcedCompanyDto> companies = requested.stream()
                .map(byId::get)
                .filter(company -> company != null)
                .map(company -> toDto(company, directSectors, adjacentSectors))
                .toList();

        return new SourcingRunResponse(new SourcingRunDto(
                run.getStatus().name(),
                run.getRequestedCount(),
                companies.size(),
                run.getSearchedIds().size(),
                run.getTotalMatched(),
                currentHash.equals(run.getCriteriaHash()),
                run.getErrorDetail(),
                companies));
    }

    /**
     * Which scope bucket this company matched through, judged by its collected industry label.
     * Exact (case-insensitive) label equality — a company whose CoreSignal industry uses different
     * vocabulary than our sector labels reads as AI-inferred, which is honest: the tag half of the
     * search anchor is what would have matched it.
     */
    private static SourcedCompanyDto toDto(CoreSignalCompany company, Set<String> directSectors,
                                           Set<String> adjacentSectors) {
        String industry = company.getIndustry() == null
                ? null
                : company.getIndustry().toLowerCase(Locale.ROOT);
        String matchTier = industry != null && directSectors.contains(industry) ? "DIRECT"
                : industry != null && adjacentSectors.contains(industry) ? "ADJACENT"
                : "INFERRED";

        return new SourcedCompanyDto(
                company.getCoresignalId(),
                company.getName(),
                company.getWebsite(),
                company.getLinkedinUrl(),
                company.getLogoUrl(),
                company.getIndustry(),
                company.getSizeRange(),
                company.getEmployeesCount(),
                company.getRevenueRange(),
                company.getRevenueAnnual() == null ? null : company.getRevenueAnnual().longValue(),
                company.getHqLocation(),
                company.getHqCountry(),
                company.getFoundedYear(),
                company.getDescription(),
                matchTier);
    }

    private static Set<String> lowered(List<String> labels) {
        return labels.stream().map(label -> label.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
