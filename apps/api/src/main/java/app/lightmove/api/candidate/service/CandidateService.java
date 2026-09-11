package app.lightmove.api.candidate.service;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.dto.CandidateCareerEntryDto;
import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import app.lightmove.api.candidate.dto.CandidateEducationEntryDto;
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
 * A mandate's mapped executives: adding one, replacing one whole, reading them back, removing one.
 *
 * <p>The one decision here that is not bookkeeping is where a candidate sits. Naming one of the
 * mandate's triaged companies maps the person to it <i>and</i> snapshots that company's name, and the
 * caller's {@code employerName} is ignored — two fields that could disagree about the same company
 * would drift the moment either changed.
 */
@Service
@Slf4j
public class CandidateService {

    /**
     * First mapped first. The name breaks ties, so paging cannot shuffle two people researched in the
     * same instant across a page boundary.
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
     * One page of people. {@code triageCompanyIds} is how the Companies grid reads, and an empty list
     * is answered without a query — a page with no companies on it, not a request for everyone.
     *
     * <p><b>A caller naming no size but a company filter gets the ceiling, not the default.</b> The
     * SPA used to name a size computed as a multiple of its page size, which landed exactly on
     * {@code maxPageSize}, so lowering that knob would have 400'd the people read on every Companies
     * page. An <i>explicit</i> oversized size is still refused.
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
     * Every executive the mandate has mapped, unpaged — the seam {@code talentmap} reads people
     * through. Takes a cap the caller states and states it back in {@code totalCount}, so a mandate
     * past it is told rather than shown a map that looks complete and is not.
     *
     * <p>Sorted rather than unsorted, so the cut at the cap is deterministic and a mandate past it
     * sees the same people on every read.
     */
    @Transactional(readOnly = true)
    public CandidatesResponse listAllOfProject(UUID workspaceId, UUID projectId, int cap) {
        requireProject(projectId, workspaceId);
        Page<Candidate> found = candidates.findByProjectId(projectId, PageRequest.of(0, cap, FIRST_MAPPED_FIRST));
        return new CandidatesResponse(
                found.getContent().stream().map(CandidateService::toDto).toList(),
                found.getTotalElements(), 0, cap);
    }

    /**
     * The person this mandate already has for a spreadsheet row, so a second import updates profiles
     * rather than colliding on every row.
     *
     * <p>Email first, name second: a name only identifies someone <i>within</i> a company, which is
     * the scope V36's unique indexes draw, so matching on it across the mandate would merge two people
     * who share one. Oldest first when more than one answers — nothing makes either column unique.
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
        refuseHeldProfile(projectId, details.linkedinUrl(), null);

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
     * Replaces a candidate whole, the mapped company included: moving someone is an ordinary edit of
     * where they work rather than a separate verb.
     */
    @Transactional
    public CandidateResponse replace(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                     SaveCandidateRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        Candidate candidate = candidates.findByIdAndProjectId(candidateId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        CandidateDetails details = detailsOf(projectId, request);
        refuseDuplicate(projectId, request.triageCompanyId(), details.fullName(), candidateId);
        refuseHeldProfile(projectId, details.linkedinUrl(), candidateId);

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
     * Moves someone along the line and touches nothing else. Not a {@link #replace} with one field
     * changed: the panel may have been open a while, and re-submitting a stale profile would undo
     * whatever was edited meanwhile.
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
     * The short transactional tail of an enrichment.
     *
     * <p>{@code REQUIRES_NEW} because the enrichment worker calls this from an {@code AFTER_COMMIT}
     * callback, where the completed transaction's resources are still bound to the thread and joining
     * them writes nothing. A racing drawer edit wins by {@code @Version}: the save throws an
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
     * mandate's universe and is mapped to it. Skipped when the mandate already maps someone of the
     * same name at that company: V36's partial unique index would refuse the row, and a constraint
     * violation here would roll the whole enrichment back with it.
     */
    private void mapToEmployer(UUID projectId, Candidate candidate, EnrichedProfile enriched) {
        if (candidate.getTriageCompanyId() != null || enriched.employerName() == null) {
            return;
        }
        // An employer somebody already stated outranks the vendor's, as enrich() also refuses to
        // overwrite it: mapping would otherwise reintroduce through employBy() exactly what that
        // guard just prevented.
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
        // Existence, not content: a grid of avatars asks this per row, and loading each whole row —
        // profile jsonb included — to throw it away is a scan the scoping does not need.
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

        // The name is recorded because the row carrying it is about to stop existing, and an audit
        // entry naming only an unresolvable id answers no question later.
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
     * Where a candidate sits. A named company is resolved through {@code triagecompany}'s public seam,
     * which proves it belongs to this mandate — so one cannot be filed against another project's.
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
     * Refuses a name the mandate already maps, in the two scopes V36's partial unique indexes draw:
     * at the company where there is one, across the mandate where there is not. Checked here rather
     * than left to the constraint, because a violation surfaces as a 500 the caller cannot act on.
     * {@code selfId} excludes the row being edited so a save without a rename does not collide.
     *
     * <p>Both finders carry the project id, including the one that already names a company. Scoping by
     * the company alone would be safe only by the order of the statements above, and a reorder would
     * turn this 409 into an oracle confirming another workspace's company id and a name mapped at it.
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

    /**
     * Refuses a LinkedIn profile the mandate already maps, wherever that row currently sits. The name
     * rule above cannot answer this one: a second capture arrives with no company, so it is checked
     * against the unmapped scope only, while the first has since been researched and mapped to its
     * employer and sits in the company scope. The two look past each other, the row is created, and
     * {@link #mapToEmployer} then finds the name held at that company and leaves the duplicate
     * unmapped — a second line reading "Not in universe" that nobody asked for.
     *
     * <p>The slug rather than the stored URL, because {@link LinkedInUrls} already rules that
     * {@code /in/John-Smith} and {@code /in/john-smith} are one profile. The finder only narrows, so
     * identity is settled here.
     *
     * <p>{@code selfId} is the row being edited, excluded so that saving someone without changing
     * their URL does not collide with themselves.
     */
    private void refuseHeldProfile(UUID projectId, String linkedinUrl, UUID selfId) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        if (slug == null) {
            return;
        }
        boolean held = candidates.findByProjectIdAndProfileSlugLike(projectId, slug).stream()
                .filter(other -> !other.getId().equals(selfId))
                .anyMatch(other -> slug.equals(LinkedInUrls.profileSlugOrNull(other.getLinkedinUrl())));
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
     * Only a page the plugin actually read is worth a billed call, and "worth billing" is exactly "a
     * slug came back" — the providers key on the slug, so gate and lookup must agree.
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
                candidate.getProfile().education().stream()
                        .map(school -> new CandidateEducationEntryDto(school.school(), school.degree(),
                                school.period()))
                        .toList(),
                candidate.getProfile().skills(),
                candidate.getSource().value(),
                candidate.getSourceUrl(),
                candidate.getCustomFields().asMap(),
                candidate.getCreatedAt(),
                candidate.getProfile().enrichedAt());
    }
}
