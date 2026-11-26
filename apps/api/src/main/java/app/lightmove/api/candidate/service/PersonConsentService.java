package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CandidatePrivacyMode;
import app.lightmove.api.candidate.constant.ConsentEventAction;
import app.lightmove.api.candidate.constant.ConsentEventVia;
import app.lightmove.api.candidate.constant.ConsentPurpose;
import app.lightmove.api.candidate.constant.ConsentRequestSkipReason;
import app.lightmove.api.candidate.constant.ConsentStatus;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.PrivacyLinkPurpose;
import app.lightmove.api.candidate.constant.PrivacyNoticeChannel;
import app.lightmove.api.candidate.constant.PrivacyNoticeSkipReason;
import app.lightmove.api.candidate.dto.BulkConsentRequestResponse;
import app.lightmove.api.candidate.dto.ConsentRequestSkipResponse;
import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.dto.RecordConsentRequest;
import app.lightmove.api.candidate.dto.ShareConsentRowResponse;
import app.lightmove.api.candidate.dto.ShareConsentsResponse;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidatePrivacySettings;
import app.lightmove.api.candidate.model.ConsentPriorState;
import app.lightmove.api.candidate.model.ConsentRegisterRow;
import app.lightmove.api.candidate.model.ConsentRequestsQueued;
import app.lightmove.api.candidate.model.MintedPrivacyLink;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonConsent;
import app.lightmove.api.candidate.model.QueuedConsentRequest;
import app.lightmove.api.candidate.model.ShareTarget;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asking a person for consent per purpose (#1267), recording an answer they gave another way, and the daily close of
 * what lapsed. A request carries the privacy notice's facts, so it also tells them; it spends the same daily budget as
 * notices. Silence is never consent: a request nobody answers closes as expired and changes nothing else.
 */
@Service
public class PersonConsentService {

    static final Duration REQUEST_LIFETIME = Duration.ofDays(30);
    private static final Duration RECENTLY = Duration.ofDays(14);
    /** A person's objection sits beside their consents, after them. */
    private static final Comparator<ConsentRegisterRow> REGISTER_ORDER = Comparator
            .comparing(ConsentRegisterRow::personName, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ConsentRegisterRow::personId)
            .thenComparing(ConsentRegisterRow::isObjection);
    private static final String PERSON_TARGET = "person";
    private static final int SCAN_BATCH = 500;
    private static final int SCAN_BATCHES_PER_RUN = 20;
    private static final Set<ConsentStatus> ANSWERS_BY_HAND = EnumSet.of(ConsentStatus.GIVEN, ConsentStatus.DECLINED,
            ConsentStatus.WITHDRAWN);

    private final PersonRepository people;
    private final PersonRecordService records;
    private final PersonActivityRecorder activity;
    private final CandidatePrivacySettingsService settings;
    private final SuppressionGate suppression;
    private final PrivacyEmailBudget budget;
    private final PrivacyLinkStore privacyLinks;
    private final PersonConsentStore consents;
    private final ShareTargets shareTargets;
    private final CandidateRepository candidates;
    private final ProjectAccess projectAccess;
    private final EmailTemplates templates;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final Clock clock;
    private final String baseUrl;

    public PersonConsentService(PersonRepository people, PersonRecordService records, PersonActivityRecorder activity,
                                CandidatePrivacySettingsService settings, SuppressionGate suppression,
                                PrivacyEmailBudget budget, PrivacyLinkStore privacyLinks, PersonConsentStore consents,
                                ShareTargets shareTargets, CandidateRepository candidates, ProjectAccess projectAccess,
                                EmailTemplates templates, ApplicationEventPublisher events, AuditService audit,
                                Clock clock, LightMoveProperties properties) {
        this.people = people;
        this.records = records;
        this.activity = activity;
        this.settings = settings;
        this.suppression = suppression;
        this.budget = budget;
        this.privacyLinks = privacyLinks;
        this.consents = consents;
        this.shareTargets = shareTargets;
        this.candidates = candidates;
        this.projectAccess = projectAccess;
        this.templates = templates;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
        this.baseUrl = properties.web().baseUrl();
    }

    @Transactional
    public PersonRecordResponse request(UUID userId, UUID workspaceId, UUID personId, List<String> wirePurposes,
                                        UUID projectId, Boolean nameClient, HttpServletRequest httpRequest) {
        CandidatePrivacySettings firm = requireConsentMode(workspaceId);
        Set<ConsentPurpose> asked = purposesOf(wirePurposes, firm, true);
        Person person = people.requireInWorkspace(personId, workspaceId);
        ShareRequest share = asked.contains(ConsentPurpose.SHARE_WITH_CLIENT)
                ? shareFromPoolOf(userId, workspaceId, projectId, person, nameClient) : null;
        askOne(userId, workspaceId, person, asked, share, firm, httpRequest);
        return records.toRecord(userId, person);
    }

    /**
     * Phase 3: asks the person on this position whether the firm may share their profile with its client. The email
     * says "a confidential client" unless the firm names the client, which a confidential search never allows.
     */
    @Transactional
    public ShareConsentRowResponse requestShare(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                                Boolean nameClient, HttpServletRequest httpRequest) {
        CandidatePrivacySettings firm = requireConsentMode(workspaceId);
        if (!firm.offeredPurposes().contains(ConsentPurpose.SHARE_WITH_CLIENT)) {
            throw ApiException.of(ErrorCode.CONSENT_REQUESTS_OFF);
        }
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        Person person = people.requireInWorkspace(candidate.getPerson().getId(), workspaceId);
        ShareRequest share = shareRequestOf(shareTargets.require(workspaceId, projectId), nameClient);
        askOne(userId, workspaceId, person, EnumSet.of(ConsentPurpose.SHARE_WITH_CLIENT), share, firm, httpRequest);
        return shareRowOf(candidateId, now(), consents.consentsOf(person.getId()).stream()
                .filter(consent -> isShareOn(consent, projectId)).findFirst().orElse(null));
    }

    /** The position's share consents, one row per person on it who was ever asked, keyed by their candidate row. */
    @Transactional(readOnly = true)
    public ShareConsentsResponse shareConsentsOf(UUID workspaceId, UUID projectId) {
        CandidatePrivacySettings firm = settings.settingsOf(workspaceId);
        ShareTarget target = shareTargets.require(workspaceId, projectId);
        Map<UUID, UUID> candidateByPerson = consents.candidatesOnPosition(workspaceId, projectId);
        Instant now = clock.instant();
        List<ShareConsentRowResponse> rows = consents.sharesOn(workspaceId, projectId).stream()
                .filter(consent -> candidateByPerson.containsKey(consent.personId()))
                .map(consent -> shareRowOf(candidateByPerson.get(consent.personId()), now, consent))
                .toList();
        boolean asking = firm.isReadyForConsent()
                && firm.offeredPurposes().contains(ConsentPurpose.SHARE_WITH_CLIENT);
        return new ShareConsentsResponse(asking, !target.confidential(), target.clientName(), rows);
    }

    /**
     * The consent register: every request and answer in the workspace, or the shares asked for on one position.
     * Past {@code cap} lines it is refused rather than cut short: half a register still looks whole.
     */
    @Transactional(readOnly = true)
    public List<ConsentRegisterRow> registerOf(UUID workspaceId, UUID projectId, int cap) {
        if (projectId != null) {
            shareTargets.require(workspaceId, projectId);
        }
        List<ConsentRegisterRow> rows = Stream.concat(consents.registerOf(workspaceId, projectId, cap + 1).stream(),
                        consents.objectionsOf(workspaceId, projectId, cap + 1).stream())
                .sorted(REGISTER_ORDER)
                .toList();
        if (rows.size() > cap) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "That is more consents than one file may carry. Download it for one position instead.");
        }
        return rows;
    }

    /** Whether turning on client visibility for this person should warn: consent mode, and no live share consent. */
    @Transactional(readOnly = true)
    public boolean showsWithoutShareConsent(UUID workspaceId, UUID projectId, UUID personId) {
        CandidatePrivacySettings firm = settings.settingsOf(workspaceId);
        if (!firm.isReadyForConsent() || !firm.offeredPurposes().contains(ConsentPurpose.SHARE_WITH_CLIENT)) {
            return false;
        }
        Instant now = clock.instant();
        return consents.consentsOf(personId).stream()
                .noneMatch(consent -> isShareOn(consent, projectId) && consent.isLiveAt(now));
    }

    private void askOne(UUID userId, UUID workspaceId, Person person, Set<ConsentPurpose> asked, ShareRequest share,
                        CandidatePrivacySettings firm, HttpServletRequest httpRequest) {
        budget.hold(workspaceId);
        Instant now = now();
        Set<ConsentPurpose> askable = EnumSet.noneOf(ConsentPurpose.class);
        ConsentRequestSkipReason skip = skipReasonOf(person,
                suppression.suppressedPeopleOf(workspaceId, List.of(person)), consents.consentsOf(person.getId()),
                asked, share, firm, now, askable);
        if (skip == null && budget.remainingToday(workspaceId) <= 0) {
            skip = ConsentRequestSkipReason.DAILY_CAP;
        }
        if (skip != null) {
            throw refusalOf(skip);
        }
        queue(userId, workspaceId, Map.of(person, askable), share, Map.of(), firm, now, httpRequest);
    }

    /**
     * A share asked or recorded from the person's own record, whose door is the pool's: the position named in the
     * body must be one the caller works, as the position's own door requires.
     */
    private ShareRequest shareFromPoolOf(UUID userId, UUID workspaceId, UUID projectId, Person person,
                                         Boolean nameClient) {
        if (projectId == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "projectId", "Choose the position to share for");
        }
        projectAccess.requireAction(userId, workspaceId, projectId, ProjectAction.WORK_EXECUTE);
        ShareTarget target = shareTargets.require(workspaceId, projectId);
        if (candidates.findByProjectIdAndPersonId(projectId, person.getId()).isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "projectId", "They are not on that position");
        }
        return shareRequestOf(target, nameClient);
    }

    private static ShareRequest shareRequestOf(ShareTarget target, Boolean nameClient) {
        boolean named = Boolean.TRUE.equals(nameClient);
        if (named && target.confidential()) {
            throw ApiException.of(ErrorCode.SHARE_CLIENT_CONFIDENTIAL);
        }
        return new ShareRequest(target, named);
    }

    /** Everyone it can, in the order asked; who is skipped is answered with why, never dropped silently. */
    @Transactional
    public BulkConsentRequestResponse requestEach(UUID userId, UUID workspaceId, List<UUID> personIds,
                                                  List<String> wirePurposes, HttpServletRequest httpRequest) {
        CandidatePrivacySettings firm = requireConsentMode(workspaceId);
        Set<ConsentPurpose> asked = purposesOf(wirePurposes, firm, false);
        budget.hold(workspaceId);
        Set<UUID> ordered = new LinkedHashSet<>(personIds);
        Map<UUID, Person> found = people.findByWorkspaceIdAndIdIn(workspaceId, ordered).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        Set<UUID> suppressed = suppression.suppressedPeopleOf(workspaceId, found.values());
        Map<UUID, List<PersonConsent>> held = consents.consentsOfEach(found.keySet());
        int capacity = budget.remainingToday(workspaceId);
        Instant now = now();
        Map<Person, Set<ConsentPurpose>> asking = new LinkedHashMap<>();
        List<ConsentRequestSkipResponse> skipped = new ArrayList<>();
        for (UUID personId : ordered) {
            Person person = found.get(personId);
            if (person == null) {
                throw ApiException.of(ErrorCode.NOT_FOUND);
            }
            Set<ConsentPurpose> askable = EnumSet.noneOf(ConsentPurpose.class);
            ConsentRequestSkipReason skip = skipReasonOf(person, suppressed, held.get(personId), asked, null, firm,
                    now, askable);
            if (skip == null && asking.size() >= capacity) {
                skip = ConsentRequestSkipReason.DAILY_CAP;
            }
            if (skip != null) {
                skipped.add(new ConsentRequestSkipResponse(personId, skip.value()));
                continue;
            }
            asking.put(person, askable);
        }
        queue(userId, workspaceId, asking, null, Map.of(), firm, now, httpRequest);
        return new BulkConsentRequestResponse(asking.size(), skipped);
    }

    /** Told another way — a call, a letter, the firm's own form. Only the answer and the day are kept. */
    @Transactional
    public PersonRecordResponse recordByHand(UUID userId, UUID workspaceId, UUID personId,
                                             RecordConsentRequest request, HttpServletRequest httpRequest) {
        CandidatePrivacySettings firm = settings.settingsOf(workspaceId);
        ConsentPurpose purpose = ApiValueEnum.require(ConsentPurpose.class, request.purpose(), "purpose");
        ConsentStatus answer = ApiValueEnum.require(ConsentStatus.class, request.answer(), "answer");
        if (!ANSWERS_BY_HAND.contains(answer)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "answer", "Choose given, declined or withdrawn");
        }
        if (request.answeredOn().isAfter(LocalDate.now(clock).plusDays(1))) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "answeredOn", "Choose today or an earlier day");
        }
        Person person = people.requireInWorkspace(personId, workspaceId);
        UUID projectId = purpose == ConsentPurpose.SHARE_WITH_CLIENT
                ? shareFromPoolOf(userId, workspaceId, request.projectId(), person, false).target().projectId()
                : null;
        Instant answeredAt = request.answeredOn().atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
        Instant validUntil = answer == ConsentStatus.GIVEN && purpose == ConsentPurpose.KEEP_ON_FILE
                ? answeredAt.atZone(ZoneOffset.UTC).plusMonths(firm.keepOnFileMonths()).toInstant() : null;
        UUID consentId = consents.recordByHand(workspaceId, personId, purpose, projectId, answer, answeredAt,
                validUntil);
        consents.appendEvent(workspaceId, personId, consentId, ConsentEventAction.RECORDED, answer,
                ConsentEventVia.BY_HAND, null, userId, null, null, now());
        activity.record(person, projectId, userId, PersonActivityKind.CONSENT_ANSWERED,
                PersonActivityDetails.of("purpose", purpose.value()).and("status", answer.value())
                        .and("via", "by_hand"));
        audit.event(ProjectEventType.PERSON_CONSENT_RECORDED)
                .actor(userId).workspace(workspaceId).target(PERSON_TARGET, personId).from(httpRequest)
                .detail("purpose", purpose.value())
                .detail("status", answer.value())
                .detailIfPresent("projectId", projectId == null ? null : projectId.toString())
                .record();
        return records.toRecord(userId, person);
    }

    /**
     * The daily close: requests past their 30 days and consents past their date become expired. Nothing else about
     * the person changes — not their basis, their retention or what a client sees.
     */
    @Transactional
    public int closeLapsed(UUID workspaceId, Instant now) {
        List<PersonConsent> expired = new ArrayList<>();
        for (int batch = 0; batch < SCAN_BATCHES_PER_RUN; batch++) {
            List<PersonConsent> closed = consents.closeLapsedRequests(workspaceId, now, SCAN_BATCH);
            closed.stream().filter(consent -> consent.status() == ConsentStatus.EXPIRED).forEach(expired::add);
            if (closed.size() < SCAN_BATCH) {
                break;
            }
        }
        for (int batch = 0; batch < SCAN_BATCHES_PER_RUN; batch++) {
            List<PersonConsent> ended = consents.expireLapsedConsents(workspaceId, now, SCAN_BATCH);
            expired.addAll(ended);
            if (ended.size() < SCAN_BATCH) {
                break;
            }
        }
        if (expired.isEmpty()) {
            return 0;
        }
        Map<UUID, Person> byId = people.findByWorkspaceIdAndIdIn(workspaceId,
                        expired.stream().map(PersonConsent::personId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        for (PersonConsent consent : expired) {
            consents.appendEvent(workspaceId, consent.personId(), consent.id(), ConsentEventAction.EXPIRED,
                    ConsentStatus.EXPIRED, ConsentEventVia.SYSTEM, null, null, null, null, now);
            Person person = byId.get(consent.personId());
            if (person != null) {
                activity.record(person, consent.projectId(), null, PersonActivityKind.CONSENT_EXPIRED,
                        PersonActivityDetails.of("purpose", consent.purpose().value()));
            }
        }
        return expired.size();
    }

    /** Keep-on-file consents ending within the firm's window are asked about once more, within the day's budget. */
    @Transactional
    public int requestRenewals(UUID workspaceId, Instant now) {
        CandidatePrivacySettings firm = settings.settingsOf(workspaceId);
        if (!firm.isReadyForConsent() || !firm.keepOnFile() || firm.renewalDaysBefore() == 0) {
            return 0;
        }
        budget.hold(workspaceId);
        int capacity = budget.remainingToday(workspaceId);
        Instant windowEnd = now.plus(Duration.ofDays(firm.renewalDaysBefore()));
        Map<Person, Set<ConsentPurpose>> asking = new LinkedHashMap<>();
        Map<UUID, PersonConsent> renewing = new LinkedHashMap<>();
        PersonConsent after = null;
        while (asking.size() < capacity) {
            List<PersonConsent> due = consents.dueRenewal(workspaceId, now, windowEnd, after, SCAN_BATCH);
            if (due.isEmpty()) {
                break;
            }
            after = due.getLast();
            Map<UUID, Person> found = people.findByWorkspaceIdAndIdIn(workspaceId,
                            due.stream().map(PersonConsent::personId).toList()).stream()
                    .collect(Collectors.toMap(Person::getId, Function.identity()));
            Set<UUID> suppressed = suppression.suppressedPeopleOf(workspaceId, found.values());
            for (PersonConsent consent : due) {
                Person person = found.get(consent.personId());
                if (asking.size() >= capacity) {
                    break;
                }
                if (person != null && PrivacyEmailBudget.unreachableReasonOf(person, suppressed) == null) {
                    asking.put(person, EnumSet.of(ConsentPurpose.KEEP_ON_FILE));
                    renewing.put(person.getId(), consent);
                }
            }
            if (due.size() < SCAN_BATCH) {
                break;
            }
        }
        queue(null, workspaceId, asking, null, renewing, firm, now.truncatedTo(ChronoUnit.MICROS), null);
        return asking.size();
    }

    /**
     * Records every request as sent and hands the emails to the mailer for after the commit: a rolled-back request
     * must not reach anyone, and a refused one is undone there.
     */
    private void queue(UUID senderId, UUID workspaceId, Map<Person, Set<ConsentPurpose>> asking, ShareRequest share,
                       Map<UUID, PersonConsent> renewals, CandidatePrivacySettings firm, Instant now,
                       HttpServletRequest httpRequest) {
        if (asking.isEmpty()) {
            return;
        }
        Map<UUID, MintedPrivacyLink> links = privacyLinks.mintEach(workspaceId,
                asking.keySet().stream().map(Person::getId).toList(), PrivacyLinkPurpose.CONSENT, senderId, now,
                REQUEST_LIFETIME);
        Instant expiresAt = now.plus(REQUEST_LIFETIME);
        int months = firm.keepOnFileMonths();
        List<QueuedConsentRequest> queued = new ArrayList<>(asking.size());
        asking.forEach((person, purposes) -> {
            MintedPrivacyLink link = links.get(person.getId());
            List<ConsentPriorState> priors = new ArrayList<>();
            List<String> asks = new ArrayList<>();
            PersonConsent renewing = renewals.get(person.getId());
            boolean renewal = renewing != null;
            for (ConsentPurpose purpose : purposes) {
                boolean shared = purpose == ConsentPurpose.SHARE_WITH_CLIENT;
                String clientName = shared ? share.clientNameOrNull() : null;
                String roleTitle = shared ? share.target().roleTitle() : null;
                Integer promisedMonths = purpose == ConsentPurpose.KEEP_ON_FILE ? months : null;
                String sentence = renewal
                        ? ConsentWording.renewalSentenceOf(renewing.answeredAt(), renewedUntil(renewing, months))
                        : ConsentWording.sentenceOf(purpose, months, clientName, roleTitle);
                if (!renewal) {
                    asks.add(ConsentWording.askingOf(purpose, months, clientName, roleTitle));
                }
                priors.add(consents.openRequest(workspaceId, person.getId(), purpose,
                        shared ? share.target().projectId() : null, senderId, link.id(), now, expiresAt,
                        shared && share.named(), ConsentWording.VERSION, sentence, promisedMonths));
            }
            Instant previouslyNotifiedAt = person.getPrivacyNotifiedAt();
            PrivacyNoticeChannel previousChannel = person.getPrivacyNoticeChannel();
            person.recordNotified(now, PrivacyNoticeChannel.PRIVACY_EMAIL);
            String purposeTokens = purposes.stream().map(ConsentPurpose::value).collect(Collectors.joining(","));
            UUID sharedProjectId = share != null && purposes.contains(ConsentPurpose.SHARE_WITH_CLIENT)
                    ? share.target().projectId() : null;
            activity.record(person, sharedProjectId, senderId, PersonActivityKind.CONSENT_REQUESTED,
                    PersonActivityDetails.of("purposes", purposeTokens).and("renewal", renewal ? true : null));
            audit.event(ProjectEventType.PERSON_CONSENT_REQUESTED)
                    .actor(senderId).workspace(workspaceId).target(PERSON_TARGET, person.getId()).from(httpRequest)
                    .detail("purposes", purposeTokens)
                    .detail("renewal", renewal)
                    .detailIfPresent("projectId", sharedProjectId == null ? null : sharedProjectId.toString())
                    .detailIfPresent("clientIdentified", sharedProjectId == null ? null : share.named())
                    .record();
            String recipient = person.emailContacts().getFirst().getValue();
            String pageLink = baseUrl + "/privacy/" + link.token();
            boolean shareOnly = purposes.equals(EnumSet.of(ConsentPurpose.SHARE_WITH_CLIENT));
            EmailMessage email = shareOnly
                    ? templates.buildShareConsentRequestEmail(recipient, person.getFullName(), firm.controllerName(),
                            firm.privacyContactEmail(), PrivacyNoticeWording.sourceOf(person.getSource()),
                            share.target().roleTitle(), share.clientNameOrNull(), pageLink, firm.noticeUrl(),
                            ConsentWording.dayOf(expiresAt))
                    : renewal
                    ? templates.buildConsentRenewalEmail(recipient, person.getFullName(), firm.controllerName(),
                            firm.privacyContactEmail(), ConsentWording.dayOf(renewing.answeredAt()),
                            ConsentWording.dayOf(renewing.validUntil()), months, pageLink,
                            firm.noticeUrl(), ConsentWording.dayOf(expiresAt))
                    : templates.buildConsentRequestEmail(recipient, person.getFullName(), firm.controllerName(),
                            firm.privacyContactEmail(), PrivacyNoticeWording.sourceOf(person.getSource()), asks,
                            pageLink, firm.noticeUrl(), ConsentWording.dayOf(expiresAt));
            queued.add(new QueuedConsentRequest(link.id(), person.getId(), now, previouslyNotifiedAt,
                    previousChannel, List.copyOf(priors), email));
        });
        events.publishEvent(new ConsentRequestsQueued(workspaceId, senderId, List.copyOf(queued)));
    }

    /** A renewal extends from the day the consent ends, so answering early loses nothing. */
    static Instant renewedUntil(PersonConsent consent, int months) {
        return consent.validUntil().atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    /**
     * Fills {@code askable} with the purposes still worth asking: not already given beyond the renewal window, not
     * asked within the last 14 days, and not declined or withdrawn within them. Answers why nothing is, or null.
     */
    private ConsentRequestSkipReason skipReasonOf(Person person, Set<UUID> suppressed, List<PersonConsent> held,
                                                  Set<ConsentPurpose> asked, ShareRequest share,
                                                  CandidatePrivacySettings firm, Instant now,
                                                  Set<ConsentPurpose> askable) {
        PrivacyNoticeSkipReason unreachable = PrivacyEmailBudget.unreachableReasonOf(person, suppressed);
        if (unreachable != null) {
            return switch (unreachable) {
                case NO_EMAIL -> ConsentRequestSkipReason.NO_EMAIL;
                case DO_NOT_CONTACT -> ConsentRequestSkipReason.DO_NOT_CONTACT;
                case PROCESSING_RESTRICTED -> ConsentRequestSkipReason.PROCESSING_RESTRICTED;
                case SUPPRESSED -> ConsentRequestSkipReason.SUPPRESSED;
                case RECENTLY_NOTIFIED, DAILY_CAP ->
                        throw new IllegalStateException("Not a reason no email may reach someone: " + unreachable);
            };
        }
        Instant renewalWindowEnd = now.plus(Duration.ofDays(firm.renewalDaysBefore()));
        boolean anyRecentlyAsked = false;
        for (ConsentPurpose purpose : asked) {
            UUID projectId = purpose == ConsentPurpose.SHARE_WITH_CLIENT ? share.target().projectId() : null;
            PersonConsent consent = held.stream()
                    .filter(candidate -> candidate.purpose() == purpose
                            && Objects.equals(candidate.projectId(), projectId))
                    .findFirst().orElse(null);
            if (consent != null && consent.isLiveAt(now)
                    && (consent.validUntil() == null || consent.validUntil().isAfter(renewalWindowEnd))) {
                continue;
            }
            if (consent != null && consent.wasAskedOrRefusedSince(now.minus(RECENTLY))) {
                anyRecentlyAsked = true;
                continue;
            }
            askable.add(purpose);
        }
        if (!askable.isEmpty()) {
            return null;
        }
        return anyRecentlyAsked ? ConsentRequestSkipReason.RECENTLY_ASKED : ConsentRequestSkipReason.ALREADY_GIVEN;
    }

    /** The purposes asked about: offered by the firm; a share names one position, so never in a bulk request. */
    private static Set<ConsentPurpose> purposesOf(List<String> wirePurposes, CandidatePrivacySettings firm,
                                                  boolean shareAllowed) {
        Set<ConsentPurpose> purposes = EnumSet.noneOf(ConsentPurpose.class);
        for (String wire : wirePurposes) {
            ConsentPurpose purpose = ApiValueEnum.require(ConsentPurpose.class, wire, "purpose");
            if (purpose == ConsentPurpose.SHARE_WITH_CLIENT && !shareAllowed) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "purposes",
                        "Ask to share with a client from the position");
            }
            if (!firm.offeredPurposes().contains(purpose)) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "purposes",
                        "Your workspace doesn't ask for that");
            }
            purposes.add(purpose);
        }
        return purposes;
    }

    private CandidatePrivacySettings requireConsentMode(UUID workspaceId) {
        CandidatePrivacySettings firm = settings.settingsOf(workspaceId);
        if (!firm.isReadyForNotices()) {
            throw ApiException.of(ErrorCode.CANDIDATE_PRIVACY_NOT_SET_UP);
        }
        if (firm.mode() != CandidatePrivacyMode.CONSENT) {
            throw ApiException.of(ErrorCode.CONSENT_REQUESTS_OFF);
        }
        return firm;
    }

    private static ApiException refusalOf(ConsentRequestSkipReason skip) {
        return ApiException.of(switch (skip) {
            case NO_EMAIL -> ErrorCode.PRIVACY_NOTICE_NO_EMAIL;
            case DO_NOT_CONTACT -> ErrorCode.PERSON_DO_NOT_CONTACT;
            case PROCESSING_RESTRICTED -> ErrorCode.PERSON_PROCESSING_RESTRICTED;
            case SUPPRESSED -> ErrorCode.PERSON_SUPPRESSED;
            case ALREADY_GIVEN -> ErrorCode.CONSENT_ALREADY_GIVEN;
            case RECENTLY_ASKED -> ErrorCode.CONSENT_RECENTLY_REQUESTED;
            case DAILY_CAP -> ErrorCode.PRIVACY_NOTICE_DAILY_LIMIT;
        });
    }

    private static boolean isShareOn(PersonConsent consent, UUID projectId) {
        return consent.purpose() == ConsentPurpose.SHARE_WITH_CLIENT && projectId.equals(consent.projectId());
    }

    private static ShareConsentRowResponse shareRowOf(UUID candidateId, Instant now, PersonConsent consent) {
        if (consent == null) {
            return new ShareConsentRowResponse(candidateId, null, null, null, null, false);
        }
        return new ShareConsentRowResponse(candidateId, consent.status().value(), consent.requestedAt(),
                consent.isOpenAt(now) ? consent.requestExpiresAt() : null, consent.answeredAt(),
                consent.clientNamedAt(now));
    }

    /** A share asked about: the position it is for, and whether the email names the client. */
    private record ShareRequest(ShareTarget target, boolean named) {

        String clientNameOrNull() {
            return target.clientNamedOrNull(named);
        }
    }

    /** Microseconds, as the columns keep them, so a refused send can tell its own telling from a later one. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
