package app.lightmove.api.candidate.service;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.dto.CandidateCareerEntryDto;
import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.dto.UpdateCandidateStatusRequest;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCapturedEvent;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.CandidatePhoto;
import app.lightmove.api.candidate.model.CandidateProfile;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.StoredPhoto;
import app.lightmove.api.candidate.repository.CandidatePhotoRepository;
import app.lightmove.api.candidate.repository.CandidateRepository;
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
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A mandate's mapped executives: adding one, replacing one whole, reading them back, and removing one.
 *
 * <p>The one decision this service makes that is not bookkeeping is where a candidate sits. Naming one
 * of the mandate's triaged companies maps the person to it <i>and</i> snapshots that company's name;
 * naming none leaves them unmapped with whatever employer the researcher typed. The caller's
 * {@code employerName} is ignored in the first case on purpose — two fields that could disagree about
 * the same company would drift the moment either changed.
 */
@Service
@Slf4j
public class CandidateService {

    /**
     * First mapped first, which is the order a consultant reads a company's people in: the executive
     * the mandate found first is the one the row leads with. The name breaks ties so paging cannot
     * shuffle two people researched in the same instant across a page boundary.
     */
    private static final Sort FIRST_MAPPED_FIRST =
            Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "fullName"));

    private final CandidateRepository candidates;
    private final CandidatePhotoRepository photos;
    private final ProjectRepository projects;
    private final TriageCompanyService triage;
    private final CustomColumnService customColumns;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ProjectStreamPublisher stream;
    private final CompanyListSettings listConfig;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public CandidateService(CandidateRepository candidates, CandidatePhotoRepository photos,
                            ProjectRepository projects, TriageCompanyService triage,
                            CustomColumnService customColumns, AuditService audit,
                            ApplicationEventPublisher events, ProjectStreamPublisher stream,
                            LightMoveProperties properties) {
        this.candidates = candidates;
        this.photos = photos;
        this.projects = projects;
        this.triage = triage;
        this.customColumns = customColumns;
        this.audit = audit;
        this.events = events;
        this.stream = stream;
        this.listConfig = properties.company().list();
    }

    /**
     * One page of people, narrowed by whichever company filter the caller asked for.
     *
     * <p>{@code triageCompanyIds} is how the Companies grid reads: it renders one page of companies and
     * asks for the people at exactly those, rather than for the mandate's whole roster, which grows
     * without bound as a mapping fills in. An empty list is answered without a query — it is a page
     * with no companies on it, not a request for everyone.
     *
     * <p><b>A caller that names no size and does name a company filter gets the ceiling, not the
     * default.</b> Those two filters have no pager behind them: the grid is asking "who is at these
     * companies?", and the answer's natural size is as many as this endpoint will return. The
     * alternative had the SPA naming a size of its own, which it had computed as a multiple of its
     * page size — and that landed exactly on {@code maxPageSize}, so lowering the deployment knob under
     * it would have 400'd the people read on every Companies page. The client no longer names one, so
     * it can no longer be refused for guessing this number wrong.
     *
     * <p>An <i>explicit</i> oversized size is still refused, which is the same contract the companies
     * list keeps: a caller that names a number is a caller that can be told the number is wrong.
     */
    @Transactional(readOnly = true)
    public CandidatesResponse list(UUID workspaceId, UUID projectId, CandidateListCriteria criteria) {
        int page = criteria.page() == null ? 0 : criteria.page();
        int size = criteria.size() == null ? unpagedSizeFor(criteria) : criteria.size();
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must not be negative");
        }
        if (size < 1 || size > listConfig.maxPageSize()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "size must be between 1 and " + listConfig.maxPageSize());
        }
        requireProject(projectId, workspaceId);

        List<UUID> companyIds = criteria.triageCompanyIds();
        if (companyIds != null && companyIds.size() > listConfig.maxPageSize()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "triageCompanyIds must name " + listConfig.maxPageSize() + " companies at most");
        }
        if (companyIds != null && companyIds.isEmpty()) {
            return new CandidatesResponse(List.of(), 0, page, size);
        }

        PageRequest pageRequest = PageRequest.of(page, size, FIRST_MAPPED_FIRST);
        String nameQuery = criteria.nameQuery() == null ? "" : criteria.nameQuery().trim();
        Page<Candidate> found = findPage(projectId, criteria, companyIds, nameQuery, pageRequest);

        return new CandidatesResponse(
                found.getContent().stream().map(CandidateService::toDto).toList(),
                found.getTotalElements(), page, size);
    }

    /**
     * The person this mandate already has for a spreadsheet row, if any — the seam the import resolves
     * a person through, so a second import of the same list updates profiles rather than colliding
     * with {@code CANDIDATE_ALREADY_MAPPED} on every row.
     *
     * <p>Email first and name second, because those identify a person differently. An address is the
     * one field that is the same across two exports that spell the name differently; a name only
     * identifies someone <i>within</i> a company, which is exactly the scope V36's unique indexes
     * enforce. Matching on name alone across the whole mandate would merge two different people who
     * happen to share one at two different employers.
     *
     * <p>Oldest first when more than one row answers, for the reason the repository's finders return
     * lists at all: nothing makes either column unique, and this has to answer rather than throw.
     */
    @Transactional(readOnly = true)
    public Optional<CandidateResponse> findCandidateOfProject(UUID projectId, UUID triageCompanyId,
                                                              String email, String fullName) {
        if (email != null && !email.isBlank()) {
            Optional<Candidate> byEmail =
                    candidates.findByProjectIdAndEmailIgnoreCase(projectId, email.trim()).stream()
                            .min(Comparator.comparing(Candidate::getCreatedAt));
            if (byEmail.isPresent()) {
                return byEmail.map(CandidateService::toDto);
            }
        }
        if (fullName == null || fullName.isBlank()) {
            return Optional.empty();
        }
        List<Candidate> byName = triageCompanyId == null
                ? candidates.findByProjectIdAndTriageCompanyIdIsNullAndFullNameIgnoreCase(projectId, fullName.trim())
                : candidates.findByProjectIdAndTriageCompanyIdAndFullNameIgnoreCase(projectId, triageCompanyId, fullName.trim());
        return byName.stream()
                .min(Comparator.comparing(Candidate::getCreatedAt))
                .map(CandidateService::toDto);
    }

    @Transactional
    public CandidateResponse add(UUID userId, UUID workspaceId, UUID projectId,
                                 SaveCandidateRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        CandidateSource source = resolveSource(request.source());
        CandidateDetails details = detailsOf(projectId, request);

        refuseDuplicate(projectId, request.triageCompanyId(), details.fullName(), null);

        Candidate candidate = candidates.save(Candidate.mapped(projectId, userId,
                request.triageCompanyId(), source, details));
        candidate.describeCustomFields(customColumns.applyTo(projectId, CustomColumnTarget.CANDIDATE,
                candidate.getCustomFields(), request.customFields()));

        if (source == CandidateSource.EXTENSION && isLinkedInProfileUrl(details.linkedinUrl())) {
            events.publishEvent(new CandidateCapturedEvent(candidate.getId(), projectId,
                    details.linkedinUrl()));
        }
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_CAPTURED);

        audit.event(ProjectEventType.CANDIDATE_ADDED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("candidateId", candidate.getId().toString())
                .detail("source", source.name())
                .record();
        return toDto(candidate);
    }

    /**
     * Replaces a candidate whole, including the company they are mapped to — moving someone to another
     * of the mandate's companies, or off the universe entirely, is an ordinary edit of where they work
     * rather than a separate verb.
     */
    @Transactional
    public CandidateResponse replace(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                     SaveCandidateRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        Candidate candidate = candidates.findByIdAndProjectId(candidateId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        CandidateDetails details = detailsOf(projectId, request);
        refuseDuplicate(projectId, request.triageCompanyId(), details.fullName(), candidateId);

        candidate.remapTo(request.triageCompanyId());
        candidate.describe(details);
        candidate.describeCustomFields(customColumns.applyTo(projectId, CustomColumnTarget.CANDIDATE,
                candidate.getCustomFields(), request.customFields()));

        audit.event(ProjectEventType.CANDIDATE_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("candidateId", candidateId.toString())
                .record();
        return toDto(candidate);
    }

    /**
     * Moves someone along the line and touches nothing else — the status pill on the read-only profile
     * panel, which a researcher flicks while reading rather than while editing.
     *
     * <p>Deliberately not a {@link #replace} with one field changed: the panel may have been open for
     * a while, and re-submitting a stale profile to change one value would quietly undo whatever was
     * edited in the meantime.
     */
    @Transactional
    public CandidateResponse changeStatus(UUID userId, UUID workspaceId, UUID projectId,
                                          UUID candidateId, UpdateCandidateStatusRequest request,
                                          HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        Candidate candidate = candidates.findByIdAndProjectId(candidateId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        candidate.moveTo(resolveStatus(request.status()));

        audit.event(ProjectEventType.CANDIDATE_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("status", request.status())
                .record();
        return toDto(candidate);
    }

    /**
     * The short transactional tail of an enrichment: re-read the row, fill it in, file the employer
     * into the mandate's universe, keep the photo, announce the change, commit.
     *
     * <p>{@code REQUIRES_NEW} because the enrichment worker calls this from an {@code AFTER_COMMIT}
     * callback, where the completed transaction's resources are still bound to the thread and joining
     * them writes nothing. The row is re-read project-scoped (the tenant invariant every candidate
     * finder keeps), and a candidate already enriched, or deleted while the provider was answering,
     * is left alone. A racing drawer edit wins by {@code @Version}: the save throws an
     * optimistic-locking failure into the worker's catch and the researcher's version stands.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyResearch(UUID projectId, UUID candidateId, EnrichedProfile enriched) {
        candidates.findByIdAndProjectId(candidateId, projectId).ifPresentOrElse(candidate -> {
            if (candidate.getProfile().enrichedAt() != null) {
                return;
            }
            candidate.enrich(enriched);
            mapToEmployer(projectId, candidate, enriched);
            keepPhoto(candidateId, enriched);
            stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        }, () -> log.info("Candidate {} was removed before its research landed", candidateId));
    }

    /**
     * An unmapped candidate whose research names an employer gets that company filed into the
     * mandate's universe and is mapped to it — the same resolve-then-snapshot the manual mapping path
     * performs, minus the consultant. Skipped when the mandate already maps someone of the same name
     * at that company: V36's partial unique index would refuse the row, and a constraint violation
     * here would roll the whole enrichment back with it.
     */
    private void mapToEmployer(UUID projectId, Candidate candidate, EnrichedProfile enriched) {
        if (candidate.getTriageCompanyId() != null || enriched.employerName() == null) {
            return;
        }
        // An employer somebody already stated outranks the vendor's, the same way enrich() refuses to
        // overwrite it — mapping would otherwise reintroduce through employBy() exactly what that
        // guard just prevented, and file a stale employer into the universe besides.
        if (candidate.getCompanyName() != null
                && !candidate.getCompanyName().equalsIgnoreCase(enriched.employerName())) {
            return;
        }
        TriageCompanyResponse company = triage.captureFromResearch(projectId,
                candidate.getAddedBy(), new CapturedCompanyDetails(
                        enriched.employerName(), null, null, null, null, null, null,
                        enriched.employerLinkedinUrl(), null, null, enriched.employerLogoUrl(),
                        null, null));

        boolean nameHeldThere = candidates
                .findByProjectIdAndTriageCompanyIdAndFullNameIgnoreCase(
                        projectId, company.id(), candidate.getFullName())
                .stream()
                .anyMatch(other -> !other.getId().equals(candidate.getId()));
        if (nameHeldThere) {
            log.info("Leaving candidate {} unmapped — {} already maps that name", candidate.getId(),
                    company.companyName());
            return;
        }
        candidate.employBy(company.id(), company.companyName());
    }

    private void keepPhoto(UUID candidateId, EnrichedProfile enriched) {
        if (enriched.photo() == null || photos.existsByCandidateId(candidateId)) {
            return;
        }
        photos.save(CandidatePhoto.of(candidateId, enriched.photo()));
    }

    /** The stored profile photo, or NOT_FOUND — "no photo" and "no such candidate" read the same. */
    @Transactional(readOnly = true)
    public StoredPhoto photoOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        requireProject(projectId, workspaceId);
        // Existence, not content: a grid of avatars asks this per row, and loading each candidate's
        // whole row — profile jsonb included — to throw it away is a scan the scoping does not need.
        if (!candidates.existsByIdAndProjectId(candidateId, projectId)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return photos.findByCandidateId(candidateId)
                .map(photo -> new StoredPhoto(photo.getContent(), photo.getContentType()))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public void remove(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                       HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        Candidate candidate = candidates.findByIdAndProjectId(candidateId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        candidates.delete(candidate);

        // The name is recorded because the row that carried it is about to stop existing, and an audit
        // entry naming only an id nobody can resolve answers no question later.
        audit.event(ProjectEventType.CANDIDATE_REMOVED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("fullName", candidate.getFullName())
                .record();
    }

    /**
     * What a caller that named no size gets. A company filter means the read has no pager and wants
     * everything it is entitled to; anything else is a plain list and takes the ordinary page.
     */
    private int unpagedSizeFor(CandidateListCriteria criteria) {
        boolean filteredByCompany =
                criteria.triageCompanyIds() != null || Boolean.TRUE.equals(criteria.unmapped());
        return filteredByCompany ? listConfig.maxPageSize() : listConfig.defaultPageSize();
    }

    private Page<Candidate> findPage(UUID projectId, CandidateListCriteria criteria,
                                     List<UUID> companyIds, String nameQuery, PageRequest pageRequest) {
        if (companyIds != null) {
            return candidates.findByProjectIdAndTriageCompanyIdInAndFullNameContainingIgnoreCase(
                    projectId, companyIds, nameQuery, pageRequest);
        }
        if (Boolean.TRUE.equals(criteria.unmapped())) {
            return candidates.findByProjectIdAndTriageCompanyIdIsNullAndFullNameContainingIgnoreCase(
                    projectId, nameQuery, pageRequest);
        }
        return candidates.findByProjectIdAndFullNameContainingIgnoreCase(
                projectId, nameQuery, pageRequest);
    }

    /**
     * Where a candidate sits, and the employer name that follows from it. A named company is resolved
     * through {@code triagecompany}'s one public seam, which also proves it belongs to this mandate —
     * so a candidate cannot be filed against another project's company by id.
     */
    private CandidateDetails detailsOf(UUID projectId, SaveCandidateRequest request) {
        CandidateDetails details = new CandidateDetails(
                request.fullName(), request.title(), resolveSeniority(request.seniority()),
                resolveStatus(request.status()), request.employerName(), request.email(),
                request.phone(), request.linkedinUrl(), request.locationCountry(),
                request.locationCity(), request.nationality(), request.yearsExperience(),
                request.summary(), request.note(), compensationOf(request.compensation()),
                profileOf(request), request.sourceUrl());

        if (request.triageCompanyId() == null) {
            return details;
        }
        TriageCompanyResponse company =
                triage.requireCompanyOfProject(projectId, request.triageCompanyId());
        return details.employedAt(company.companyName());
    }

    /**
     * Refuses a name the mandate already maps, and refuses it in the two scopes V36's partial unique
     * indexes draw: at the company where there is one, across the mandate where there is not. Checked
     * here rather than left to the constraint because a violation surfaces as a 500 the caller cannot
     * act on, and the field to correct is the name.
     *
     * <p>{@code selfId} is the row being edited, excluded so that saving someone without renaming them
     * does not collide with themselves.
     *
     * <p>Both finders carry the project id, including the one that already names a company. Scoping it
     * by the company alone would be safe only by the order of the statements above — {@code detailsOf}
     * proves the company belongs to the mandate before this runs — and a reorder would turn this 409
     * into an oracle confirming another workspace's company id and a name mapped at it.
     */
    private void refuseDuplicate(UUID projectId, UUID triageCompanyId, String fullName, UUID selfId) {
        List<Candidate> sameName = triageCompanyId == null
                ? candidates.findByProjectIdAndTriageCompanyIdIsNullAndFullNameIgnoreCase(projectId, fullName)
                : candidates.findByProjectIdAndTriageCompanyIdAndFullNameIgnoreCase(
                        projectId, triageCompanyId, fullName);

        boolean held = sameName.stream().anyMatch(other -> !other.getId().equals(selfId));
        if (held) {
            throw ApiException.of(ErrorCode.CANDIDATE_ALREADY_MAPPED);
        }
    }

    private static CandidateCompensation compensationOf(CandidateCompensationDto supplied) {
        if (supplied == null) {
            return CandidateCompensation.unknown();
        }
        return new CandidateCompensation(supplied.currency(), supplied.baseSalary(), supplied.bonus(),
                supplied.allowances(), supplied.longTermIncentive(), supplied.noticePeriod());
    }

    private static CandidateProfile profileOf(SaveCandidateRequest request) {
        List<CandidateCareerEntry> career = request.career() == null ? List.of()
                : request.career().stream()
                        .map(entry -> new CandidateCareerEntry(entry.company(), entry.title(), entry.period()))
                        .toList();
        return new CandidateProfile(career, request.languages(), null, null, null);
    }

    /**
     * Only a page the plugin actually read is worth a billed research call. "Worth billing" is
     * exactly "a slug came back": a bare {@code /in/} carries no one to research, and the providers
     * are keyed by the slug rather than by the URL, so the gate and the lookup must agree on what
     * counts.
     */
    private static boolean isLinkedInProfileUrl(String url) {
        return LinkedInUrls.profileSlugOrNull(url) != null;
    }

    /** Omitted means identified — where every profile starts, and the only honest default. */
    private static CandidateStatus resolveStatus(String token) {
        if (token == null || token.isBlank()) {
            return CandidateStatus.IDENTIFIED;
        }
        CandidateStatus status = CandidateStatus.fromValue(token);
        if (status == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown candidate status: " + token);
        }
        return status;
    }

    /** Null when nobody named a level, which is not the same as naming an unknown one. */
    private static Seniority resolveSeniority(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Seniority seniority = Seniority.fromValue(token);
        if (seniority == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown seniority level: " + token);
        }
        return seniority;
    }

    private static CandidateSource resolveSource(String token) {
        if (token == null || token.isBlank()) {
            return CandidateSource.MANUAL;
        }
        CandidateSource source = CandidateSource.fromValue(token);
        if (source == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown candidate source: " + token);
        }
        return source;
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private static CandidateResponse toDto(Candidate candidate) {
        CandidateCompensation compensation = candidate.compensation();
        return new CandidateResponse(
                candidate.getId(),
                candidate.getTriageCompanyId(),
                candidate.getCompanyName(),
                candidate.getFullName(),
                candidate.getTitle(),
                candidate.getSeniorityLevel() == null ? null : candidate.getSeniorityLevel().value(),
                candidate.getStatus().value(),
                candidate.getEmail(),
                candidate.getPhone(),
                candidate.getLinkedinUrl(),
                candidate.getLocationCountry(),
                candidate.getLocationCity(),
                candidate.getNationality(),
                candidate.getYearsExperience(),
                candidate.getSummary(),
                candidate.getNote(),
                new CandidateCompensationDto(compensation.currency(), compensation.baseSalary(),
                        compensation.bonus(), compensation.allowances(),
                        compensation.longTermIncentive(), compensation.noticePeriod()),
                candidate.getProfile().career().stream()
                        .map(entry -> new CandidateCareerEntryDto(entry.company(), entry.title(), entry.period()))
                        .toList(),
                candidate.getProfile().languages(),
                candidate.getSource().value(),
                candidate.getSourceUrl(),
                candidate.getCustomFields().asMap(),
                candidate.getCreatedAt(),
                candidate.getProfile().enrichedAt());
    }
}
