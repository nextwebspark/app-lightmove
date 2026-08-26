package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CandidateSeniority;
import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.dto.CandidateCareerEntryDto;
import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.CandidateProfile;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
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
public class CandidateService {

    /**
     * First mapped first, which is the order a consultant reads a company's people in: the executive
     * the mandate found first is the one the row leads with. The name breaks ties so paging cannot
     * shuffle two people researched in the same instant across a page boundary.
     */
    private static final Sort FIRST_MAPPED_FIRST =
            Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "fullName"));

    private final CandidateRepository candidates;
    private final ProjectRepository projects;
    private final TriageCompanyService triage;
    private final AuditService audit;
    private final CompanyListSettings listConfig;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public CandidateService(CandidateRepository candidates, ProjectRepository projects,
                            TriageCompanyService triage, AuditService audit,
                            LightMoveProperties properties) {
        this.candidates = candidates;
        this.projects = projects;
        this.triage = triage;
        this.audit = audit;
        this.listConfig = properties.company().list();
    }

    /**
     * One page of people, narrowed by whichever company filter the caller asked for.
     *
     * <p>{@code triageCompanyIds} is how the Companies grid reads: it renders one page of companies and
     * asks for the people at exactly those, rather than for the mandate's whole roster, which grows
     * without bound as a mapping fills in. An empty list is answered without a query — it is a page
     * with no companies on it, not a request for everyone.
     */
    @Transactional(readOnly = true)
    public CandidatesResponse list(UUID workspaceId, UUID projectId, CandidateListCriteria criteria) {
        int page = criteria.page() == null ? 0 : criteria.page();
        int size = criteria.size() == null ? listConfig.defaultPageSize() : criteria.size();
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

    @Transactional
    public CandidateResponse add(UUID userId, UUID workspaceId, UUID projectId,
                                 SaveCandidateRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        CandidateSource source = resolveSource(request.source());
        CandidateDetails details = detailsOf(projectId, request);

        refuseDuplicate(projectId, request.triageCompanyId(), details.fullName(), null);

        Candidate candidate = candidates.save(Candidate.mapped(projectId, userId,
                request.triageCompanyId(), source, details));

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

        audit.event(ProjectEventType.CANDIDATE_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("candidateId", candidateId.toString())
                .record();
        return toDto(candidate);
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
     */
    private void refuseDuplicate(UUID projectId, UUID triageCompanyId, String fullName, UUID selfId) {
        List<Candidate> sameName = triageCompanyId == null
                ? candidates.findByProjectIdAndTriageCompanyIdIsNullAndFullNameIgnoreCase(projectId, fullName)
                : candidates.findByTriageCompanyIdAndFullNameIgnoreCase(triageCompanyId, fullName);

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
        return new CandidateProfile(career, request.languages());
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
    private static CandidateSeniority resolveSeniority(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        CandidateSeniority seniority = CandidateSeniority.fromValue(token);
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
                candidate.getCreatedAt());
    }
}
