package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.constant.AiRunScope;
import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.ProfileClaim;
import app.lightmove.api.candidate.dto.AddedByResponse;
import app.lightmove.api.candidate.dto.CandidateExitReasonResponse;
import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.dto.ChangeExitReasonRequest;
import app.lightmove.api.candidate.dto.HeldCandidateResponse;
import app.lightmove.api.candidate.dto.MapPeopleToPositionResponse;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.dto.UpdateCandidateContactsRequest;
import app.lightmove.api.candidate.dto.UpdateCandidateStatusRequest;
import app.lightmove.api.candidate.dto.UpdateCandidateTriageStatusRequest;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateAiEnrichRequested;
import app.lightmove.api.candidate.model.CandidateAttribution;
import app.lightmove.api.candidate.model.CandidateCapturedEvent;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidateContactState;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateProfile;
import app.lightmove.api.candidate.model.CandidateSearchMatches;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.ContactEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.ExitReason;
import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.candidate.model.NationalityReading;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonPhoto;
import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.candidate.model.ResearchedCandidate;
import app.lightmove.api.candidate.model.ResearchedEmployer;
import app.lightmove.api.candidate.model.ResearchedFiling;
import app.lightmove.api.candidate.model.StoredPhoto;
import app.lightmove.api.candidate.repository.CandidateListFilter;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonPhotoRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.common.location.model.InternationalPhone;
import app.lightmove.api.common.location.service.PhoneNumbers;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.constant.ApiValueEnum;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.core.text.model.SearchText;
import app.lightmove.api.core.text.service.LikePatterns;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.service.CustomColumnService;
import app.lightmove.api.project.service.ProjectLookup;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.model.FiledCompany;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import app.lightmove.api.workspace.model.WorkspaceAiControls;
import app.lightmove.api.workspace.service.WorkspaceAiControlsReader;
import app.lightmove.api.workspace.service.WorkspaceSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A mandate's mapped executives. Every executive is a workspace {@link Person} first: a match ({@link PersonMatcher})
 * is mapped, not duplicated. Naming a triaged company snapshots its name and ignores {@code employerName}, so the two
 * never drift.
 */
@Service
@Slf4j
@NamedInterface(name = "api", propagate = false)
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
    private final ProjectLookup projects;
    private final TriageCompanyService triage;
    private final CustomColumnService customColumns;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ProjectStreamPublisher stream;
    private final CandidateRequestReader requests;
    private final CandidateResponseMapper responses;
    private final CompanyListSettings listConfig;
    private final CandidatePipelineCatalog pipeline;
    private final CandidateExitReasons exitReasons;
    private final PersonEmployerResolver employerResolver;
    private final UserRepository users;
    private final CandidateSearchService searches;
    private final CandidateRemoval removal;
    private final SuppressionGate suppression;
    private final WorkspaceAiControlsReader aiControls;
    private final WorkspaceSettingsService workspaceSettings;
    private final LookedUpResearch lookedUp;
    private final CorrectedProfileResearch correctedProfiles;

    public CandidateService(CandidateRepository candidates, PersonRepository people, PersonMatcher matcher,
                            PersonActivityRecorder activity, PersonNoteService personNotes,
                            PersonPhotoRepository photos,
                            ProjectLookup projects, TriageCompanyService triage,
                            CustomColumnService customColumns, AuditService audit,
                            ApplicationEventPublisher events, ProjectStreamPublisher stream,
                            CandidateRequestReader requests, CandidateResponseMapper responses,
                            CandidatePipelineCatalog pipeline, CandidateExitReasons exitReasons,
                            PersonEmployerResolver employerResolver,
                            UserRepository users, CandidateSearchService searches,
                            CandidateRemoval removal, SuppressionGate suppression,
                            WorkspaceAiControlsReader aiControls, WorkspaceSettingsService workspaceSettings,
                            LookedUpResearch lookedUp, CorrectedProfileResearch correctedProfiles,
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
        this.pipeline = pipeline;
        this.exitReasons = exitReasons;
        this.employerResolver = employerResolver;
        this.users = users;
        this.searches = searches;
        this.removal = removal;
        this.suppression = suppression;
        this.aiControls = aiControls;
        this.workspaceSettings = workspaceSettings;
        this.lookedUp = lookedUp;
        this.correctedProfiles = correctedProfiles;
    }

    /**
     * One page of people; an empty {@code triageCompanyIds} is answered without a query. A company filter with no size
     * gets the ceiling, not the default (the SPA once sent exactly {@code maxPageSize}, so lowering it would 400 every
     * Companies page); an explicit oversized size is refused.
     */
    @Transactional(readOnly = true)
    public CandidatesResponse list(UUID workspaceId, UUID projectId, CandidateListCriteria criteria,
                                   ProjectAudience audience) {
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

        if (criteria.withinTriageCompanyIds() != null && criteria.withinTriageCompanyIds().isEmpty()) {
            return new CandidatesResponse(List.of(), 0, page, size);
        }

        PageRequest pageRequest = PageRequest.of(page, size, FIRST_MAPPED_FIRST);
        String nameQuery = criteria.nameQuery() == null ? "" : criteria.nameQuery().trim();
        String textQuery = criteria.textQuery() == null ? "" : criteria.textQuery().trim();
        List<UUID> statusIds = statusIdsOf(workspaceId, criteria.status(), criteria.pipelineStage());
        if (statusIds != null && statusIds.isEmpty()) {
            return new CandidatesResponse(List.of(), 0, page, size);
        }
        TriageCompanyStatus triageStatus = criteria.triageStatus() == null || criteria.triageStatus().isBlank()
                ? null : TriageCompanyStatus.parseOrInUniverse(criteria.triageStatus());
        SearchText search = SearchText.ofOrNull(criteria.search());
        CandidateSearchMatches searched = search == null ? null
                : searches.matchesOf(workspaceId, projectId, search, audience);
        Page<Candidate> found = candidates.findAll(CandidateListFilter.of(projectId, audience.clientVisibleOnly(),
                statusIds, companyIds,
                Boolean.TRUE.equals(criteria.unmapped()), nameQuery, textQuery, criteria.withinTriageCompanyIds(),
                triageStatus, searched), pageRequest);

        return new CandidatesResponse(
                responses.toDtos(found.getContent()),
                found.getTotalElements(), page, size);
    }

    /**
     * The statuses a list filter names: a status key, a stage key, or both (their overlap). Null when
     * neither is asked; an unknown key is a 400 rather than an empty answer, as an unknown token always was.
     */
    private List<UUID> statusIdsOf(UUID workspaceId, String statusKey, String stageKey) {
        boolean byStatus = statusKey != null && !statusKey.isBlank();
        boolean byStage = stageKey != null && !stageKey.isBlank();
        if (!byStatus && !byStage) {
            return null;
        }
        PipelineCatalog catalog = pipeline.catalogOf(workspaceId);
        List<UUID> ids = null;
        if (byStatus) {
            ids = List.of(catalog.byKey(statusKey.strip())
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown status: " + statusKey))
                    .getId());
        }
        if (byStage) {
            List<UUID> ofStage = catalog.stageByKey(stageKey.strip())
                    .map(stage -> catalog.statusIdsOfStage(stage.getStageKey()))
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Unknown pipeline stage: " + stageKey));
            ids = ids == null ? ofStage : ids.stream().filter(ofStage::contains).toList();
        }
        return ids;
    }

    /**
     * A mandate's executives at each status in pipeline order — every live status present, zero included,
     * and a retired one only while somebody still holds it: one grouped count.
     */
    @Transactional(readOnly = true)
    public Map<CandidateStatusDefinition, Long> statusCountsOf(UUID workspaceId, UUID projectId,
                                                               ProjectAudience audience) {
        projects.requireInWorkspace(projectId, workspaceId);
        Map<UUID, Long> held = new HashMap<>();
        candidates.countByStatusOfProject(projectId, audience.clientVisibleOnly())
                .forEach(row -> held.put(row.getStatusId(), row.getTotal()));
        Map<CandidateStatusDefinition, Long> counts = new LinkedHashMap<>();
        for (CandidateStatusDefinition status : pipeline.catalogOf(workspaceId).statuses()) {
            long total = held.getOrDefault(status.getId(), 0L);
            if (!status.isRetired() || total > 0) {
                counts.put(status, total);
            }
        }
        return counts;
    }

    /** An executive hidden from the client is a 404 to a client seat, the answer a foreign id gets. */
    @Transactional(readOnly = true)
    public CandidateResponse get(UUID workspaceId, UUID projectId, UUID candidateId, ProjectAudience audience) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        if (!candidate.isClientVisible() && audience.clientVisibleOnly()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return responses.toDto(candidate);
    }

    /**
     * The executive this position already maps at {@code linkedinUrl}'s profile, if any: what the plugin asks
     * before it offers to file someone. Settled on the slug as {@link #refuseHeldProfile} is, so it finds the row
     * a save from the same page would collide with.
     */
    @Transactional(readOnly = true)
    public Optional<HeldCandidateResponse> heldAt(UUID workspaceId, UUID projectId, String linkedinUrl) {
        projects.requireInWorkspace(projectId, workspaceId);
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        if (slug == null) {
            return Optional.empty();
        }
        return candidatesAtProfile(projectId, slug).stream()
                .min(Comparator.comparing(Candidate::getCreatedAt))
                .map(held -> new HeldCandidateResponse(responses.toDto(held), adderOf(projectId, held.getId()),
                        employerResolver.logoOfMapping(workspaceId, held)));
    }

    /**
     * Every mapped executive, unpaged, for {@code talentmap}: capped at the caller's limit with {@code totalCount} saying
     * so, and sorted so the cut at the cap is deterministic.
     */
    @Transactional(readOnly = true)
    public CandidatesResponse listAllOfProject(UUID workspaceId, UUID projectId, int cap,
                                               ProjectAudience audience) {
        projects.requireInWorkspace(projectId, workspaceId);
        PageRequest wholePosition = PageRequest.of(0, cap, FIRST_MAPPED_FIRST);
        Page<Candidate> found = audience.clientVisibleOnly()
                ? candidates.findByProjectIdAndClientVisibleTrue(projectId, wholePosition)
                : candidates.findByProjectId(projectId, wholePosition);
        return new CandidatesResponse(
                responses.toDtos(found.getContent()),
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

    /** Who filed each of {@code found}'s executives, for a staff read of the Companies grid. */
    @Transactional(readOnly = true)
    public CandidatesResponse withAddedBy(UUID workspaceId, UUID projectId, CandidatesResponse found) {
        projects.requireInWorkspace(projectId, workspaceId);
        List<UUID> ids = found.candidates().stream().map(CandidateResponse::id).toList();
        if (ids.isEmpty()) {
            return found.withAddedBy(Map.of());
        }
        Map<UUID, UUID> adderOf = candidates.findAttributionByProjectIdAndIdIn(projectId, ids).stream()
                .filter(attribution -> attribution.getAddedBy() != null)
                .collect(Collectors.toMap(CandidateAttribution::getCandidateId, CandidateAttribution::getAddedBy));
        Map<UUID, User> adders = users.findAllById(Set.copyOf(adderOf.values())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, AddedByResponse> attribution = new HashMap<>();
        adderOf.forEach((candidateId, userId) -> {
            User adder = adders.get(userId);
            if (adder != null) {
                attribution.put(candidateId, new AddedByResponse(userId, adder.getFullName(), adder.getAvatarUrl()));
            }
        });
        return found.withAddedBy(attribution);
    }

    /**
     * The person this mandate already has for a spreadsheet row: LinkedIn profile first, then email, then name — a name
     * identifies someone only within a company (V36's index scope); oldest first when several answer. An email match
     * follows {@link PersonMatcher}'s rule: never a role inbox, and only where the names agree.
     */
    @Transactional(readOnly = true)
    public Optional<CandidateResponse> findCandidateOfProject(UUID projectId, UUID triageCompanyId, String linkedinUrl,
                                                              String email, String fullName) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        if (slug != null) {
            Optional<Candidate> byProfile = candidatesAtProfile(projectId, slug).stream()
                    .min(Comparator.comparing(Candidate::getCreatedAt));
            if (byProfile.isPresent()) {
                return byProfile.map(responses::toDto);
            }
        }
        if (email != null && !email.isBlank() && !RoleAddresses.isRoleAddress(email)) {
            Optional<Candidate> byEmail = candidates
                    .findByProjectIdAndEmailKey(projectId, CandidateContact.keyOf(ContactChannel.EMAIL, email))
                    .stream()
                    .filter(held -> PersonNames.agree(held.getPerson().getFullName(), fullName))
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

    /** LIKE narrows; the exact slug decides — "jon-smith" must not answer for "jon-smith-acwa". */
    private List<Candidate> candidatesAtProfile(UUID projectId, String slug) {
        return candidates.findByProjectIdAndProfileSlugLike(projectId, slug, LikePatterns.escape(slug),
                        LikePatterns.escape(LinkedInUrls.percentEncodedSlug(slug)))
                .stream()
                .filter(held -> slug.equals(LinkedInUrls.profileSlugOrNull(held.getPerson().getLinkedinUrl())))
                .toList();
    }

    @Transactional
    public CandidateResponse add(UUID userId, UUID workspaceId, UUID projectId,
                                 SaveCandidateRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        CandidateSource source = requests.resolveSource(request.source());
        UUID triageCompanyId = requests.companyOf(projectId, request, null);
        CandidateDetails details = requests.detailsOf(projectId, request.withTriageCompanyId(triageCompanyId), null);

        // The Add form's lookup already bought this research: the save files it, employer into the universe included.
        boolean lookedUpProfile = source == CandidateSource.MANUAL && Boolean.TRUE.equals(request.lookedUpProfile())
                && isLinkedInProfileUrl(details.linkedinUrl());
        Optional<EnrichedProfile> research = lookedUpProfile
                ? lookedUp.heldFor(workspaceId, details.linkedinUrl())
                : Optional.empty();
        Optional<TriageCompanyResponse> researchedEmployer = triageCompanyId == null && research.isPresent()
                ? lookedUp.fileEmployer(projectId, userId, research.get(), details)
                : Optional.empty();
        if (researchedEmployer.isPresent()) {
            triageCompanyId = researchedEmployer.get().id();
            details = details.employedAt(researchedEmployer.get().companyName());
        }

        refuseDuplicate(projectId, triageCompanyId, details.fullName(), details.linkedinUrl(), null);
        refuseHeldProfile(projectId, details.linkedinUrl(), null);
        suppression.refuseSuppressed(workspaceId, details.linkedinUrl(), emailsOf(details.emails()));

        CandidateStatusDefinition status = pipeline.requireOfferable(workspaceId, request.status());
        // An add straight out of process is a move like any other: a way out that offers reasons is given one.
        ExitReason reason = exitReasons.requireForMove(workspaceId, status, request.exitReasonId(),
                request.exitNote());
        Optional<Person> known = personToFile(workspaceId, source, request, details);
        Filed filed = file(userId, workspaceId, projectId, triageCompanyId, source, status, reason,
                request.exitNote(), details, known);
        Candidate candidate = filed.candidate();
        candidate.describeCustomFields(customColumns.applyTo(projectId, CustomColumnTarget.CANDIDATE,
                candidate.getCustomFields(), request.customFields()));

        // A capture of someone already researched costs no second vendor call: the person's profile is
        // the research. The mandate still wants them scored against its own brief.
        if (source == CandidateSource.EXTENSION && isLinkedInProfileUrl(details.linkedinUrl())) {
            if (candidate.getPerson().isResearched()) {
                mapToKnownEmployer(workspaceId, projectId, candidate);
                events.publishEvent(new CandidateAiEnrichRequested(candidate.getId(), projectId, workspaceId,
                        userId, AiEnrichTrigger.CAPTURE));
            } else {
                events.publishEvent(new CandidateCapturedEvent(candidate.getId(), projectId,
                        details.linkedinUrl()));
            }
        }
        if (research.isPresent()) {
            lookedUp.fileOnto(userId, workspaceId, projectId, candidate, research.get(), details.linkedinUrl());
        } else if (lookedUpProfile && !candidate.getPerson().isResearched()) {
            // Held no longer (past its window, or looked up on another instance): researched behind the save instead.
            events.publishEvent(new CandidateCapturedEvent(candidate.getId(), projectId, details.linkedinUrl()));
        }
        if (researchedEmployer.isPresent()) {
            stream.publish(projectId, ProjectStreamKind.COMPANY_CAPTURED);
        }
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_CAPTURED);

        var added = audit.projectEvent(ProjectEventType.CANDIDATE_ADDED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidate.getId().toString())
                .detail("source", source.name())
                .detail("mappedExisting", filed.personWasKnown());
        if (reason != null) {
            added.detail("exitReasonId", reason.getId().toString());
        }
        added.record();
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
        ProfileChanges changes = ProfileChanges.before(person);

        UUID triageCompanyId = requests.companyOf(projectId, request, candidate);
        CandidateDetails details = requests.detailsOf(projectId, request.withTriageCompanyId(triageCompanyId),
                candidate.getTriageCompanyId());
        refuseDuplicate(projectId, triageCompanyId, details.fullName(), details.linkedinUrl(), candidateId);
        refuseHeldProfile(projectId, details.linkedinUrl(), candidateId);
        ProfileClaim claim = matcher.claimOf(workspaceId, details.linkedinUrl(), person);
        if (claim == ProfileClaim.HELD) {
            throw ApiException.of(ErrorCode.PERSON_PROFILE_HELD);
        }
        refuseSuppressedAdditions(workspaceId, person, details.linkedinUrl(), details.emails());

        boolean confirmBackground = Boolean.TRUE.equals(request.confirmBackground());
        candidate.remapTo(triageCompanyId, triageStatusOfCompany(projectId, triageCompanyId));
        candidate.describe(details);
        // Naming the status they hold is no move: a retired one, re-checked, refused every edit of whoever held it.
        if (request.status() != null && !request.status().isBlank()
                && !request.status().strip().equals(candidate.getStatus().getStatusKey())) {
            CandidateStatusDefinition to = pipeline.requireOfferable(workspaceId, request.status());
            moveTo(candidate, userId, to,
                    exitReasons.requireForMove(workspaceId, to, request.exitReasonId(), request.exitNote()),
                    request.exitNote());
        }
        // The person is the workspace's: an edit made through this mandate is what every other mandate
        // mapping them now reads.
        boolean wasResearched = person.isResearched();
        String previousUrl = person.getLinkedinUrl();
        ContactChannelSizes contactsBefore = requests.channelSizesOf(person);
        person.describe(details, door);
        correctedProfiles.researchIfCorrected(candidate, wasResearched, previousUrl, door);
        if (claim == ProfileClaim.SHARED) {
            person.yieldProfileKey();
        }
        if (confirmBackground) {
            person.confirmBackground();
        }
        requests.refuseOverfullChannels(person, contactsBefore);
        candidate.describeCustomFields(customColumns.applyTo(projectId, CustomColumnTarget.CANDIDATE,
                candidate.getCustomFields(), request.customFields()));
        List<Map<String, String>> changed = changes.after(person);
        activity.record(candidate, userId, PersonActivityKind.PROFILE_EDITED, PersonActivityDetails
                .of("door", door.name()).and("backgroundConfirmed", confirmBackground)
                .and(ProfileChanges.DETAIL_KEY, changed.isEmpty() ? null : changed));
        // A note sent with an edit (a re-imported sheet's Note column) is filed as a note, once.
        personNotes.fileFromDoor(candidate, userId, details.note());
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_EDITED);

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

        CandidateStatusDefinition to = pipeline.requireOfferable(workspaceId, request.status());
        ExitReason reason;
        if (to.getId().equals(candidate.getStatus().getId())) {
            // A reason rides a move; one for the status already held is changed by PUT …/exit-reason instead.
            if (request.reasonId() != null) {
                throw ApiException.of(ErrorCode.EXIT_REASON_NOT_OFFERED);
            }
            reason = null;
        } else {
            reason = exitReasons.requireForMove(workspaceId, to, request.reasonId(), request.note());
        }
        moveTo(candidate, userId, to, reason, request.note());
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_EDITED);

        var event = audit.projectEvent(ProjectEventType.CANDIDATE_UPDATED, userId, workspaceId, projectId,
                        httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("status", candidate.getStatus().getStatusKey())
                .detail("statusLabel", candidate.getStatus().getLabel());
        if (reason != null) {
            event.detail("exitReasonId", reason.getId().toString());
        }
        event.record();
        return responses.toDto(candidate);
    }

    /** A different reason or note for someone already out of process: the drawer's status line, changed in place. */
    @Transactional
    public CandidateExitReasonResponse changeExitReason(UUID userId, UUID workspaceId, UUID projectId,
                                                        UUID candidateId, ChangeExitReasonRequest request,
                                                        HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        ExitReason reason = exitReasons.requireForChange(workspaceId, candidate, request.reasonId(), request.note());
        String fromReason = exitReasons.labelOf(workspaceId, candidate.getExitReasonId());
        if (candidate.giveExitReason(reason.getId(), request.note(), userId)) {
            activity.record(candidate, userId, PersonActivityKind.EXIT_REASON_CHANGED, PersonActivityDetails
                    .of("reason", reason.getLabel()).and("fromReason", fromReason)
                    .and("status", candidate.getStatus().getLabel()));
            audit.projectEvent(ProjectEventType.CANDIDATE_UPDATED, userId, workspaceId, projectId, httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("exitReasonId", reason.getId().toString())
                    .record();
        }
        return exitReasons.givenTo(workspaceId, candidate);
    }

    /** Why each of the mandate's candidates left, for those who have; staff-only. */
    @Transactional(readOnly = true)
    public List<CandidateExitReasonResponse> exitReasonsOf(UUID workspaceId, UUID projectId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return exitReasons.givenTo(workspaceId, candidates.findByProjectIdAndExitReasonIdIsNotNull(projectId));
    }

    /**
     * Moves one executive to another Companies stage (#903). Their company stays where it is, and so does
     * everyone else mapped at it: the company's own move is the one that carries them all.
     */
    @Transactional
    public CandidateResponse changeTriageStatus(UUID userId, UUID workspaceId, UUID projectId,
                                                UUID candidateId, UpdateCandidateTriageStatusRequest request,
                                                HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        TriageCompanyStatus to = ApiValueEnum.require(TriageCompanyStatus.class, request.status(), "stage");
        TriageCompanyStatus from = candidate.getTriageStatus();
        if (!candidate.moveToStage(to)) {
            return responses.toDto(candidate);
        }
        activity.record(candidate, userId, PersonActivityKind.TRIAGE_STATUS_CHANGED,
                PersonActivityDetails.triageStatusChange(from, to, false));
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_MOVED);

        audit.projectEvent(ProjectEventType.CANDIDATE_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("triageStatus", to.name())
                .record();
        return responses.toDto(candidate);
    }

    private void moveTo(Candidate candidate, UUID userId, CandidateStatusDefinition to) {
        moveTo(candidate, userId, to, null, null);
    }

    /** Every door's move: a reason, where the door asked for one, is given with it and named on its timeline line. */
    private void moveTo(Candidate candidate, UUID userId, CandidateStatusDefinition to, ExitReason reason,
                        String note) {
        CandidateStatusDefinition from = candidate.getStatus();
        if (candidate.moveTo(to)) {
            candidate.giveExitReasonIfAny(reason, note, userId);
            activity.record(candidate, userId, PersonActivityKind.STATUS_CHANGED,
                    PersonActivityDetails.statusChange(from, to)
                            .and("reason", reason == null ? null : reason.getLabel()));
        }
    }

    /**
     * The short transactional tail of an enrichment. {@code REQUIRES_NEW} because it runs from an {@code AFTER_COMMIT}
     * callback, where joining the finished transaction writes nothing; a racing drawer edit wins by {@code @Version}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyResearch(UUID projectId, UUID candidateId, EnrichedProfile enriched) {
        fileResearch(projectId, candidateId, enriched, true);
    }

    /**
     * {@link #applyResearch} with no AI background run after it: an import's enrichment box consents to LinkedIn
     * research, not to inference, which stays a press of Suggest with AI.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyResearchWithoutAi(UUID projectId, UUID candidateId, EnrichedProfile enriched) {
        fileResearch(projectId, candidateId, enriched, false);
    }

    private void fileResearch(UUID projectId, UUID candidateId, EnrichedProfile enriched, boolean requestsAi) {
        candidates.findByIdAndProjectId(candidateId, projectId).ifPresentOrElse(candidate -> {
            Person person = candidate.getPerson();
            if (person.isProcessingRestricted()) {
                log.info("Candidate {} was restricted before its research landed; it is not kept", candidateId);
                return;
            }
            boolean fresh = !person.isResearched();
            // Two mandates capturing one new person before either's research lands each queue a call;
            // the second finds the person researched, but its own mandate still wants the employer
            // filed; the background run that follows asks nothing once the first has filled it.
            if (fresh) {
                person.enrich(enriched);
                keepPhoto(person.getId(), enriched);
                activity.record(candidate, candidate.getAddedBy(), PersonActivityKind.RESEARCHED,
                        PersonActivityDetails.of("vendor", enriched.vendor()));
            }
            mapToEmployer(projectId, candidate, CapturedCompanyDetails.employerNamed(enriched.employerName(),
                    enriched.employerLinkedinUrl(), enriched.employerLogoUrl()), capturedBy(projectId, candidate));
            stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
            if (requestsAi) {
                projects.findById(projectId).ifPresent(project -> events.publishEvent(
                        new CandidateAiEnrichRequested(candidateId, projectId, project.getWorkspaceId(),
                                candidate.getAddedBy(), AiEnrichTrigger.CAPTURE)));
            }
        }, () -> log.info("Candidate {} was removed before its research landed", candidateId));
    }

    /**
     * A person captured at LinkedIn's member-id address ({@code /in/ACoAA…}) takes the vanity address research named,
     * so the next capture or lookup by that address finds them rather than founding a second person. Left alone when
     * the workspace already holds the vanity profile as somebody else: folding two people is the merge tool's.
     *
     * <p>Its own transaction, run once the research has committed: a capture founding the vanity profile between the
     * lookup and this write trips V95's unique index, and that must cost the address change, never the research.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void adoptPublicProfile(UUID projectId, UUID candidateId, EnrichedProfile enriched) {
        candidates.findByIdAndProjectId(candidateId, projectId)
                .ifPresent(candidate -> adoptPublicProfile(candidate.getPerson(), enriched));
    }

    private void adoptPublicProfile(Person person, EnrichedProfile enriched) {
        String held = LinkedInUrls.profileSlugOrNull(person.getLinkedinUrl());
        String vanity = LinkedInUrls.profileSlugOrNull(enriched.publicProfileUrl());
        if (!LinkedInUrls.isMemberIdSlug(held) || vanity == null || LinkedInUrls.isMemberIdSlug(vanity)) {
            return;
        }
        Optional<Person> holder = people.findByWorkspaceIdAndProfileSlug(person.getWorkspaceId(), vanity);
        if (holder.isPresent() && !holder.get().getId().equals(person.getId())) {
            log.info("Person {} keeps its member-id address: person {} holds the public profile", person.getId(),
                    holder.get().getId());
            return;
        }
        // Spelled as the plugin stores a captured address, so the column never holds two spellings of one profile.
        person.adoptPublicProfile(LinkedInUrls.profileUrlOf(LinkedInUrls.percentEncodedSlug(vanity)));
    }

    /**
     * A vendor search hit filed and researched in one write (no vendor call, no {@code CandidateCapturedEvent}); the
     * employer is captured in the same transaction so a refused person leaves no company behind. {@code REQUIRES_NEW}
     * for {@link #applyResearch}'s reason: called from an {@code AFTER_COMMIT} callback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResearchedCandidate addResearched(UUID userId, UUID workspaceId, UUID projectId,
                                             SaveCandidateRequest request, EnrichedProfile research,
                                             ResearchedFiling filing) {
        projects.requireInWorkspace(projectId, workspaceId);
        CandidateDetails details = requests.detailsOf(projectId, request, null);
        suppression.refuseSuppressed(workspaceId, details.linkedinUrl(), emailsOf(details.emails()));
        UUID triageCompanyId = request.triageCompanyId();
        TriageCompanyResponse employer = null;
        if (filing.employer() != null && triageCompanyId == null) {
            ResearchedEmployer named = filing.employer();
            employer = triage.captureFromResearch(projectId, userId, named.details(), named.source(), named.stage());
            triageCompanyId = employer.id();
            details = details.employedAt(employer.companyName());
        }

        refuseDuplicate(projectId, triageCompanyId, details.fullName(), details.linkedinUrl(), null);
        refuseHeldProfile(projectId, details.linkedinUrl(), null);

        CandidateStatusDefinition status = pipeline.requireOfferable(workspaceId, request.status());
        ExitReason reason = exitReasons.requireForMove(workspaceId, status, request.exitReasonId(),
                request.exitNote());
        Filed filed = file(userId, workspaceId, projectId, triageCompanyId, filing.source(), status, reason,
                request.exitNote(), details);
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

    /** {@link #dossierOf(UUID, UUID)} once the position is confirmed to be this workspace's. */
    @Transactional(readOnly = true)
    public Optional<CandidateDossier> dossierOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return dossierOf(projectId, candidateId);
    }

    /**
     * What the AI enrichment may send to the model — {@link CandidateDossier} is an allowlist, so
     * contact details and compensation never leave through here. Empty once the row is gone. Every model run on an
     * executive reads them here, so a person whose processing is restricted is refused here, for all of them.
     */
    @Transactional(readOnly = true)
    public Optional<CandidateDossier> dossierOf(UUID projectId, UUID candidateId) {
        return candidates.findByIdAndProjectId(candidateId, projectId).map(candidate -> {
            Person person = candidate.getPerson();
            if (person.isProcessingRestricted()) {
                throw ApiException.of(ErrorCode.PERSON_PROCESSING_RESTRICTED);
            }
            CandidateProfile profile = person.getProfile();
            return new CandidateDossier(person.getFullName(), person.getTitle(),
                    candidate.getCompanyName(), person.getLocationCity(), person.getLocationCountry(),
                    person.getLinkedinUrl(), person.getSummary(), profile.career(),
                    profile.education(), profile.skills(), profile.languages(),
                    person.missingBackground());
        });
    }

    /** The nationality classifier's last reading, staff-only — never carried on {@link CandidateResponse}. */
    @Transactional(readOnly = true)
    public Optional<NationalityReading> nationalityReadingOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return Optional.ofNullable(candidates.requireInProject(candidateId, projectId).getPerson()
                .getAiNationalityReading());
    }

    /** Whether the classifier has answered for this executive, so an automatic run never asks it twice. */
    @Transactional(readOnly = true)
    public boolean hasNationalityReading(UUID projectId, UUID candidateId) {
        return candidates.findByIdAndProjectId(candidateId, projectId)
                .map(candidate -> candidate.getPerson().getAiNationalityReading() != null)
                .orElse(false);
    }

    /**
     * The AI's background proposals: whichever of gender, years and seniority are still empty, and the nationality
     * reading. Both are the person's. {@code REQUIRES_NEW} for {@link #applyResearch}'s reason; a racing drawer edit
     * wins by {@code @Version}, and the caller asks again over the fresh row. The workspace's switches are read
     * again here, before the person is loaded: one turned off while the model was answering drops its half (#922).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CandidateResponse applyAiBackground(UUID workspaceId, UUID projectId, UUID candidateId, UUID requestedBy,
                                               InferredBackground background, NationalityReading reading) {
        WorkspaceAiControls controls = aiControls.aiControlsHeldForWrite(workspaceId);
        InferredBackground allowedBackground = background == null || controls.inferGender()
                ? background : background.withoutGender();
        NationalityReading allowedReading = controls.inferNationality() ? reading : null;
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        Person person = candidate.getPerson();
        boolean filled = allowedBackground != null && person.proposeBackground(allowedBackground);
        if (allowedReading != null) {
            person.recordNationalityReading(allowedReading);
        }
        if (filled || allowedReading != null) {
            activity.record(candidate, requestedBy, PersonActivityKind.AI_ASSESSED,
                    PersonActivityDetails.of("scope", AiRunScope.BACKGROUND));
            stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        }
        return responses.toDto(candidate);
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
        refuseSuppressedAdditions(workspaceId, person, null, emails);
        person.replaceContacts(ContactChannel.EMAIL, emails, ContactSource.MANUAL,
                requests.primaryOf(ContactChannel.EMAIL, request.emails()));
        person.replaceContacts(ContactChannel.PHONE, phones, ContactSource.MANUAL,
                requests.primaryOf(ContactChannel.PHONE, request.phones()));
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
     * Save as (#1337): a locally written number kept with the calling code read for it here, never one the browser
     * sends, so the row stays the provider's or the importer's with its finding time and its place in the list.
     */
    @Transactional
    public CandidateResponse savePhoneInternational(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                                    String number, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        Person person = candidate.getPerson();
        String international = PhoneNumbers
                .internationalOf(number, person.getLocationCountry(), workspaceSettings.companyCountryOf(workspaceId))
                .map(InternationalPhone::international)
                .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "That number has no country code to add"));
        if (!person.respellPhone(number, international)) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "That number is no longer on the record");
        }
        activity.record(candidate, userId, PersonActivityKind.CONTACTS_EDITED,
                PersonActivityDetails.of("emails", person.emailContacts().size())
                        .and("phones", person.phoneContacts().size()));
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);

        audit.projectEvent(ProjectEventType.CANDIDATE_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("section", "contacts")
                .record();
        return responses.toDto(candidate);
    }

    /** Asks the suppression list only for a profile or address the edit adds: what a person holds is theirs. */
    private void refuseSuppressedAdditions(UUID workspaceId, Person person, String linkedinUrl,
                                           List<ContactEntry> emails) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        String newProfile = slug != null
                && !slug.equalsIgnoreCase(LinkedInUrls.profileSlugOrNull(person.getLinkedinUrl())) ? linkedinUrl : null;
        Set<String> heldEmailKeys = person.emailContacts().stream()
                .map(CandidateContact::getValueKey)
                .collect(Collectors.toSet());
        List<String> newEmails = emailsOf(emails).stream()
                .filter(email -> !heldEmailKeys.contains(CandidateContact.keyOf(ContactChannel.EMAIL, email)))
                .toList();
        suppression.refuseSuppressed(workspaceId, newProfile, newEmails);
    }

    private static List<String> emailsOf(List<ContactEntry> emails) {
        return emails.stream().map(ContactEntry::value).filter(Objects::nonNull).toList();
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
                person.isProcessingRestricted(), responses.toDto(candidate));
    }

    /**
     * The transactional tail of an email lookup (no {@code REQUIRES_NEW}: called from a request thread). The guard is
     * re-checked here so two racing presses leave the first answer standing.
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
     * A captured person the workspace already researched is filed on this mandate under the employer it
     * already knows them at ({@link PersonEmployerResolver#knownEmployerOf}), as research would have filed
     * them, with no second vendor call.
     */
    private void mapToKnownEmployer(UUID workspaceId, UUID projectId, Candidate candidate) {
        Person person = candidate.getPerson();
        List<Candidate> elsewhere = candidates.findPositionsOfPerson(workspaceId, person.getId()).stream()
                .filter(row -> !row.getProjectId().equals(projectId))
                .toList();
        employerResolver.knownEmployerOf(workspaceId, person, elsewhere)
                .ifPresent(employer -> mapToEmployer(projectId, candidate, employer, capturedBy(projectId, candidate)));
    }

    /**
     * An unmapped candidate whose employer is named — by research, or by what the workspace already knows of
     * them — gets that company filed into the mandate's universe and is mapped to it, and the grid is told
     * a company moved. Skipped when the mandate already maps someone of the same name at that company —
     * the scope {@link #refuseDuplicate} holds a mandate to.
     */
    private void mapToEmployer(UUID projectId, Candidate candidate, CapturedCompanyDetails employer,
                               EmployerFilings filings) {
        candidate.adoptEmployer(employer.companyName());
        if (candidate.getTriageCompanyId() != null || employer.companyName() == null) {
            return;
        }
        // An employer somebody already stated outranks the vendor's, as enrich() also refuses to
        // overwrite it: mapping would otherwise reintroduce through employBy() exactly what that
        // guard just prevented.
        if (candidate.getCompanyName() != null
                && !candidate.getCompanyName().equalsIgnoreCase(employer.companyName())) {
            return;
        }
        FiledCompany row = filings.rowFor(employer);
        TriageCompanyResponse company = row.company();

        String slug = LinkedInUrls.profileSlugOrNull(candidate.getPerson().getLinkedinUrl());
        boolean nameHeldThere = candidates
                .findByProjectIdAndTriageCompanyIdAndPersonFullNameIgnoreCase(
                        projectId, company.id(), candidate.getPerson().getFullName())
                .stream()
                .anyMatch(other -> !other.getId().equals(candidate.getId()) && !isAnotherProfile(other, slug));
        if (nameHeldThere) {
            log.info("Leaving candidate {} unmapped — {} already maps that name", candidate.getId(),
                    company.companyName());
            return;
        }
        candidate.employBy(company.id(), company.companyName(), TriageCompanyStatus.fromValue(company.status()),
                row.created());
        stream.publish(projectId, ProjectStreamKind.COMPANY_CAPTURED);
    }

    /** Filed as the door the person came through: a hand-typed add researched behind its save is still hand-typed. */
    private EmployerFilings capturedBy(UUID projectId, Candidate candidate) {
        // CSV is its own door: a spreadsheet person researched by an import's enrichment was badged a plugin capture.
        TriageCompanySource door = switch (candidate.getSource()) {
            case MANUAL -> TriageCompanySource.MANUAL;
            case CSV -> TriageCompanySource.CSV;
            default -> TriageCompanySource.EXTENSION;
        };
        return new EmployerFilings(triage, projectId, candidate.getAddedBy(), door);
    }

    /**
     * The research of a corrected profile, which replaces the old one's — no AI run follows: correcting an address
     * asked for research, not inference. Skipped when the address was corrected again while research ran.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyCorrectedResearch(UUID projectId, UUID candidateId, String researchedUrl,
                                       EnrichedProfile enriched) {
        candidates.findByIdAndProjectId(candidateId, projectId).ifPresent(candidate -> {
            Person person = candidate.getPerson();
            String held = LinkedInUrls.profileSlugOrNull(person.getLinkedinUrl());
            if (person.isProcessingRestricted() || held == null
                    || !held.equalsIgnoreCase(LinkedInUrls.profileSlugOrNull(researchedUrl))) {
                return;
            }
            person.replaceResearch(enriched);
            photos.findByPersonId(person.getId()).ifPresent(photos::delete);
            photos.flush();
            keepPhoto(person.getId(), enriched);
            activity.record(candidate, candidate.getAddedBy(), PersonActivityKind.RESEARCHED,
                    PersonActivityDetails.of("vendor", enriched.vendor()));
            stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        });
    }

    private void keepPhoto(UUID personId, EnrichedProfile enriched) {
        if (enriched.photo() == null || photos.existsByPersonId(personId)) {
            return;
        }
        photos.save(PersonPhoto.of(personId, enriched.photo()));
    }

    /** The stored profile photo, or NOT_FOUND — "no photo" and "no such candidate" read the same. */
    @Transactional(readOnly = true)
    public StoredPhoto photoOf(UUID workspaceId, UUID projectId, UUID candidateId, ProjectAudience audience) {
        projects.requireInWorkspace(projectId, workspaceId);
        // Existence, not content: a grid of avatars asks this per row, and loading each whole row —
        // profile jsonb included — to throw it away is a scan the scoping does not need.
        UUID personId = (audience.clientVisibleOnly()
                        ? candidates.findClientVisiblePersonIdByIdAndProjectId(candidateId, projectId)
                        : candidates.findPersonIdByIdAndProjectId(candidateId, projectId))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return photos.findByPersonId(personId)
                .map(photo -> new StoredPhoto(photo.getContent(), photo.getContentType()))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /**
     * {@code withCompany} also removes the company they were the last one at; anyone else mapped there refuses the
     * whole removal. {@code withFiledCompany} (the plugin's Undo) takes the company only where it was filed for them
     * and nobody has worked it since, and otherwise leaves it without refusing. The audit is recorded last: its writer
     * is async and would outlive a rollback.
     */
    @Transactional
    public void remove(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, boolean withCompany,
                       boolean withFiledCompany, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        UUID companyId = candidate.getTriageCompanyId();
        boolean asksForCompany = companyId != null
                && (withCompany || withFiledCompany && candidate.isCompanyFiledForThem());
        if (asksForCompany) {
            triage.holdForRemoval(projectId, companyId);
        }

        removal.unmap(candidate, userId);
        boolean companyRemoved = false;
        if (asksForCompany) {
            candidates.flush();
            if (withCompany) {
                triage.removeFromProject(userId, workspaceId, projectId, companyId, 0, httpRequest);
                companyRemoved = true;
            } else {
                companyRemoved = triage.removeIfUntouched(userId, workspaceId, projectId, companyId, httpRequest);
            }
        }
        CandidateRemoval.recordRemoved(audit.projectEvent(ProjectEventType.CANDIDATE_REMOVED, userId, workspaceId,
                projectId, httpRequest).detail("companyRemoved", String.valueOf(companyRemoved)), candidate);
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

    /**
     * Refuses a name the mandate already maps, in V36's two scopes (the company, else the mandate); {@code selfId} is the
     * row being edited. Both finders carry the project id: scoping by company alone would make this 409 an oracle for
     * another workspace's company ids.
     */
    private void refuseDuplicate(UUID projectId, UUID triageCompanyId, String fullName, String linkedinUrl,
                                 UUID selfId) {
        List<Candidate> sameName = triageCompanyId == null
                ? candidates.findByProjectIdAndTriageCompanyIdIsNullAndPersonFullNameIgnoreCase(projectId, fullName)
                : candidates.findByProjectIdAndTriageCompanyIdAndPersonFullNameIgnoreCase(
                        projectId, triageCompanyId, fullName);

        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        boolean held = sameName.stream()
                .anyMatch(other -> !other.getId().equals(selfId) && !isAnotherProfile(other, slug));
        if (held) {
            throw ApiException.of(ErrorCode.CANDIDATE_ALREADY_MAPPED);
        }
    }

    /**
     * Two vanity profiles are two people, whatever they are called: the name rule exists for rows nobody can tell
     * apart, and a plugin capture is never mapped when it lands, so it refused every namesake of an unmapped capture.
     * A member-id address ({@code /in/ACoAA…}) is no such proof — it may be the same human's vanity profile seen from
     * Sales Navigator, which only research can pair — so the name rule still holds against it.
     */
    private static boolean isAnotherProfile(Candidate other, String slug) {
        String otherSlug = LinkedInUrls.profileSlugOrNull(other.getPerson().getLinkedinUrl());
        return slug != null && otherSlug != null && !slug.equals(otherSlug)
                && !LinkedInUrls.isMemberIdSlug(slug) && !LinkedInUrls.isMemberIdSlug(otherSlug);
    }

    /**
     * Refuses a LinkedIn profile slug the mandate already maps, wherever that row sits — the name rule misses a second
     * capture (no company) against a first since mapped to its employer, leaving a duplicate "Not in universe" line.
     * {@code selfId} is the row being edited.
     */
    private void refuseHeldProfile(UUID projectId, String linkedinUrl, UUID selfId) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        if (slug == null) {
            return;
        }
        boolean held = candidatesAtProfile(projectId, slug).stream()
                .anyMatch(other -> !other.getId().equals(selfId));
        if (held) {
            throw ApiException.of(ErrorCode.CANDIDATE_ALREADY_MAPPED);
        }
    }

    private AddedByResponse adderOf(UUID projectId, UUID candidateId) {
        return candidates.findAttributionByProjectIdAndIdIn(projectId, List.of(candidateId)).stream()
                .map(CandidateAttribution::getAddedBy)
                .filter(Objects::nonNull)
                .findFirst()
                .flatMap(users::findById)
                .map(adder -> new AddedByResponse(adder.getId(), adder.getFullName(), adder.getAvatarUrl()))
                .orElse(null);
    }

    /**
     * Files an executive on this mandate as a workspace person: the one the workspace already knows
     * them as, whose empty fields this filing fills, or a new one it founds. A person this mandate
     * already holds is refused as {@code CANDIDATE_ALREADY_MAPPED}, the answer every door already
     * handles — V91's (project, person) index is the backstop a race would hit. A person whose processing is
     * restricted is mapped onto no further position.
     */
    private Filed file(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                       CandidateSource source, CandidateStatusDefinition status, ExitReason reason,
                       String exitNote, CandidateDetails details) {
        return file(userId, workspaceId, projectId, triageCompanyId, source, status, reason, exitNote, details,
                matcher.find(workspaceId, details));
    }

    /** {@code reason} is why someone filed straight at a way out left, already checked against {@code status}. */
    private Filed file(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                       CandidateSource source, CandidateStatusDefinition status, ExitReason reason, String exitNote,
                       CandidateDetails details, Optional<Person> known) {
        if (known.isPresent() && candidates.existsByProjectIdAndPersonId(projectId, known.get().getId())) {
            throw ApiException.of(ErrorCode.CANDIDATE_ALREADY_MAPPED);
        }
        if (known.isPresent()) {
            suppression.refuseSuppressedPerson(workspaceId, known.get());
        }
        if (known.isPresent() && known.get().isProcessingRestricted()) {
            throw ApiException.of(ErrorCode.PERSON_PROCESSING_RESTRICTED);
        }
        ContactChannelSizes contactsBefore = known.map(requests::channelSizesOf).orElse(ContactChannelSizes.NONE);
        Person person = known.orElseGet(() -> people.save(Person.founded(workspaceId, userId, source, details)));
        if (known.isPresent()) {
            person.fillFrom(details, ContactSource.ofDoor(source));
        }
        if (source == CandidateSource.EXTENSION) {
            person.lockProfileUrl(details.linkedinUrl());
        }
        requests.refuseOverfullChannels(person, contactsBefore);
        Candidate candidate = candidates.save(Candidate.mapped(projectId, userId, triageCompanyId,
                triageStatusOfCompany(projectId, triageCompanyId), person, source, status, details));
        candidate.giveExitReasonIfAny(reason, exitNote, userId);
        activity.record(candidate, userId,
                known.isPresent() ? PersonActivityKind.MAPPED : PersonActivityKind.ADDED_TO_POOL,
                PersonActivityDetails.of("door", source).and("reason", reason == null ? null : reason.getLabel()));
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
            return Optional.of(answeredPerson(workspaceId, request.existingPersonId(), details));
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
    private Person answeredPerson(UUID workspaceId, UUID existingPersonId, CandidateDetails details) {
        Optional<Person> keyHolder = matcher.keyHolderOf(workspaceId, details);
        if (keyHolder.isPresent()) {
            if (!keyHolder.get().getId().equals(existingPersonId)) {
                throw ApiException.of(ErrorCode.CANDIDATE_KEYS_NAME_ANOTHER);
            }
            return keyHolder.get();
        }
        return matcher.possibleDuplicates(workspaceId, details).stream()
                .filter(namesake -> namesake.getId().equals(existingPersonId))
                .findFirst()
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /**
     * Adds people the workspace already holds to a mandate, as Identified, each under the employer their latest
     * position recorded, as a plugin capture of someone known is. Someone it already holds stays as they are, and
     * someone whose processing is restricted is left out and counted. The caller has checked they may work the mandate.
     */
    @Transactional
    public MapPeopleToPositionResponse mapFromPool(UUID userId, UUID workspaceId, UUID projectId,
                                                   List<Person> persons, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Map<UUID, List<Candidate>> mappedOldestFirst = candidates
                .findPositionsOfPeople(workspaceId, persons.stream().map(Person::getId).toList()).stream()
                .collect(Collectors.groupingBy(row -> row.getPerson().getId()));
        Set<UUID> suppressed = suppression.suppressedPeopleOf(workspaceId, persons);
        List<UUID> added = new ArrayList<>();
        int alreadyIn = 0;
        int restricted = 0;
        CandidateStatusDefinition startingStatus = pipeline.catalogOf(workspaceId).defaultStatus();
        EmployerFilings filings = new EmployerFilings(triage, projectId, userId, TriageCompanySource.MANUAL);
        for (Person person : persons) {
            List<Candidate> rows = mappedOldestFirst.getOrDefault(person.getId(), List.of());
            if (rows.stream().anyMatch(row -> row.getProjectId().equals(projectId))) {
                alreadyIn++;
                continue;
            }
            if (suppressed.contains(person.getId())) {
                continue;
            }
            if (person.isProcessingRestricted()) {
                restricted++;
                continue;
            }
            Candidate candidate = candidates.save(Candidate.mappedFromPool(projectId, userId, person, startingStatus));
            activity.record(candidate, userId, PersonActivityKind.MAPPED,
                    PersonActivityDetails.of("door", CandidateSource.MANUAL));
            employerResolver.knownEmployerOf(workspaceId, person, rows).ifPresentOrElse(
                    employer -> mapToEmployer(projectId, candidate, employer, filings),
                    () -> candidate.adoptEmployer(PersonEmployerResolver.employerOf(person, rows).name()));
            added.add(person.getId());
        }
        if (!added.isEmpty()) {
            stream.publish(projectId, ProjectStreamKind.CANDIDATE_CAPTURED);
            audit.projectEvent(ProjectEventType.PEOPLE_MAPPED_FROM_POOL, userId, workspaceId, projectId, httpRequest)
                    .detail("personIds", added.stream().map(UUID::toString).toList())
                    .record();
        }
        return new MapPeopleToPositionResponse(added.size(), alreadyIn,
                persons.size() - added.size() - alreadyIn - restricted, restricted);
    }

    /** The stage a person filed or remapped at this company starts at; In universe at none. */
    private TriageCompanyStatus triageStatusOfCompany(UUID projectId, UUID triageCompanyId) {
        if (triageCompanyId == null) {
            return TriageCompanyStatus.IN_UNIVERSE;
        }
        return TriageCompanyStatus.parseOrInUniverse(
                triage.requireCompanyOfProject(projectId, triageCompanyId, false).status());
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
