package app.lightmove.api.triagecompany.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.service.CustomColumnService;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.AddSelectedTriageCompaniesRequest;
import app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.CaptureCompanyRequest;
import app.lightmove.api.triagecompany.dto.EditTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.dto.UpdateTriageCompanyRequest;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.model.TriageCompany;
import app.lightmove.api.triagecompany.model.TriageCompanyCapturedEvent;
import app.lightmove.api.triagecompany.repository.TriageCompanyRepository;
import app.lightmove.api.triagecompany.repository.TriageCompanyWriter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A mandate's triaged companies: taken from the market, captured, moved between stages, removed. A
 * market company is resolved server-side, so a client cannot file one under a name of its choosing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TriageCompanyService {

    /** The doors a caller may supply a company through. {@code STRATEGY} is the server's to write. */
    private static final Set<TriageCompanySource> CAPTURABLE_SOURCES =
            Set.of(TriageCompanySource.MANUAL, TriageCompanySource.EXTENSION, TriageCompanySource.CSV,
                    TriageCompanySource.ASSISTANT);

    /** One company at a time, so resolving and researching each is affordable. */
    private static final Set<TriageCompanySource> SUPPLIED_ONE_AT_A_TIME =
            Set.of(TriageCompanySource.MANUAL, TriageCompanySource.EXTENSION);

    private final TriageCompanyRepository triaged;
    private final TriageCompanyWriter writer;
    private final ProjectRepository projects;
    private final StrategyService strategy;
    private final CustomColumnService customColumns;
    private final AuditService audit;
    private final ApolloCompanyQueryService market;
    private final ApplicationEventPublisher events;
    private final ProjectStreamPublisher stream;
    private final LightMoveProperties properties;

    /**
     * The seam {@code candidate} maps an executive through; scoped to the project, so a candidate
     * cannot be filed against another mandate's company by id. Only a {@code newMapping} clears
     * {@code noExecutiveFound} — an edit of someone already mapped must not revive a ruled-out company.
     */
    @Transactional
    public TriageCompanyResponse requireCompanyOfProject(UUID projectId, UUID triageCompanyId, boolean newMapping) {
        TriageCompany company = triaged.requireInProject(triageCompanyId, projectId);
        if (newMapping) {
            company.unflagNoExecutiveFound();
        }
        return TriageCompanyResponseMapper.toDto(company);
    }

    /** How an import resolves a company cell. Oldest wins: Apollo can publish two accounts under one name. */
    @Transactional(readOnly = true)
    public Optional<TriageCompanyResponse> findCompanyOfProjectByName(UUID projectId, String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        return triaged.findByProjectIdAndCompanyNameIgnoreCase(projectId, companyName.trim()).stream()
                .min(Comparator.comparing(TriageCompany::getCreatedAt))
                .map(TriageCompanyResponseMapper::toDto);
    }

    @Transactional
    public TriageCompanyResponse add(UUID userId, UUID workspaceId, UUID projectId,
                                     AddTriageCompanyRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        String accountId = request.apolloAccountId();
        // Before the held check, so an unknown stage is a 400 whether or not the company is held.
        TriageCompanyStatus landingStatus = TriageCompanyStatus.parseOrInUniverse(request.status());

        // Already held answers with the row untouched, so re-adding cannot un-decline a company.
        Optional<TriageCompany> held = triaged.findByProjectIdAndApolloAccountId(projectId, accountId);
        if (held.isPresent()) {
            return TriageCompanyResponseMapper.toDto(held.get());
        }

        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);
        if (scope.offLimitsAccountIds().contains(accountId)) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "This company is off-limits for this mandate.");
        }

        CompanyRow row = market.byAccountIds(List.of(accountId)).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Not in the universe: " + accountId));

        // The held check is a fast path, not the guard: a racing click passes it too. Only the caller
        // whose insert actually wrote the row records an event.
        int inserted = writer.insertIgnoringHeld(projectId, userId, List.of(row),
                TriageCompanySource.STRATEGY, landingStatus, request.note(), null);
        TriageCompany taken = triaged.findByProjectIdAndApolloAccountId(projectId, accountId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        if (inserted > 0) {
            audit.projectEvent(ProjectEventType.TRIAGE_COMPANY_ADDED, userId, workspaceId, projectId, httpRequest)
                    .detail("apolloAccountId", accountId)
                    .record();
        }
        return TriageCompanyResponseMapper.toDto(taken);
    }

    /**
     * A company the mandate supplies itself, typed or read by the plugin. Refused if the name is held
     * under <i>any</i> source (wider than V34's index). One-directional by decision: a later Strategy
     * bulk add may still land a second row under that name.
     */
    @Transactional
    public TriageCompanyResponse capture(UUID userId, UUID workspaceId, UUID projectId,
                                         CaptureCompanyRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);

        TriageCompanySource source = resolveCapturableSource(request.source());
        TriageCompanyStatus status = TriageCompanyStatus.parseOrInUniverse(request.status());
        CapturedCompanyDetails details = new CapturedCompanyDetails(
                request.companyName(), request.industry(), request.companyCountry(),
                request.companyCity(), request.numEmployees(), request.annualRevenue(),
                request.website(), request.companyLinkedinUrl(), request.foundedYear(),
                request.shortDescription(), null, request.sourceUrl(), request.note());

        if (triaged.existsByProjectIdAndCompanyNameIgnoreCase(projectId, details.companyName())) {
            throw ApiException.of(ErrorCode.TRIAGE_COMPANY_ALREADY_HELD);
        }

        ResolvedCapture resolved = SUPPLIED_ONE_AT_A_TIME.contains(source)
                ? resolveCapture(projectId, userId, details, source, status)
                : saveCaptured(projectId, userId, source, status, details);

        // Covers the name the row landed under (the market's, once resolved). Without it a capture of
        // "Al Rawabi" resolving to "Al Rawabi Dairy" duplicated a row the mandate already held.
        if (!resolved.created()) {
            throw ApiException.of(ErrorCode.TRIAGE_COMPANY_ALREADY_HELD);
        }
        TriageCompany captured = resolved.company();
        captured.describeCustomFields(customColumns.applyTo(
                projectId, CustomColumnTarget.COMPANY, captured.getCustomFields(), request.customFields()));

        auditCaptured(captured, source, userId, workspaceId, projectId, httpRequest);
        if (SUPPLIED_ONE_AT_A_TIME.contains(source)) {
            announceForResearch(captured, projectId);
        }
        stream.publish(projectId, ProjectStreamKind.COMPANY_CAPTURED);
        return TriageCompanyResponseMapper.toDto(captured);
    }

    /**
     * Files a researched executive's employer. A held name answers with the existing row, no audit of
     * its own, and clears {@code noExecutiveFound}: the row is about to be mapped to that executive.
     */
    @Transactional
    public TriageCompanyResponse captureFromResearch(UUID projectId, UUID addedBy,
                                                     CapturedCompanyDetails details) {
        ResolvedCapture resolved = resolveCapture(projectId, addedBy, details,
                TriageCompanySource.EXTENSION, TriageCompanyStatus.IN_UNIVERSE);
        resolved.company().unflagNoExecutiveFound();
        if (resolved.created()) {
            announceForResearch(resolved.company(), projectId);
        }
        return TriageCompanyResponseMapper.toDto(resolved.company());
    }

    /** Files the assistant's researched company card. False when the mandate already held it. */
    @Transactional
    public boolean captureResearched(UUID userId, UUID workspaceId, UUID projectId,
                                     CapturedCompanyDetails details, TriageCompanySource source,
                                     String status, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        ResolvedCapture resolved = resolveCapture(
                projectId, userId, details, source, TriageCompanyStatus.parseOrInUniverse(status));
        if (!resolved.created()) {
            return false;
        }
        TriageCompany captured = resolved.company();
        auditCaptured(captured, source, userId, workspaceId, projectId, httpRequest);
        stream.publish(projectId, ProjectStreamKind.COMPANY_CAPTURED);
        return true;
    }

    private void auditCaptured(TriageCompany captured, TriageCompanySource source, UUID userId, UUID workspaceId,
                               UUID projectId, HttpServletRequest httpRequest) {
        audit.projectEvent(ProjectEventType.TRIAGE_COMPANY_CAPTURED, userId, workspaceId, projectId, httpRequest)
                .detail("source", source.name())
                .detail("triageCompanyId", captured.getId().toString())
                .detailIfPresent("apolloAccountId", captured.getApolloAccountId())
                .record();
    }

    /**
     * {@code REQUIRES_NEW}: called from an {@code AFTER_COMMIT} callback, where joining the completed
     * transaction still bound to the thread writes nothing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyEnrichment(UUID projectId, UUID companyId, CapturedCompanyDetails details) {
        triaged.findByIdAndProjectId(companyId, projectId).ifPresentOrElse(company -> {
            // Unreachable through today's only producer; kept as the guard a second one would need.
            if (company.getApolloAccountId() != null) {
                return;
            }
            company.enrichFacts(details);
            stream.publish(projectId, ProjectStreamKind.COMPANY_ENRICHED);
        }, () -> log.info("Company {} was removed before its research landed", companyId));
    }

    private record ResolvedCapture(TriageCompany company, boolean created) {}

    /**
     * The market snapshot when the universe carries it, a hand-shaped row otherwise. Deliberately no
     * off-limits check, unlike {@link #add}: a captured person works where they work.
     */
    private ResolvedCapture resolveCapture(UUID projectId, UUID addedBy,
                                           CapturedCompanyDetails details,
                                           TriageCompanySource source, TriageCompanyStatus status) {
        Optional<CompanyRow> matched = market.matchEmployer(
                LinkedInUrls.companySlugOrNull(details.companyLinkedinUrl()), details.companyName());

        if (matched.isPresent()) {
            CompanyRow row = matched.get();
            Optional<TriageCompany> held = heldMarketRow(projectId, row);
            if (held.isPresent()) {
                return new ResolvedCapture(held.get(), false);
            }
            // The count, not a hard-coded true: the race loser inserts zero rows. Reporting true to
            // both fired the audit and stream twice and sent a second billed company lookup.
            int inserted = writer.insertIgnoringHeld(projectId, addedBy, List.of(row), source, status,
                    details.note(), details.sourceUrl());
            return new ResolvedCapture(
                    triaged.findByProjectIdAndApolloAccountId(projectId, row.apolloAccountId())
                            .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND)), inserted > 0);
        }

        List<TriageCompany> heldByName =
                triaged.findByProjectIdAndCompanyNameIgnoreCase(projectId, details.companyName());
        if (!heldByName.isEmpty()) {
            return new ResolvedCapture(preferred(heldByName), false);
        }
        return saveCaptured(projectId, addedBy, source, status, details);
    }

    private Optional<TriageCompany> heldMarketRow(UUID projectId, CompanyRow row) {
        Optional<TriageCompany> byId =
                triaged.findByProjectIdAndApolloAccountId(projectId, row.apolloAccountId());
        if (byId.isPresent()) {
            return byId;
        }
        List<TriageCompany> byName =
                triaged.findByProjectIdAndCompanyNameIgnoreCase(projectId, row.companyName());
        return byName.isEmpty() ? Optional.empty() : Optional.of(preferred(byName));
    }

    /**
     * Ordered on purpose: an unordered {@code getFirst} mapped people to whichever same-named row
     * Postgres returned, including a declined one where nobody would look for them.
     */
    private static TriageCompany preferred(List<TriageCompany> rows) {
        return rows.stream()
                .min(Comparator
                        .comparing((TriageCompany row) -> row.getStatus() == TriageCompanyStatus.DECLINED)
                        .thenComparing(row -> row.getApolloAccountId() == null)
                        .thenComparing(TriageCompany::getCreatedAt))
                .orElseThrow();
    }

    /**
     * Answers with the winner's row when a concurrent capture got there first: two enrichment workers
     * both pass the held-check, and V34's index refusing the loser would fail its whole enrichment.
     */
    private ResolvedCapture saveCaptured(UUID projectId, UUID addedBy, TriageCompanySource source,
                                         TriageCompanyStatus status, CapturedCompanyDetails details) {
        try {
            return new ResolvedCapture(triaged.saveAndFlush(
                    TriageCompany.captured(projectId, addedBy, source, status, details)), true);
        } catch (DataIntegrityViolationException lostTheRace) {
            List<TriageCompany> held =
                    triaged.findByProjectIdAndCompanyNameIgnoreCase(projectId, details.companyName());
            if (held.isEmpty()) {
                throw lostTheRace;
            }
            return new ResolvedCapture(preferred(held), false);
        }
    }

    private void announceForResearch(TriageCompany company, UUID projectId) {
        String slug = LinkedInUrls.companySlugOrNull(company.getCompanyLinkedinUrl());
        if (company.getApolloAccountId() == null && slug != null) {
            events.publishEvent(new TriageCompanyCapturedEvent(company.getId(), projectId, slug));
        }
    }

    /** "Add all to Universe". Over {@code bulkAddLimit} is refused whole, never silently truncated. */
    @Transactional
    public TriageBulkAddResponse addAllInScope(UUID userId, UUID workspaceId, UUID projectId,
                                               HttpServletRequest httpRequest) {
        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);
        int limit = properties.company().list().bulkAddLimit();
        long matching = market.count(scope);
        if (matching > limit) {
            // Safe to interpolate into a user-facing message: neither number came from the caller.
            throw ApiException.userFacing(ErrorCode.BULK_ADD_SCOPE_TOO_LARGE,
                    "%,d companies match this filter. You can add %,d at a time — narrow it and try again."
                            .formatted(matching, limit));
        }

        List<CompanyRow> rows = market.search(scope, CompanySortField.EMPLOYEES, SortDirection.DESC,
                0, limit);

        // The insert skips held companies, so the count is the new ones and a declined row stays declined.
        int added = writer.insertIgnoringHeld(projectId, userId, rows,
                TriageCompanySource.STRATEGY, TriageCompanyStatus.IN_UNIVERSE, null, null);

        audit.projectEvent(ProjectEventType.TRIAGE_BULK_ADDED, userId, workspaceId, projectId, httpRequest)
                .detail("added", String.valueOf(added))
                .record();
        return new TriageBulkAddResponse(added, rows.size() - added);
    }

    /**
     * Strategy's ticked companies at one stage. Every id is resolved and off-limits-checked
     * server-side; an off-limits one is dropped rather than refusing the batch.
     */
    @Transactional
    public TriageBulkAddResponse addSelected(UUID userId, UUID workspaceId, UUID projectId,
                                             AddSelectedTriageCompaniesRequest request,
                                             HttpServletRequest httpRequest) {
        return addSelected(userId, workspaceId, projectId, request, TriageCompanySource.STRATEGY,
                httpRequest);
    }

    /** The same write, badged with its door; overloaded so Strategy says {@code STRATEGY} by construction. */
    @Transactional
    public TriageBulkAddResponse addSelected(UUID userId, UUID workspaceId, UUID projectId,
                                             AddSelectedTriageCompaniesRequest request,
                                             TriageCompanySource source,
                                             HttpServletRequest httpRequest) {
        TriageCompanyStatus landingStatus = TriageCompanyStatus.parseOrInUniverse(request.status());
        // Distinct: ON CONFLICT DO NOTHING cannot deduplicate two rows inside one statement.
        List<String> accountIds = request.apolloAccountIds().stream().distinct().toList();

        int limit = properties.company().list().bulkAddLimit();
        if (accountIds.size() > limit) {
            throw ApiException.userFacing(ErrorCode.BULK_ADD_SCOPE_TOO_LARGE,
                    "You selected %,d companies. You can add %,d at a time."
                            .formatted(accountIds.size(), limit));
        }

        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);
        List<CompanyRow> rows = market.byAccountIds(accountIds).stream()
                .filter(row -> !scope.offLimitsAccountIds().contains(row.apolloAccountId()))
                .toList();

        int added = writer.insertIgnoringHeld(projectId, userId, rows, source, landingStatus,
                null, null);

        audit.projectEvent(ProjectEventType.TRIAGE_BULK_ADDED, userId, workspaceId, projectId, httpRequest)
                .detail("added", String.valueOf(added))
                .detail("status", landingStatus.name())
                .detail("source", source.name())
                .record();
        return new TriageBulkAddResponse(added, accountIds.size() - added);
    }

    @Transactional
    public TriageCompanyResponse update(UUID userId, UUID workspaceId, UUID projectId,
                                        UUID triageCompanyId, UpdateTriageCompanyRequest request,
                                        HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        TriageCompany company = triaged.requireInProject(triageCompanyId, projectId);

        // Null leaves a field alone, so declining keeps the note saying why; "" clears it.
        if (request.status() != null) {
            TriageCompanyStatus status = TriageCompanyStatus.fromValue(request.status());
            if (status == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown status: " + request.status());
            }
            company.moveTo(status);
        }
        if (request.note() != null) {
            company.annotate(request.note());
        }
        if (request.noExecutiveFound() != null) {
            if (request.noExecutiveFound()) {
                company.flagNoExecutiveFound();
            } else {
                company.unflagNoExecutiveFound();
            }
        }

        // One event type for any edit; the details show whether the stage actually moved.
        audit.projectEvent(ProjectEventType.TRIAGE_COMPANY_MOVED, userId, workspaceId, projectId, httpRequest)
                .detail("triageCompanyId", triageCompanyId.toString())
                .detailIfPresent("status", request.status())
                .detailIfPresent("noExecutiveFound", Objects.toString(request.noExecutiveFound(), null))
                .record();
        return TriageCompanyResponseMapper.toDto(company);
    }

    /**
     * Replaces the facts of a mandate-supplied company. The rule lives here, not in the hidden button:
     * the plugin posts here directly.
     */
    @Transactional
    public TriageCompanyResponse edit(UUID userId, UUID workspaceId, UUID projectId,
                                      UUID triageCompanyId, EditTriageCompanyRequest request,
                                      HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        TriageCompany company = triaged.requireInProject(triageCompanyId, projectId);

        if (!company.isMandateSupplied()) {
            throw ApiException.of(ErrorCode.TRIAGE_COMPANY_NOT_EDITABLE);
        }

        CapturedCompanyDetails details = new CapturedCompanyDetails(
                request.companyName(), request.industry(), request.companyCountry(),
                request.companyCity(), request.numEmployees(), request.annualRevenue(),
                request.website(), request.companyLinkedinUrl(), request.foundedYear(),
                request.shortDescription(), null, null, null);

        boolean nameTaken = triaged
                .findByProjectIdAndCompanyNameIgnoreCase(projectId, details.companyName())
                .stream()
                .anyMatch(other -> !other.getId().equals(triageCompanyId));
        if (nameTaken) {
            throw ApiException.of(ErrorCode.TRIAGE_COMPANY_ALREADY_HELD);
        }

        company.describe(details);
        company.describeCustomFields(customColumns.applyTo(
                projectId, CustomColumnTarget.COMPANY, company.getCustomFields(), request.customFields()));

        audit.projectEvent(ProjectEventType.TRIAGE_COMPANY_EDITED, userId, workspaceId, projectId, httpRequest)
                .detail("triageCompanyId", triageCompanyId.toString())
                .record();
        return TriageCompanyResponseMapper.toDto(company);
    }

    /** Custom-column values only — the one edit a market company, which {@link #edit} refuses, allows. */
    @Transactional
    public TriageCompanyResponse editCustomFields(UUID userId, UUID workspaceId, UUID projectId,
                                                  UUID triageCompanyId, Map<String, String> customFields,
                                                  HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        TriageCompany company = triaged.requireInProject(triageCompanyId, projectId);

        company.describeCustomFields(customColumns.applyTo(
                projectId, CustomColumnTarget.COMPANY, company.getCustomFields(), customFields));

        audit.projectEvent(ProjectEventType.TRIAGE_COMPANY_EDITED, userId, workspaceId, projectId, httpRequest)
                .detail("triageCompanyId", triageCompanyId.toString())
                .detail("customFieldsOnly", "true")
                .record();
        return TriageCompanyResponseMapper.toDto(company);
    }

    /**
     * Drops the mandate's decision only; the universe row is ETL-owned and untouched. Unlike Declining
     * it is not remembered — a later "Add all" may take the company back in.
     */
    @Transactional
    public void removeFromProject(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                                  HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        TriageCompany company = triaged.requireInProject(triageCompanyId, projectId);

        triaged.delete(company);

        // The name, because the row carrying it is gone and a bare id answers nothing later.
        audit.projectEvent(ProjectEventType.TRIAGE_COMPANY_REMOVED, userId, workspaceId, projectId, httpRequest)
                .detail("triageCompanyId", triageCompanyId.toString())
                .detail("companyName", company.getCompanyName())
                .record();
    }

    /** Defaults to manual; refuses {@code strategy}, which only {@link #add} may write from the market. */
    private static TriageCompanySource resolveCapturableSource(String token) {
        TriageCompanySource source = ApiValueEnum.parse(
                TriageCompanySource.class, token, TriageCompanySource.MANUAL, "capture source");
        if (!CAPTURABLE_SOURCES.contains(source)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown capture source: " + token);
        }
        return source;
    }
}
