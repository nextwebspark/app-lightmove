package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.ProfileClaim;
import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidatePipelineResponse;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.dto.MapPeopleToPositionResponse;
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
import app.lightmove.api.candidate.model.CandidateProfile;
import app.lightmove.api.candidate.model.ContactEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.ResearchedCandidate;
import app.lightmove.api.candidate.model.ResearchedEmployer;
import app.lightmove.api.candidate.model.ResearchedFiling;
import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;
import app.lightmove.api.candidate.model.NationalityReading;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonPhoto;
import app.lightmove.api.candidate.model.StoredPhoto;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonPhotoRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.core.text.service.LikePatterns;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.service.CustomColumnService;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * <p>Every executive is a workspace {@link Person} before they are anybody's candidate. Adding one
 * first asks whether the workspace already knows them ({@link PersonMatcher}); if it does, the mandate
 * maps that person and what it brings only fills in what nobody recorded, so a second mandate never
 * starts a second history. Every change leaves a line in the person's timeline, in the same
 * transaction ({@link PersonActivityRecorder}).
 *
 * <p>The one other decision here that is not bookkeeping is where a candidate sits. Naming one of the
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
            Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "person.fullName"));

    private final CandidateRepository candidates;
    private final PersonRepository people;
    private final PersonMatcher matcher;
    private final PersonActivityRecorder activity;
    private final PersonNoteService personNotes;
    private final PersonPhotoRepository photos;
    private final ProjectRepository projects;
    private final TriageCompanyService triage;
    private final CustomColumnService customColumns;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ProjectStreamPublisher stream;
    private final CandidateRequestReader requests;
    private final CandidateResponseMapper responses;
    private final CompanyListSettings listConfig;

    public CandidateService(CandidateRepository candidates, PersonRepository people, PersonMatcher matcher,
                            PersonActivityRecorder activity, PersonNoteService personNotes,
                            PersonPhotoRepository photos,
                            ProjectRepository projects, TriageCompanyService triage,
                            CustomColumnService customColumns, AuditService audit,
                            ApplicationEventPublisher events, ProjectStreamPublisher stream,
                            CandidateRequestReader requests, CandidateResponseMapper responses,
                            LightMoveProperties properties) {
        this.candidates = candidates;
        this.people = people;
        this.matcher = matcher;
        this.activity = activity;
        this.personNotes = personNotes;
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

    /**
     * The position's Candidates page: the mandate's executives, searched on name, title and employer,
     * at one status or all, with each status counted under the same search.
     */
    @Transactional(readOnly = true)
    public CandidatePipelineResponse pipeline(UUID workspaceId, UUID projectId, String query, String statusToken,
                                              Integer requestedPage, Integer requestedSize) {
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? listConfig.defaultPageSize() : requestedSize;
        listConfig.requireValidPage(page, size);
        projects.requireInWorkspace(projectId, workspaceId);

        List<CandidateStatus> statuses = statusToken == null || statusToken.isBlank()
                ? List.of(CandidateStatus.values())
                : List.of(ApiValueEnum.require(CandidateStatus.class, statusToken, "candidate status"));
        String like = "%" + LikePatterns.escape(query == null ? "" : query.trim().toLowerCase(Locale.ROOT)) + "%";

        Page<Candidate> found = candidates.findPipelinePage(projectId, statuses, like,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
        Map<String, Long> counts = new LinkedHashMap<>();
        candidates.countPipelineByStatus(projectId, like)
                .forEach(count -> counts.put(count.getStatus().value(), count.getTotal()));
        return new CandidatePipelineResponse(found.getContent().stream().map(responses::toDto).toList(),
                counts, found.getTotalElements(), page, size);
    }

    /** The most rows one page of a mandate's people may hold, and so the most ids a read beside it may name. */
    public int maxPageSize() {
        return listConfig.maxPageSize();
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
                ? candidates.findByProjectIdAndTriageCompanyIdIsNullAndPersonFullNameIgnoreCase(projectId, fullName.trim())
                : candidates.findByProjectIdAndTriageCompanyIdAndPersonFullNameIgnoreCase(projectId, triageCompanyId, fullName.trim());
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

        Optional<Person> known = personToFile(workspaceId, source, request, details);
        Filed filed = file(userId, workspaceId, projectId, request.triageCompanyId(), source, details, known);
        Candidate candidate = filed.candidate();
        candidate.describeCustomFields(customColumns.applyTo(projectId, CustomColumnTarget.CANDIDATE,
                candidate.getCustomFields(), request.customFields()));

        // A capture of someone already researched costs no second vendor call: the person's profile is
        // the research. The mandate still wants them scored against its own brief.
        if (source == CandidateSource.EXTENSION && isLinkedInProfileUrl(details.linkedinUrl())) {
            events.publishEvent(candidate.getPerson().isResearched()
                    ? new CandidateAiEnrichRequested(candidate.getId(), projectId, workspaceId, userId,
                            AiEnrichTrigger.CAPTURE)
                    : new CandidateCapturedEvent(candidate.getId(), projectId, details.linkedinUrl()));
        }
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_CAPTURED);

        audit.projectEvent(ProjectEventType.CANDIDATE_ADDED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidate.getId().toString())
                .detail("source", source.name())
                .detail("mappedExisting", filed.personWasKnown())
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
        Person person = candidate.getPerson();

        CandidateDetails details = requests.detailsOf(projectId, request, candidate.getTriageCompanyId());
        refuseDuplicate(projectId, request.triageCompanyId(), details.fullName(), candidateId);
        refuseHeldProfile(projectId, details.linkedinUrl(), candidateId);
        ProfileClaim claim = matcher.claimOf(workspaceId, details.linkedinUrl(), person);
        if (claim == ProfileClaim.HELD) {
            throw ApiException.of(ErrorCode.PERSON_PROFILE_HELD);
        }
        refuseRetypedCapturedProfile(person, details.linkedinUrl());

        boolean confirmBackground = Boolean.TRUE.equals(request.confirmBackground());
        candidate.remapTo(request.triageCompanyId());
        candidate.describe(details);
        // The person is the workspace's: an edit made through this mandate is what every other mandate
        // mapping them now reads.
        person.describe(details, door);
        if (claim == ProfileClaim.SHARED) {
            person.yieldProfileKey();
        }
        if (confirmBackground) {
            person.confirmBackground();
        }
        requests.refuseOverfullChannels(person);
        candidate.describeCustomFields(customColumns.applyTo(projectId, CustomColumnTarget.CANDIDATE,
                candidate.getCustomFields(), request.customFields()));
        activity.record(candidate, userId, PersonActivityKind.PROFILE_EDITED, PersonActivityDetails
                .of("door", door.name()).and("backgroundConfirmed", confirmBackground));
        // A note sent with an edit (a re-imported sheet's Note column) is filed as a note, once.
        personNotes.fileFromDoor(candidate, userId, details.note());

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

        CandidateStatus from = candidate.getStatus();
        CandidateStatus to = requests.resolveStatus(request.status());
        candidate.moveTo(to);
        if (from != to) {
            activity.record(candidate, userId, PersonActivityKind.STATUS_CHANGED,
                    PersonActivityDetails.of("from", from.value()).and("to", to.value()));
        }

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
            Person person = candidate.getPerson();
            boolean fresh = !person.isResearched();
            // Two mandates capturing one new person before either's research lands each queue a call;
            // the second finds the person researched, but its own mandate still wants the employer
            // filed and the person scored. Only a row with both done has nothing left to do.
            if (!fresh && candidate.getAiAssessment() != null) {
                return;
            }
            if (fresh) {
                person.enrich(enriched);
                keepPhoto(person.getId(), enriched);
                activity.record(candidate, candidate.getAddedBy(), PersonActivityKind.RESEARCHED,
                        PersonActivityDetails.of("vendor", enriched.vendor()));
            }
            candidate.adoptEmployer(enriched.employerName());
            mapToEmployer(projectId, candidate, enriched);
            stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
            projects.findById(projectId).ifPresent(project -> events.publishEvent(
                    new CandidateAiEnrichRequested(candidateId, projectId, project.getWorkspaceId(),
                            candidate.getAddedBy(), AiEnrichTrigger.CAPTURE)));
        }, () -> log.info("Candidate {} was removed before its research landed", candidateId));
    }

    /**
     * A vendor search hit, filed and researched in one write — a Find executives pick or a People search
     * tick: the hit is the profile, so there is no vendor call to wait for and no
     * {@code CandidateCapturedEvent}. The deep enrichment is queued only where {@code filing} asks.
     *
     * <p>The employer a filing names is captured in the same transaction, so a person refused by the
     * duplicate guards — {@code CANDIDATE_ALREADY_MAPPED}, which the caller counts as a skip — or failing
     * any other way leaves no company behind at the stage it was filed for.
     *
     * <p>{@code REQUIRES_NEW} for {@link #applyResearch}'s reason: the run's worker calls this per person
     * from an {@code AFTER_COMMIT} callback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResearchedCandidate addResearched(UUID userId, UUID workspaceId, UUID projectId,
                                             SaveCandidateRequest request, EnrichedProfile research,
                                             ResearchedFiling filing) {
        projects.requireInWorkspace(projectId, workspaceId);
        CandidateDetails details = requests.detailsOf(projectId, request, null);
        UUID triageCompanyId = request.triageCompanyId();
        TriageCompanyResponse employer = null;
        if (filing.employer() != null && triageCompanyId == null) {
            ResearchedEmployer named = filing.employer();
            employer = triage.captureFromResearch(projectId, userId, named.details(), named.source(), named.stage());
            triageCompanyId = employer.id();
            details = details.employedAt(employer.companyName());
        }

        refuseDuplicate(projectId, triageCompanyId, details.fullName(), null);
        refuseHeldProfile(projectId, details.linkedinUrl(), null);

        Filed filed = file(userId, workspaceId, projectId, triageCompanyId, filing.source(), details);
        Candidate candidate = filed.candidate();
        Person person = candidate.getPerson();
        if (!person.isResearched()) {
            person.enrich(research);
            keepPhoto(person.getId(), research);
            activity.record(candidate, userId, PersonActivityKind.RESEARCHED,
                    PersonActivityDetails.of("vendor", research.vendor()).and("runId", filing.runId()));
        }
        candidate.adoptEmployer(research.employerName());
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_CAPTURED);
        if (filing.enrichTrigger() != null) {
            events.publishEvent(new CandidateAiEnrichRequested(candidate.getId(), projectId, workspaceId,
                    userId, filing.enrichTrigger()));
        }

        audit.event(ProjectEventType.CANDIDATE_ADDED)
                .actor(userId).workspace(workspaceId).target(AuditService.PROJECT_TARGET, projectId)
                .detail("candidateId", candidate.getId().toString())
                .detail("source", filing.source().name())
                .detailIfPresent("runId", filing.runId() == null ? null : filing.runId().toString())
                .detail("mappedExisting", filed.personWasKnown())
                .record();
        return new ResearchedCandidate(responses.toDto(candidate), employer);
    }

    /**
     * The LinkedIn slugs of everyone the mandate already maps — what a sourcing run drops from a
     * vendor's hits before filing them, so nobody already held is filed twice.
     */
    /** As {@link #mappedProfileSlugsOf}, each slug to the executive filed under it. */
    @Transactional(readOnly = true)
    public Map<String, UUID> mappedProfileCandidatesOf(UUID workspaceId, UUID projectId) {
        projects.requireInWorkspace(projectId, workspaceId);
        Map<String, UUID> bySlug = new HashMap<>();
        candidates.findMappedProfilesByProjectId(projectId).forEach(mapped -> {
            String slug = LinkedInUrls.profileSlugOrNull(mapped.linkedinUrl());
            if (slug != null) {
                bySlug.putIfAbsent(slug, mapped.candidateId());
            }
        });
        return bySlug;
    }

    @Transactional(readOnly = true)
    public Set<String> mappedProfileSlugsOf(UUID workspaceId, UUID projectId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return candidates.findLinkedinUrlsByProjectId(projectId).stream()
                .map(LinkedInUrls::profileSlugOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** The mandate's triaged companies with at least one executive mapped at them. */
    @Transactional(readOnly = true)
    public Set<UUID> companiesWithExecutivesOf(UUID workspaceId, UUID projectId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return candidates.findTriageCompanyIdsByProjectId(projectId);
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
            Person person = candidate.getPerson();
            CandidateProfile profile = person.getProfile();
            return new CandidateDossier(person.getFullName(), person.getTitle(),
                    candidate.getCompanyName(), person.getLocationCity(), person.getLocationCountry(),
                    person.getLinkedinUrl(), person.getSummary(), profile.career(),
                    profile.education(), profile.skills(), profile.languages(),
                    person.missingBackground());
        });
    }

    /**
     * The last AI assessment, nationality reading and failed run, staff-only — none is carried on
     * {@link CandidateResponse}. Empty when the candidate has never been enriched.
     */
    @Transactional(readOnly = true)
    public Optional<CandidateAiEnrichState> aiAssessmentOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        NationalityReading reading = candidate.getPerson().getAiNationalityReading();
        if (candidate.getAiAssessment() == null && reading == null && candidate.getAiEnrichFailedAt() == null) {
            return Optional.empty();
        }
        return Optional.of(new CandidateAiEnrichState(candidate.getAiAssessment(), reading,
                candidate.getAiEnrichFailedAt()));
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
     * The AI enrichment's own write: background into whichever fields are still empty, the assessment
     * and the nationality reading each replaced whole. A run whose assessment failed is stamped as
     * failed even when its nationality reading landed. {@code REQUIRES_NEW} for {@link #applyResearch}'s reason; a racing
     * drawer edit wins by {@code @Version} the same way. The background and the nationality reading are
     * the person's; the assessment is against this mandate's brief and stays on its row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyAiEnrichment(UUID projectId, UUID candidateId, UUID requestedBy,
                                  CandidateAiEnrichment enrichment) {
        candidates.findByIdAndProjectId(candidateId, projectId).ifPresent(candidate -> {
            Person person = candidate.getPerson();
            if (enrichment.isAssessed()) {
                person.proposeBackground(enrichment.background());
                candidate.recordAiAssessment(enrichment.assessment());
                activity.record(candidate, requestedBy, PersonActivityKind.AI_ASSESSED);
            } else {
                candidate.recordAiEnrichFailure();
            }
            if (enrichment.nationalityReading() != null) {
                person.recordNationalityReading(enrichment.nationalityReading());
            }
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
        Person person = candidate.getPerson();
        person.replaceContacts(ContactChannel.EMAIL, emails, ContactSource.MANUAL);
        person.replaceContacts(ContactChannel.PHONE, phones, ContactSource.MANUAL);
        activity.record(candidate, userId, PersonActivityKind.CONTACTS_EDITED,
                PersonActivityDetails.of("emails", emails.size()).and("phones", phones.size()));
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
    private static void refuseRetypedCapturedProfile(Person person, String linkedinUrl) {
        if (person.isLinkedinUrlLocked() && !Objects.equals(person.getLinkedinUrl(), linkedinUrl)) {
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
        Person person = candidate.getPerson();
        return new CandidateContactState(person.getLinkedinUrl(),
                person.hasAskedForEmails(), person.hasAskedForPhones(),
                person.hasFoundEmails(), person.hasFoundPhones(), person.isDoNotContact(),
                responses.toDto(candidate));
    }

    /**
     * The short transactional tail of an email lookup, {@code applyResearch}'s shape without its
     * {@code REQUIRES_NEW}: this is called from a request thread with no transaction bound.
     *
     * <p>The guard is re-checked here rather than only before the vendor call, so two presses racing
     * each other leave the first answer standing instead of a second write of the same values. The
     * lookup is the person's, so an answer bought through one mandate is held on every other.
     */
    @Transactional
    public CandidateResponse applyFoundEmails(UUID userId, UUID projectId, UUID candidateId, FoundEmails found) {
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        Person person = candidate.getPerson();
        if (person.hasAskedForEmails()) {
            return responses.toDto(candidate);
        }
        person.recordFoundEmails(found);
        activity.record(candidate, userId, PersonActivityKind.CONTACT_FOUND,
                PersonActivityDetails.of("channel", ContactChannel.EMAIL)
                        .and("found", found.emails().size()).and("via", found.source()));
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        return responses.toDto(candidate);
    }

    /** The phone half of {@link #applyFoundEmails}. */
    @Transactional
    public CandidateResponse applyFoundPhones(UUID userId, UUID projectId, UUID candidateId, FoundPhones found) {
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        Person person = candidate.getPerson();
        if (person.hasAskedForPhones()) {
            return responses.toDto(candidate);
        }
        person.recordFoundPhones(found);
        activity.record(candidate, userId, PersonActivityKind.CONTACT_FOUND,
                PersonActivityDetails.of("channel", ContactChannel.PHONE)
                        .and("found", found.phones().size()).and("via", found.source()));
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        return responses.toDto(candidate);
    }

    /**
     * An unmapped candidate whose research names an employer gets that company filed into the
     * mandate's universe and is mapped to it. Skipped when the mandate already maps someone of the
     * same name at that company — the scope {@link #refuseDuplicate} holds a mandate to.
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
                .findByProjectIdAndTriageCompanyIdAndPersonFullNameIgnoreCase(
                        projectId, company.id(), candidate.getPerson().getFullName())
                .stream()
                .anyMatch(other -> !other.getId().equals(candidate.getId()));
        if (nameHeldThere) {
            log.info("Leaving candidate {} unmapped — {} already maps that name", candidate.getId(),
                    company.companyName());
            return;
        }
        candidate.employBy(company.id(), company.companyName());
    }

    private void keepPhoto(UUID personId, EnrichedProfile enriched) {
        if (enriched.photo() == null || photos.existsByPersonId(personId)) {
            return;
        }
        photos.save(PersonPhoto.of(personId, enriched.photo()));
    }

    /** The stored profile photo, or NOT_FOUND — "no photo" and "no such candidate" read the same. */
    @Transactional(readOnly = true)
    public StoredPhoto photoOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        // Existence, not content: a grid of avatars asks this per row, and loading each whole row —
        // profile jsonb included — to throw it away is a scan the scoping does not need.
        UUID personId = candidates.findPersonIdByIdAndProjectId(candidateId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return photos.findByPersonId(personId)
                .map(photo -> new StoredPhoto(photo.getContent(), photo.getContentType()))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public void remove(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                       HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);

        // The person stays with the workspace — their notes, contacts and history are the point of
        // keeping them — and the line saying which mandate let them go is written before the row goes.
        activity.record(candidate, userId, PersonActivityKind.UNMAPPED);
        candidates.delete(candidate);

        // The name is recorded because the row carrying it is about to stop existing, and an audit
        // entry naming only an unresolvable id answers no question later.
        audit.projectEvent(ProjectEventType.CANDIDATE_REMOVED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("fullName", candidate.getPerson().getFullName())
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
            return candidates.findByProjectIdAndTriageCompanyIdInAndPersonFullNameContainingIgnoreCase(
                    projectId, companyIds, nameQuery, pageRequest);
        }
        if (Boolean.TRUE.equals(criteria.unmapped())) {
            return candidates.findByProjectIdAndTriageCompanyIdIsNullAndPersonFullNameContainingIgnoreCase(
                    projectId, nameQuery, pageRequest);
        }
        return candidates.findByProjectIdAndPersonFullNameContainingIgnoreCase(
                projectId, nameQuery, pageRequest);
    }

    /**
     * Refuses a name the mandate already maps, in the two scopes V36's partial unique indexes drew:
     * at the company where there is one, across the mandate where there is not. Since V91 the name is
     * the person's, so this is the only place the rule is held.
     * {@code selfId} excludes the row being edited so a save without a rename does not collide.
     *
     * <p>Both finders carry the project id, including the one that already names a company. Scoping by
     * the company alone would be safe only by the order of the statements above, and a reorder would
     * turn this 409 into an oracle confirming another workspace's company id and a name mapped at it.
     */
    private void refuseDuplicate(UUID projectId, UUID triageCompanyId, String fullName, UUID selfId) {
        List<Candidate> sameName = triageCompanyId == null
                ? candidates.findByProjectIdAndTriageCompanyIdIsNullAndPersonFullNameIgnoreCase(projectId, fullName)
                : candidates.findByProjectIdAndTriageCompanyIdAndPersonFullNameIgnoreCase(
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
                .anyMatch(other -> slug.equals(LinkedInUrls.profileSlugOrNull(other.getPerson().getLinkedinUrl())));
        if (held) {
            throw ApiException.of(ErrorCode.CANDIDATE_ALREADY_MAPPED);
        }
    }

    /**
     * Files an executive on this mandate as a workspace person: the one the workspace already knows
     * them as, whose empty fields this filing fills, or a new one it founds. A person this mandate
     * already holds is refused as {@code CANDIDATE_ALREADY_MAPPED}, the answer every door already
     * handles — V91's (project, person) index is the backstop a race would hit.
     */
    private Filed file(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                       CandidateSource source, CandidateDetails details) {
        return file(userId, workspaceId, projectId, triageCompanyId, source, details,
                matcher.find(workspaceId, details));
    }

    private Filed file(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                       CandidateSource source, CandidateDetails details, Optional<Person> known) {
        if (known.isPresent() && candidates.existsByProjectIdAndPersonId(projectId, known.get().getId())) {
            throw ApiException.of(ErrorCode.CANDIDATE_ALREADY_MAPPED);
        }
        Person person = known.orElseGet(() -> people.save(Person.founded(workspaceId, userId, source, details)));
        if (known.isPresent()) {
            person.fillFrom(details, ContactSource.ofDoor(source));
        }
        if (source == CandidateSource.EXTENSION) {
            person.lockProfileUrl(details.linkedinUrl());
        }
        requests.refuseOverfullChannels(person);
        Candidate candidate = candidates.save(Candidate.mapped(projectId, userId, triageCompanyId, person,
                source, details));
        activity.record(candidate, userId,
                known.isPresent() ? PersonActivityKind.MAPPED : PersonActivityKind.ADDED_TO_POOL,
                PersonActivityDetails.of("door", source));
        personNotes.fileFromDoor(candidate, userId, details.note());
        return new Filed(candidate, known.isPresent());
    }

    /**
     * Who a hand-typed add files as. A name alone never matches (see {@link PersonMatcher}), so where the
     * keys find nobody but the workspace holds someone of that name at that employer, the drawer is asked
     * first; its answer comes back as {@code existingPersonId} or {@code addAsNewPerson}. Every other door
     * is a capture, a file or a run, which nobody is there to ask and which carries keys of its own.
     */
    private Optional<Person> personToFile(UUID workspaceId, CandidateSource source, SaveCandidateRequest request,
                                          CandidateDetails details) {
        Optional<Person> known = matcher.find(workspaceId, details);
        if (source != CandidateSource.MANUAL) {
            return known;
        }
        if (request.existingPersonId() != null) {
            return Optional.of(answeredPerson(workspaceId, request.existingPersonId(), known, details));
        }
        if (known.isEmpty() && !Boolean.TRUE.equals(request.addAsNewPerson())) {
            List<UUID> namesakes = matcher.possibleDuplicates(workspaceId, details).stream()
                    .map(Person::getId)
                    .toList();
            if (!namesakes.isEmpty()) {
                throw ApiException.withProperty(ErrorCode.CANDIDATE_POSSIBLE_DUPLICATE, "personIds", namesakes);
            }
        }
        return known;
    }

    /**
     * The person the dialog's "add them here" named, held to what the dialog was asked about. The keys
     * still decide first: filing someone else's LinkedIn profile or email onto the chosen person would give
     * two people one key. Without a key match, only a namesake the 409 could have named is taken — any
     * other id would map a stranger under a typed name the mandate's own name rule never saw.
     */
    private Person answeredPerson(UUID workspaceId, UUID existingPersonId, Optional<Person> known,
                                  CandidateDetails details) {
        if (known.isPresent()) {
            if (!known.get().getId().equals(existingPersonId)) {
                throw ApiException.of(ErrorCode.CANDIDATE_KEYS_NAME_ANOTHER);
            }
            return known.get();
        }
        return matcher.possibleDuplicates(workspaceId, details).stream()
                .filter(namesake -> namesake.getId().equals(existingPersonId))
                .findFirst()
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /**
     * Adds people the workspace already holds to a mandate, as Identified. Someone it already holds stays
     * as they are. The caller has checked they may work the mandate.
     */
    @Transactional
    public MapPeopleToPositionResponse mapFromPool(UUID userId, UUID workspaceId, UUID projectId,
                                                   List<Person> persons, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Map<UUID, List<Candidate>> mappedOldestFirst = candidates
                .findPositionsOfPeople(workspaceId, persons.stream().map(Person::getId).toList()).stream()
                .collect(Collectors.groupingBy(row -> row.getPerson().getId()));
        List<UUID> added = new ArrayList<>();
        for (Person person : persons) {
            List<Candidate> rows = mappedOldestFirst.getOrDefault(person.getId(), List.of());
            if (rows.stream().anyMatch(row -> row.getProjectId().equals(projectId))) {
                continue;
            }
            Candidate candidate = candidates.save(Candidate.mappedFromPool(projectId, userId, person,
                    PersonRecordService.employerOf(person, rows).name()));
            activity.record(candidate, userId, PersonActivityKind.MAPPED,
                    PersonActivityDetails.of("door", CandidateSource.MANUAL));
            added.add(person.getId());
        }
        if (!added.isEmpty()) {
            stream.publish(projectId, ProjectStreamKind.CANDIDATE_CAPTURED);
            audit.projectEvent(ProjectEventType.PEOPLE_MAPPED_FROM_POOL, userId, workspaceId, projectId, httpRequest)
                    .detail("personIds", added.stream().map(UUID::toString).toList())
                    .record();
        }
        return new MapPeopleToPositionResponse(added.size(), persons.size() - added.size());
    }

    /** A filing's row, and whether it mapped someone the workspace already knew. */
    private record Filed(Candidate candidate, boolean personWasKnown) {}

    /**
     * Only a page the plugin actually read is worth a billed call, and "worth billing" is exactly "a
     * slug came back" — the providers key on the slug, so gate and lookup must agree.
     */
    private static boolean isLinkedInProfileUrl(String url) {
        return LinkedInUrls.profileSlugOrNull(url) != null;
    }
}
