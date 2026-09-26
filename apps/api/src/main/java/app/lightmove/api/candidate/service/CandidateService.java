package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.dto.UpdateCandidateContactsRequest;
import app.lightmove.api.candidate.dto.UpdateCandidateStatusRequest;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateAiEnrichRequested;
import app.lightmove.api.candidate.model.CandidateAiEnrichState;
import app.lightmove.api.candidate.model.CandidateAiEnrichment;
import app.lightmove.api.candidate.model.CandidateAttribution;
import app.lightmove.api.candidate.model.CandidateCapturedEvent;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidateContactState;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidatePhoto;
import app.lightmove.api.candidate.model.CandidateProfile;
import app.lightmove.api.candidate.model.ContactEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final CandidateRequestReader requests;
    private final CandidateResponseMapper responses;
    private final CompanyListSettings listConfig;

    public CandidateService(CandidateRepository candidates, CandidatePhotoRepository photos,
                            ProjectRepository projects, TriageCompanyService triage,
                            CustomColumnService customColumns, AuditService audit,
                            ApplicationEventPublisher events, ProjectStreamPublisher stream,
                            CandidateRequestReader requests, CandidateResponseMapper responses,
                            LightMoveProperties properties) {
        this.candidates = candidates;
        this.photos = photos;
        this.projects = projects;
        this.triage = triage;
        this.requests = requests;
        this.responses = responses;
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
        listConfig.requireValidPage(page, size);
        projects.requireInWorkspace(projectId, workspaceId);

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
                found.getContent().stream().map(responses::toDto).toList(),
                found.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public CandidateResponse get(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return responses.toDto(candidates.requireInProject(candidateId, projectId));
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
        projects.requireInWorkspace(projectId, workspaceId);
        Page<Candidate> found = candidates.findByProjectId(projectId, PageRequest.of(0, cap, FIRST_MAPPED_FIRST));
        return new CandidatesResponse(
                found.getContent().stream().map(responses::toDto).toList(),
                found.getTotalElements(), 0, cap);
    }

    /**
     * Who filed each of the mandate's executives, by candidate id — the report's researcher breakdown.
     * Its own read rather than a field on {@code CandidateResponse}, which a client seat also reads.
     */
    @Transactional(readOnly = true)
    public Map<UUID, UUID> addedByOf(UUID workspaceId, UUID projectId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return candidates.findAttributionByProjectId(projectId).stream()
                .collect(Collectors.toMap(CandidateAttribution::getCandidateId, CandidateAttribution::getAddedBy));
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
            Optional<Candidate> byEmail = candidates
                    .findByProjectIdAndEmailKey(projectId, CandidateContact.keyOf(ContactChannel.EMAIL, email))
                    .stream()
                    .min(Comparator.comparing(Candidate::getCreatedAt));
            if (byEmail.isPresent()) {
                return byEmail.map(responses::toDto);
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
                .map(responses::toDto);
    }

    @Transactional
    public CandidateResponse add(UUID userId, UUID workspaceId, UUID projectId,
                                 SaveCandidateRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        CandidateSource source = requests.resolveSource(request.source());
        CandidateDetails details = requests.detailsOf(projectId, request, null);

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

        audit.projectEvent(ProjectEventType.CANDIDATE_ADDED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidate.getId().toString())
                .detail("source", source.name())
                .record();
        return responses.toDto(candidate);
    }

    /**
     * Replaces a candidate whole, the mapped company included: moving someone is an ordinary edit of
     * where they work rather than a separate verb.
     */
    @Transactional
    public CandidateResponse replace(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                     SaveCandidateRequest request, HttpServletRequest httpRequest) {
        return replace(userId, workspaceId, projectId, candidateId, request, ContactSource.MANUAL,
                httpRequest);
    }

    /**
     * {@code door} is the ledger's word for who is writing — the drawer is a person, the importer is
     * a spreadsheet — and it is a parameter rather than read off {@code request.source()} because the
     * drawer replays the row's own source on every edit, and a captured executive's address corrected
     * by hand was typed, not captured.
     */
    @Transactional
    public CandidateResponse replace(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                     SaveCandidateRequest request, ContactSource door,
                                     HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);

        CandidateDetails details = requests.detailsOf(projectId, request, candidate.getTriageCompanyId());
        refuseDuplicate(projectId, request.triageCompanyId(), details.fullName(), candidateId);
        refuseHeldProfile(projectId, details.linkedinUrl(), candidateId);
        refuseRetypedCapturedProfile(candidate, details.linkedinUrl());

        candidate.remapTo(request.triageCompanyId());
        candidate.describe(details, door);
        if (Boolean.TRUE.equals(request.confirmBackground())) {
            candidate.confirmBackground();
        }
        requests.refuseOverfullChannels(candidate);
        candidate.describeCustomFields(customColumns.applyTo(projectId, CustomColumnTarget.CANDIDATE,
                candidate.getCustomFields(), request.customFields()));

        audit.projectEvent(ProjectEventType.CANDIDATE_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .record();
        return responses.toDto(candidate);
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
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);

        candidate.moveTo(requests.resolveStatus(request.status()));

        audit.projectEvent(ProjectEventType.CANDIDATE_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("status", request.status())
                .record();
        return responses.toDto(candidate);
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
            projects.findById(projectId).ifPresent(project -> events.publishEvent(
                    new CandidateAiEnrichRequested(candidateId, projectId, project.getWorkspaceId(),
                            candidate.getAddedBy(), AiEnrichTrigger.CAPTURE)));
        }, () -> log.info("Candidate {} was removed before its research landed", candidateId));
    }

    /** Confirms the candidate is one of this workspace's before an AI enrichment is paid for. */
    @Transactional(readOnly = true)
    public void requireCandidate(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        candidates.requireInProject(candidateId, projectId);
    }

    /**
     * What the AI enrichment may send to the model — {@link CandidateDossier} is an allowlist, so
     * contact details and compensation never leave through here. Empty once the row is gone.
     */
    @Transactional(readOnly = true)
    public Optional<CandidateDossier> dossierOf(UUID projectId, UUID candidateId) {
        return candidates.findByIdAndProjectId(candidateId, projectId).map(candidate -> {
            CandidateProfile profile = candidate.getProfile();
            return new CandidateDossier(candidate.getFullName(), candidate.getTitle(),
                    candidate.getCompanyName(), candidate.getLocationCity(), candidate.getLocationCountry(),
                    candidate.getLinkedinUrl(), candidate.getSummary(), profile.career(),
                    profile.education(), profile.skills(), profile.languages(),
                    candidate.missingBackground());
        });
    }

    /**
     * The last AI assessment and the last failed run, staff-only — neither is carried on
     * {@link CandidateResponse}. Empty when the candidate has never been enriched.
     */
    @Transactional(readOnly = true)
    public Optional<CandidateAiEnrichState> aiAssessmentOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        if (candidate.getAiAssessment() == null && candidate.getAiEnrichFailedAt() == null) {
            return Optional.empty();
        }
        return Optional.of(new CandidateAiEnrichState(candidate.getAiAssessment(), candidate.getAiEnrichFailedAt()));
    }

    /** Stamps a run that produced nothing, so the drawer says so at once; a later success clears it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAiEnrichFailure(UUID projectId, UUID candidateId) {
        candidates.findByIdAndProjectId(candidateId, projectId).ifPresent(candidate -> {
            candidate.recordAiEnrichFailure();
            stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        });
    }

    /**
     * The AI enrichment's own write: background into whichever fields are still empty, and the
     * assessment replaced whole. {@code REQUIRES_NEW} for {@link #applyResearch}'s reason; a racing
     * drawer edit wins by {@code @Version} the same way.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyAiEnrichment(UUID projectId, UUID candidateId, CandidateAiEnrichment enrichment) {
        candidates.findByIdAndProjectId(candidateId, projectId).ifPresent(candidate -> {
            candidate.proposeBackground(enrichment.background());
            candidate.recordAiAssessment(enrichment.assessment());
            stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        });
    }

    /**
     * The Contact section's save: both channels replaced wholesale by what the person now lists.
     * Every entry is a person's claim written through the drawer, so the door is always MANUAL —
     * a row that came from a spreadsheet or the plugin keeps that source unless its spelling changes.
     */
    @Transactional
    public CandidateResponse replaceContacts(UUID userId, UUID workspaceId, UUID projectId,
                                             UUID candidateId, UpdateCandidateContactsRequest request,
                                             HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);

        List<ContactEntry> emails = requests.entriesOf(ContactChannel.EMAIL, request.emails(), null);
        List<ContactEntry> phones = requests.entriesOf(ContactChannel.PHONE, request.phones(), null);
        candidate.replaceContacts(ContactChannel.EMAIL, emails, ContactSource.MANUAL);
        candidate.replaceContacts(ContactChannel.PHONE, phones, ContactSource.MANUAL);
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);

        audit.projectEvent(ProjectEventType.CANDIDATE_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("section", "contacts")
                .record();
        return responses.toDto(candidate);
    }

    /**
     * The plugin read this row's URL off the profile page it was on; research and contact lookup
     * key on that slug, and a retyped one can only break them. A person's own row is theirs to fix.
     */
    private static void refuseRetypedCapturedProfile(Candidate candidate, String linkedinUrl) {
        if (candidate.getSource() == CandidateSource.EXTENSION
                && !Objects.equals(candidate.getLinkedinUrl(), linkedinUrl)) {
            throw ApiException.of(ErrorCode.CANDIDATE_PROFILE_URL_LOCKED);
        }
    }

    /**
     * What a contact lookup needs before it decides whether to spend a credit, in one read.
     */
    @Transactional(readOnly = true)
    public CandidateContactState contactStateOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        return new CandidateContactState(candidate.getLinkedinUrl(),
                candidate.hasAskedForEmails(), candidate.hasAskedForPhones(),
                candidate.hasFoundEmails(), candidate.hasFoundPhones(),
                responses.toDto(candidate));
    }

    /**
     * The short transactional tail of an email lookup, {@code applyResearch}'s shape without its
     * {@code REQUIRES_NEW}: this is called from a request thread with no transaction bound.
     *
     * <p>The guard is re-checked here rather than only before the vendor call, so two presses racing
     * each other leave the first answer standing instead of a second write of the same values.
     */
    @Transactional
    public CandidateResponse applyFoundEmails(UUID projectId, UUID candidateId, FoundEmails found) {
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        if (candidate.hasAskedForEmails()) {
            return responses.toDto(candidate);
        }
        candidate.recordFoundEmails(found);
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        return responses.toDto(candidate);
    }

    /** The phone half of {@link #applyFoundEmails}. */
    @Transactional
    public CandidateResponse applyFoundPhones(UUID projectId, UUID candidateId, FoundPhones found) {
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        if (candidate.hasAskedForPhones()) {
            return responses.toDto(candidate);
        }
        candidate.recordFoundPhones(found);
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        return responses.toDto(candidate);
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
        projects.requireInWorkspace(projectId, workspaceId);
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
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);

        candidates.delete(candidate);

        // The name is recorded because the row carrying it is about to stop existing, and an audit
        // entry naming only an unresolvable id answers no question later.
        audit.projectEvent(ProjectEventType.CANDIDATE_REMOVED, userId, workspaceId, projectId, httpRequest)
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

    /**
     * Only a page the plugin actually read is worth a billed call, and "worth billing" is exactly "a
     * slug came back" — the providers key on the slug, so gate and lookup must agree.
     */
    private static boolean isLinkedInProfileUrl(String url) {
        return LinkedInUrls.profileSlugOrNull(url) != null;
    }
}
