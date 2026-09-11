package app.lightmove.api.triagecompany.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CompanyListSettings;
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
import app.lightmove.api.triagecompany.constant.TriageCompanySortField;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.AddSelectedTriageCompaniesRequest;
import app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.CaptureCompanyRequest;
import app.lightmove.api.triagecompany.dto.EditTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyListCriteria;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.dto.TriageCountsDto;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A mandate's triaged companies: taking one out of the market, capturing one the market does not
 * carry, moving it between stages, and removing it. A company taken from Strategy is resolved from
 * the market server-side, so a client cannot file one under a name of its own choosing.
 */
@Service
@Slf4j
public class TriageCompanyService {

    /** The doors a caller may supply a company through. {@code STRATEGY} is the server's to write. */
    private static final Set<TriageCompanySource> CAPTURABLE_SOURCES =
            Set.of(TriageCompanySource.MANUAL, TriageCompanySource.EXTENSION, TriageCompanySource.CSV);

    /** The doors that come one company at a time, so resolving and researching each is affordable. */
    private static final Set<TriageCompanySource> SUPPLIED_ONE_AT_A_TIME =
            Set.of(TriageCompanySource.MANUAL, TriageCompanySource.EXTENSION);

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final TriageCompanyRepository triaged;
    private final TriageCompanyWriter writer;
    private final ProjectRepository projects;
    private final StrategyService strategy;
    private final CustomColumnService customColumns;
    private final AuditService audit;
    private final ApolloCompanyQueryService market;
    private final ApplicationEventPublisher events;
    private final ProjectStreamPublisher stream;
    private final CompanyListSettings listConfig;

    public TriageCompanyService(TriageCompanyRepository triaged, TriageCompanyWriter writer,
                                ProjectRepository projects, StrategyService strategy,
                                CustomColumnService customColumns, AuditService audit,
                                ApolloCompanyQueryService market, ApplicationEventPublisher events,
                                ProjectStreamPublisher stream, LightMoveProperties properties) {
        this.triaged = triaged;
        this.writer = writer;
        this.projects = projects;
        this.strategy = strategy;
        this.customColumns = customColumns;
        this.audit = audit;
        this.market = market;
        this.events = events;
        this.stream = stream;
        this.listConfig = properties.company().list();
    }

    /** One stage, with all three counts: the stage switcher is always visible, so a badge cannot lag. */
    @Transactional(readOnly = true)
    public TriageCompaniesResponse list(UUID workspaceId, UUID projectId,
                                        TriageCompanyListCriteria criteria) {
        int page = criteria.page() == null ? 0 : criteria.page();
        int size = criteria.size() == null ? listConfig.defaultPageSize() : criteria.size();
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must not be negative");
        }
        if (size < 1 || size > listConfig.maxPageSize()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "size must be between 1 and " + listConfig.maxPageSize());
        }
        TriageCompanyStatus status = resolveStatus(criteria.status());
        requireProject(projectId, workspaceId);

        PageRequest pageRequest = PageRequest.of(page, size, resolveSort(criteria));
        String nameQuery = criteria.nameQuery() == null ? "" : criteria.nameQuery().trim();
        Page<TriageCompany> found = nameQuery.isEmpty()
                ? triaged.findByProjectIdAndStatus(projectId, status, pageRequest)
                : triaged.findByProjectIdAndStatusAndCompanyNameContainingIgnoreCase(
                        projectId, status, nameQuery, pageRequest);

        return new TriageCompaniesResponse(
                found.getContent().stream().map(TriageCompanyService::toDto).toList(),
                found.getTotalElements(), page, size, countsFor(projectId));
    }

    /**
     * The whole of one stage, unpaged — the seam {@code talentmap} reads companies through, because a
     * globe with a page two is no globe. Takes a cap the caller states and answers the total, so a
     * mandate past it is told rather than shown a map that looks complete.
     *
     * <p>Name order, not newest first: a stable order keeps the cut at the cap deterministic.
     */
    @Transactional(readOnly = true)
    public TriageCompaniesResponse listAllOfStage(UUID workspaceId, UUID projectId,
                                                  TriageCompanyStatus status, int cap) {
        requireProject(projectId, workspaceId);
        PageRequest wholeStage = PageRequest.of(0, cap, Sort.by(Sort.Direction.ASC, "companyName")
                .and(NEWEST_FIRST));
        Page<TriageCompany> found = triaged.findByProjectIdAndStatus(projectId, status, wholeStage);
        return new TriageCompaniesResponse(
                found.getContent().stream().map(TriageCompanyService::toDto).toList(),
                found.getTotalElements(), 0, cap, countsFor(projectId));
    }

    /**
     * One of this mandate's own company rows — the seam {@code candidate} maps an executive through.
     * It adds that the company belongs to <i>that</i> project, so a candidate cannot be filed against
     * another mandate's company by id.
     */
    @Transactional(readOnly = true)
    public TriageCompanyResponse requireCompanyOfProject(UUID projectId, UUID triageCompanyId) {
        return triaged.findByIdAndProjectId(triageCompanyId, projectId)
                .map(TriageCompanyService::toDto)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /**
     * The mandate's company of that name — how an import resolves a company cell. Oldest first when a
     * mandate holds two: nothing stops Apollo publishing two accounts under one name, so this has to
     * answer deterministically rather than throw.
     */
    @Transactional(readOnly = true)
    public Optional<TriageCompanyResponse> findCompanyOfProjectByName(UUID projectId, String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        return triaged.findByProjectIdAndCompanyNameIgnoreCase(projectId, companyName.trim()).stream()
                .min(Comparator.comparing(TriageCompany::getCreatedAt))
                .map(TriageCompanyService::toDto);
    }

    @Transactional
    public TriageCompanyResponse add(UUID userId, UUID workspaceId, UUID projectId,
                                     AddTriageCompanyRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        String accountId = request.apolloAccountId();
        // Resolved before the held check, so an unknown stage is a 400 whether or not the mandate
        // already holds the company — the same reason resolveSort settles both its tokens up front.
        TriageCompanyStatus landingStatus = resolveStatus(request.status());

        // Already held is not an error: a second click means the same thing as the first. Returning
        // the existing row leaves its stage and note untouched, so re-adding cannot walk a declined
        // company back into the universe.
        Optional<TriageCompany> held = triaged.findByProjectIdAndApolloAccountId(projectId, accountId);
        if (held.isPresent()) {
            return toDto(held.get());
        }

        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);
        if (scope.offLimitsAccountIds().contains(accountId)) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "This company is off-limits for this mandate.");
        }

        CompanyRow row = market.byAccountIds(List.of(accountId)).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Not in the universe: " + accountId));

        // The check above is a fast path, not the guard: a second click racing this one passes it too.
        // The insert ignores the conflict, so only the caller that actually wrote it records an event.
        int inserted = writer.insertIgnoringHeld(projectId, userId, List.of(row),
                TriageCompanySource.STRATEGY, landingStatus, request.note(), null);
        TriageCompany taken = triaged.findByProjectIdAndApolloAccountId(projectId, accountId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        if (inserted > 0) {
            audit.event(ProjectEventType.TRIAGE_COMPANY_ADDED)
                    .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                    .detail("apolloAccountId", accountId)
                    .record();
        }
        return toDto(taken);
    }

    /**
     * A company the mandate supplies itself — typed in on the Companies screen, or read off a live
     * page by the plugin.
     *
     * <p>Refused if the mandate already holds that name under <i>any</i> source. That is wider than
     * the partial unique index V34 adds, which can only see the manual rows.
     *
     * <p><b>The guard is one-directional by decision:</b> a later bulk add from Strategy can still
     * land a second row under a name a capture holds. The two are distinguishable by their Source
     * badge, and either can be removed.
     */
    @Transactional
    public TriageCompanyResponse capture(UUID userId, UUID workspaceId, UUID projectId,
                                         CaptureCompanyRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);

        TriageCompanySource source = resolveCapturableSource(request.source());
        TriageCompanyStatus status = resolveStatus(request.status());
        CapturedCompanyDetails details = new CapturedCompanyDetails(
                request.companyName(), request.industry(), request.companyCountry(),
                request.companyCity(), request.numEmployees(), request.annualRevenue(),
                request.website(), request.companyLinkedinUrl(), request.foundedYear(),
                request.shortDescription(), null, request.sourceUrl(), request.note());

        if (triaged.existsByProjectIdAndCompanyNameIgnoreCase(projectId, details.companyName())) {
            throw ApiException.of(ErrorCode.TRIAGE_COMPANY_ALREADY_HELD);
        }

        // A captured company the universe already carries lands as the full market row instead of a
        // thin hand-typed one — the plugin read a name and a slug, the market knows the rest.
        ResolvedCapture resolved = SUPPLIED_ONE_AT_A_TIME.contains(source)
                ? resolveCapture(projectId, userId, details, source, status)
                : saveCaptured(projectId, userId, source, status, details);

        // The check above covers the name the caller typed; this covers the name the row actually
        // landed under, which for a market-resolved capture is the market's. Without it a capture of
        // "Al Rawabi" resolving to "Al Rawabi Dairy" duplicated a row the mandate already held —
        // the very thing the guard exists to refuse.
        if (!resolved.created()) {
            throw ApiException.of(ErrorCode.TRIAGE_COMPANY_ALREADY_HELD);
        }
        TriageCompany captured = resolved.company();
        captured.describeCustomFields(customColumns.applyTo(
                projectId, CustomColumnTarget.COMPANY, captured.getCustomFields(), request.customFields()));

        var event = audit.event(ProjectEventType.TRIAGE_COMPANY_CAPTURED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("source", source.name())
                .detail("triageCompanyId", captured.getId().toString());
        if (captured.getApolloAccountId() != null) {
            event = event.detail("apolloAccountId", captured.getApolloAccountId());
        }
        event.record();
        if (SUPPLIED_ONE_AT_A_TIME.contains(source)) {
            announceForResearch(captured, projectId);
        }
        stream.publish(projectId, ProjectStreamKind.COMPANY_CAPTURED);
        return toDto(captured);
    }

    /**
     * The research door: the enrichment worker files a captured executive's employer into the
     * universe. Unlike {@link #capture}, a name already held answers with the existing row rather
     * than a refusal. No audit event of its own — this row is a consequence of the capture.
     */
    @Transactional
    public TriageCompanyResponse captureFromResearch(UUID projectId, UUID addedBy,
                                                     CapturedCompanyDetails details) {
        ResolvedCapture resolved = resolveCapture(projectId, addedBy, details,
                TriageCompanySource.EXTENSION, TriageCompanyStatus.IN_UNIVERSE);
        if (resolved.created()) {
            announceForResearch(resolved.company(), projectId);
        }
        return toDto(resolved.company());
    }

    /**
     * The short transactional tail of a company enrichment.
     *
     * <p>{@code REQUIRES_NEW} because the enrichment worker calls this from an {@code AFTER_COMMIT}
     * callback, where the completed transaction's resources are still bound to the thread and joining
     * them writes nothing.
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
     * Where a captured company lands: the market snapshot when the universe carries it, a hand-shaped
     * row when it does not. Deliberately no off-limits check, unlike {@link #add} — a person the
     * consultant captured works where they work.
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
            // The count, not a hard-coded true: the insert is ON CONFLICT DO NOTHING, so the loser
            // of a race gets zero rows. Reporting true to both fired the audit event and the stream
            // broadcast twice, and sent a second billed company lookup after a row that was already
            // being researched.
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

    /** The mandate's existing row for a market company — by its apollo id, or by the name it lands under. */
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
     * Which of several same-named rows a capture answers with. The mandate can legitimately hold two,
     * and an unordered {@code getFirst} mapped people to whichever row Postgres happened to return —
     * including a declined one, where a freshly researched executive would never be looked for.
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
     * Saves a hand-shaped row, answering with the winner's when a concurrent capture of the same
     * employer got there first. Two enrichment workers researching colleagues at one company both
     * pass the held-check before either commits, and V34's partial unique index refuses the loser —
     * whose transaction is the candidate's whole enrichment.
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

    /**
     * A row the market could not resolve still has a LinkedIn page — announce it so the company
     * enrichment worker can research what the plugin could not read.
     */
    private void announceForResearch(TriageCompany company, UUID projectId) {
        String slug = LinkedInUrls.companySlugOrNull(company.getCompanyLinkedinUrl());
        if (company.getApolloAccountId() == null && slug != null) {
            events.publishEvent(new TriageCompanyCapturedEvent(company.getId(), projectId, slug));
        }
    }

    /**
     * "Add all to Universe". A filter matching more than {@code bulkAddLimit} is <b>refused whole</b>:
     * taking the first {@code bulkAddLimit} would silently decide which companies a mandate got.
     */
    @Transactional
    public TriageBulkAddResponse addAllInScope(UUID userId, UUID workspaceId, UUID projectId,
                                               HttpServletRequest httpRequest) {
        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);
        int limit = listConfig.bulkAddLimit();
        long matching = market.count(scope);
        if (matching > limit) {
            // Interpolated into a user-facing message, which is otherwise reserved for literals.
            // Neither number came from the caller: the scope is the mandate's stored filter — this
            // endpoint takes no body — and the limit is configuration.
            throw ApiException.userFacing(ErrorCode.BULK_ADD_SCOPE_TOO_LARGE,
                    "%,d companies match this filter. You can add %,d at a time — narrow it and try again."
                            .formatted(matching, limit));
        }

        List<CompanyRow> rows = market.search(scope, CompanySortField.EMPLOYEES, SortDirection.DESC,
                0, limit);

        // No read-then-filter: the insert ignores companies the mandate already holds, so the count
        // is the number that were new, and a declined row stays declined.
        int added = writer.insertIgnoringHeld(projectId, userId, rows,
                TriageCompanySource.STRATEGY, TriageCompanyStatus.IN_UNIVERSE, null, null);

        audit.event(ProjectEventType.TRIAGE_BULK_ADDED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("added", String.valueOf(added))
                .record();
        return new TriageBulkAddResponse(added, rows.size() - added);
    }

    /**
     * The companies a consultant ticked on Strategy, taken in at one stage. Every id is resolved and
     * off-limits-checked server-side, so a hand-crafted request buys nothing a click could not; an
     * off-limits company is dropped rather than refusing the batch.
     */
    @Transactional
    public TriageBulkAddResponse addSelected(UUID userId, UUID workspaceId, UUID projectId,
                                             AddSelectedTriageCompaniesRequest request,
                                             HttpServletRequest httpRequest) {
        TriageCompanyStatus landingStatus = resolveStatus(request.status());
        // Distinct and ordered: a duplicate id in the request would bind two placeholder sets for one
        // company, and ON CONFLICT DO NOTHING cannot deduplicate rows inside the statement writing them.
        List<String> accountIds = request.apolloAccountIds().stream().distinct().toList();

        int limit = listConfig.bulkAddLimit();
        if (accountIds.size() > limit) {
            throw ApiException.userFacing(ErrorCode.BULK_ADD_SCOPE_TOO_LARGE,
                    "You selected %,d companies. You can add %,d at a time."
                            .formatted(accountIds.size(), limit));
        }

        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);
        List<CompanyRow> rows = market.byAccountIds(accountIds).stream()
                .filter(row -> !scope.offLimitsAccountIds().contains(row.apolloAccountId()))
                .toList();

        int added = writer.insertIgnoringHeld(projectId, userId, rows,
                TriageCompanySource.STRATEGY, landingStatus, null, null);

        audit.event(ProjectEventType.TRIAGE_BULK_ADDED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("added", String.valueOf(added))
                .detail("status", landingStatus.name())
                .record();
        return new TriageBulkAddResponse(added, accountIds.size() - added);
    }

    @Transactional
    public TriageCompanyResponse update(UUID userId, UUID workspaceId, UUID projectId,
                                        UUID triageCompanyId, UpdateTriageCompanyRequest request,
                                        HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        TriageCompany company = triaged.findByIdAndProjectId(triageCompanyId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        // Null leaves that half alone: moving a company to Declined must not clear the note saying
        // why, so clearing one is an explicit empty string rather than an omission.
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

        audit.event(ProjectEventType.TRIAGE_COMPANY_MOVED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("triageCompanyId", triageCompanyId.toString())
                .record();
        return toDto(company);
    }

    /**
     * Replaces a company's own facts. Only for a company the mandate supplied itself.
     *
     * <p><b>The rule lives here, not in the button.</b> The Companies panel hides Edit on a market
     * row, but the plugin posts here directly and a hidden button is not an access control.
     *
     * <p>A rename re-runs the capture guard, excluding the row being renamed.
     */
    @Transactional
    public TriageCompanyResponse edit(UUID userId, UUID workspaceId, UUID projectId,
                                      UUID triageCompanyId, EditTriageCompanyRequest request,
                                      HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        TriageCompany company = triaged.findByIdAndProjectId(triageCompanyId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

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

        audit.event(ProjectEventType.TRIAGE_COMPANY_EDITED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("triageCompanyId", triageCompanyId.toString())
                .record();
        return toDto(company);
    }

    /**
     * Writes only the mandate's custom-column values onto a company, leaving its own facts alone.
     *
     * <p>Exists for the case {@link #edit} refuses: a company taken out of the Apollo universe. Its
     * fields are the export's, but the mandate's <i>own</i> columns beside them are not, and without
     * this a market company could never carry a value in a column the mandate added.
     */
    @Transactional
    public TriageCompanyResponse editCustomFields(UUID userId, UUID workspaceId, UUID projectId,
                                                  UUID triageCompanyId, Map<String, String> customFields,
                                                  HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        TriageCompany company = triaged.findByIdAndProjectId(triageCompanyId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        company.describeCustomFields(customColumns.applyTo(
                projectId, CustomColumnTarget.COMPANY, company.getCustomFields(), customFields));

        audit.event(ProjectEventType.TRIAGE_COMPANY_EDITED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("triageCompanyId", triageCompanyId.toString())
                .detail("customFieldsOnly", "true")
                .record();
        return toDto(company);
    }

    /**
     * Drops this mandate's decision about a company. <b>Nothing of the company itself is deleted</b>:
     * {@code app_lm_apollo_companies} is ETL-owned and read-only here, so the company stays in the
     * universe and untouched for every other mandate.
     *
     * <p>Unlike Declining, this is not remembered — a later "Add all to Universe" may take the company
     * back in. To rule one out durably, decline it.
     */
    @Transactional
    public void removeFromProject(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                                  HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        TriageCompany company = triaged.findByIdAndProjectId(triageCompanyId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        triaged.delete(company);

        // The name is recorded because the row carrying it is about to stop existing, and an audit
        // entry naming only an unresolvable id answers no question later.
        audit.event(ProjectEventType.TRIAGE_COMPANY_REMOVED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("triageCompanyId", triageCompanyId.toString())
                .detail("companyName", company.getCompanyName())
                .record();
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
        if (token == null || token.isBlank()) {
            return null;
        }
        TriageCompanySortField field = TriageCompanySortField.fromValue(token);
        if (field == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown sort field: " + token);
        }
        return field;
    }

    private static Sort newestFirstIn(Sort.Direction direction) {
        return direction == Sort.Direction.DESC ? NEWEST_FIRST : Sort.by(Sort.Direction.ASC, "createdAt");
    }

    /** Omitted means DESC: the grid opens newest-first, and an absent direction must not reverse it. */
    private static SortDirection resolveDirection(String token) {
        if (token == null || token.isBlank()) {
            return SortDirection.DESC;
        }
        SortDirection direction = SortDirection.fromValue(token);
        if (direction == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown sort direction: " + token);
        }
        return direction;
    }

    /** The landing stage is where a company arrives from Strategy, and where a capture lands by default. */
    private static TriageCompanyStatus resolveStatus(String token) {
        if (token == null || token.isBlank()) {
            return TriageCompanyStatus.IN_UNIVERSE;
        }
        TriageCompanyStatus status = TriageCompanyStatus.fromValue(token);
        if (status == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown status: " + token);
        }
        return status;
    }

    /**
     * Defaults to a hand-typed company, and refuses {@code strategy} outright. A row claiming to come
     * from the market must come through {@link #add}, where the snapshot is resolved from the market
     * and the account id it is keyed by actually exists — V34's CHECK refuses the alternative anyway,
     * and a constraint violation is a worse way to learn it.
     */
    private static TriageCompanySource resolveCapturableSource(String token) {
        if (token == null || token.isBlank()) {
            return TriageCompanySource.MANUAL;
        }
        TriageCompanySource source = TriageCompanySource.fromValue(token);
        if (source == null || !CAPTURABLE_SOURCES.contains(source)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown capture source: " + token);
        }
        return source;
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }


    private static TriageCompanyResponse toDto(TriageCompany company) {
        return new TriageCompanyResponse(company.getId(), company.getApolloAccountId(),
                company.getSource().value(), company.getStatus().value(), company.getNote(),
                company.getCompanyName(), company.getIndustry(), company.getCompanyCountry(),
                company.getCompanyCity(), company.getNumEmployees(), company.getAnnualRevenue(),
                company.getWebsite(), company.getCompanyLinkedinUrl(), company.getFoundedYear(),
                company.getShortDescription(), company.getSourceUrl(), company.getLogoUrl(),
                company.getCustomFields().asMap(), company.getCreatedAt());
    }
}
