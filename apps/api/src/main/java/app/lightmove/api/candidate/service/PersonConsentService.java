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
import app.lightmove.api.candidate.model.CandidatePrivacySettings;
import app.lightmove.api.candidate.model.ConsentPriorState;
import app.lightmove.api.candidate.model.ConsentRequestsQueued;
import app.lightmove.api.candidate.model.MintedPrivacyLink;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonConsent;
import app.lightmove.api.candidate.model.QueuedConsentRequest;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private static final String PERSON_TARGET = "person";
    private static final int SCAN_BATCH = 500;
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
    private final EmailTemplates templates;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final Clock clock;
    private final String baseUrl;

    public PersonConsentService(PersonRepository people, PersonRecordService records, PersonActivityRecorder activity,
                                CandidatePrivacySettingsService settings, SuppressionGate suppression,
                                PrivacyEmailBudget budget, PrivacyLinkStore privacyLinks, PersonConsentStore consents,
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
        this.templates = templates;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
        this.baseUrl = properties.web().baseUrl();
    }

    @Transactional
    public PersonRecordResponse request(UUID userId, UUID workspaceId, UUID personId, List<String> wirePurposes,
                                        HttpServletRequest httpRequest) {
        CandidatePrivacySettings firm = requireConsentMode(workspaceId);
        Set<ConsentPurpose> asked = personalPurposesOf(wirePurposes, firm);
        budget.hold(workspaceId);
        Person person = people.requireInWorkspace(personId, workspaceId);
        Instant now = now();
        Set<ConsentPurpose> askable = EnumSet.noneOf(ConsentPurpose.class);
        ConsentRequestSkipReason skip = skipReasonOf(person,
                suppression.suppressedPeopleOf(workspaceId, List.of(person)), consents.consentsOf(personId), asked,
                firm, now, askable);
        if (skip == null && budget.remainingToday(workspaceId) <= 0) {
            skip = ConsentRequestSkipReason.DAILY_CAP;
        }
        if (skip != null) {
            throw refusalOf(skip);
        }
        queue(userId, workspaceId, Map.of(person, askable), firm, false, now, httpRequest);
        return records.toRecord(userId, person);
    }

    /** Everyone it can, in the order asked; who is skipped is answered with why, never dropped silently. */
    @Transactional
    public BulkConsentRequestResponse requestEach(UUID userId, UUID workspaceId, List<UUID> personIds,
                                                  List<String> wirePurposes, HttpServletRequest httpRequest) {
        CandidatePrivacySettings firm = requireConsentMode(workspaceId);
        Set<ConsentPurpose> asked = personalPurposesOf(wirePurposes, firm);
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
            ConsentRequestSkipReason skip = skipReasonOf(person, suppressed, held.get(personId), asked, firm, now,
                    askable);
            if (skip == null && asking.size() >= capacity) {
                skip = ConsentRequestSkipReason.DAILY_CAP;
            }
            if (skip != null) {
                skipped.add(new ConsentRequestSkipResponse(personId, skip.value()));
                continue;
            }
            asking.put(person, askable);
        }
        queue(userId, workspaceId, asking, firm, false, now, httpRequest);
        return new BulkConsentRequestResponse(asking.size(), skipped);
    }

    /** Told another way — a call, a letter, the firm's own form. Only the answer and the day are kept. */
    @Transactional
    public PersonRecordResponse recordByHand(UUID userId, UUID workspaceId, UUID personId,
                                             RecordConsentRequest request, HttpServletRequest httpRequest) {
        CandidatePrivacySettings firm = settings.settingsOf(workspaceId);
        ConsentPurpose purpose = ApiValueEnum.require(ConsentPurpose.class, request.purpose(), "purpose");
        if (purpose == ConsentPurpose.SHARE_WITH_CLIENT) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "purpose",
                    "Record a share from the position it is for");
        }
        ConsentStatus answer = ApiValueEnum.require(ConsentStatus.class, request.answer(), "answer");
        if (!ANSWERS_BY_HAND.contains(answer)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "answer", "Choose given, declined or withdrawn");
        }
        if (request.answeredOn().isAfter(LocalDate.now(clock).plusDays(1))) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "answeredOn", "Choose today or an earlier day");
        }
        Person person = people.requireInWorkspace(personId, workspaceId);
        Instant answeredAt = request.answeredOn().atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
        Instant validUntil = answer == ConsentStatus.GIVEN && purpose == ConsentPurpose.KEEP_ON_FILE
                ? answeredAt.atZone(ZoneOffset.UTC).plusMonths(firm.keepOnFileMonths()).toInstant() : null;
        UUID consentId = consents.recordByHand(workspaceId, personId, purpose, null, answer, answeredAt, validUntil);
        consents.appendEvent(workspaceId, personId, consentId, ConsentEventAction.RECORDED, answer,
                ConsentEventVia.BY_HAND, null, userId, null, null, now());
        activity.record(person, null, userId, PersonActivityKind.CONSENT_ANSWERED,
                PersonActivityDetails.of("purpose", purpose.value()).and("status", answer.value())
                        .and("via", "by_hand"));
        audit.event(ProjectEventType.PERSON_CONSENT_RECORDED)
                .actor(userId).workspace(workspaceId).target(PERSON_TARGET, personId).from(httpRequest)
                .detail("purpose", purpose.value())
                .detail("status", answer.value())
                .record();
        return records.toRecord(userId, person);
    }

    /**
     * The daily close: requests past their 30 days and consents past their date become expired. Nothing else about
     * the person changes — not their basis, their retention or what a client sees.
     */
    @Transactional
    public int closeLapsed(UUID workspaceId, Instant now) {
        List<PersonConsent> expired = new ArrayList<>(consents.closeLapsedRequests(workspaceId, now, SCAN_BATCH));
        expired.addAll(consents.expireLapsedConsents(workspaceId, now, SCAN_BATCH));
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
        List<UUID> due = consents.peopleDueRenewal(workspaceId, now,
                now.plus(Duration.ofDays(firm.renewalDaysBefore())), SCAN_BATCH);
        if (due.isEmpty()) {
            return 0;
        }
        List<Person> found = people.findByWorkspaceIdAndIdIn(workspaceId, due);
        Set<UUID> suppressed = suppression.suppressedPeopleOf(workspaceId, found);
        int capacity = budget.remainingToday(workspaceId);
        Map<Person, Set<ConsentPurpose>> asking = new LinkedHashMap<>();
        for (Person person : found) {
            if (asking.size() >= capacity) {
                break;
            }
            if (PrivacyEmailBudget.unreachableReasonOf(person, suppressed) == null) {
                asking.put(person, EnumSet.of(ConsentPurpose.KEEP_ON_FILE));
            }
        }
        queue(null, workspaceId, asking, firm, true, now.truncatedTo(ChronoUnit.MICROS), null);
        return asking.size();
    }

    /**
     * Records every request as sent and hands the emails to the mailer for after the commit: a rolled-back request
     * must not reach anyone, and a refused one is undone there.
     */
    private void queue(UUID senderId, UUID workspaceId, Map<Person, Set<ConsentPurpose>> asking,
                       CandidatePrivacySettings firm, boolean renewal, Instant now, HttpServletRequest httpRequest) {
        if (asking.isEmpty()) {
            return;
        }
        Map<UUID, MintedPrivacyLink> links = privacyLinks.mintEach(workspaceId,
                asking.keySet().stream().map(Person::getId).toList(), PrivacyLinkPurpose.CONSENT, senderId, now,
                REQUEST_LIFETIME);
        Instant expiresAt = now.plus(REQUEST_LIFETIME);
        List<QueuedConsentRequest> queued = new ArrayList<>(asking.size());
        asking.forEach((person, purposes) -> {
            MintedPrivacyLink link = links.get(person.getId());
            List<ConsentPriorState> priors = new ArrayList<>();
            List<String> asks = new ArrayList<>();
            PersonConsent renewing = renewal ? keptOnFile(person.getId()) : null;
            for (ConsentPurpose purpose : purposes) {
                String sentence = renewing != null
                        ? ConsentWording.renewalSentenceOf(renewing.answeredAt(), renewedUntil(renewing, firm))
                        : ConsentWording.sentenceOf(purpose, firm.keepOnFileMonths(), null, null);
                asks.add(ConsentWording.askingOf(purpose, firm.keepOnFileMonths(), null, null));
                priors.add(consents.openRequest(workspaceId, person.getId(), purpose, null, senderId, link.id(), now,
                        expiresAt, false, ConsentWording.VERSION, sentence));
            }
            Instant previouslyNotifiedAt = person.getPrivacyNotifiedAt();
            PrivacyNoticeChannel previousChannel = person.getPrivacyNoticeChannel();
            person.recordNotified(now, PrivacyNoticeChannel.PRIVACY_EMAIL);
            String purposeTokens = purposes.stream().map(ConsentPurpose::value).collect(Collectors.joining(","));
            activity.record(person, null, senderId, PersonActivityKind.CONSENT_REQUESTED,
                    PersonActivityDetails.of("purposes", purposeTokens).and("renewal", renewal ? true : null));
            audit.event(ProjectEventType.PERSON_CONSENT_REQUESTED)
                    .actor(senderId).workspace(workspaceId).target(PERSON_TARGET, person.getId()).from(httpRequest)
                    .detail("purposes", purposeTokens)
                    .detail("renewal", renewal)
                    .record();
            String recipient = person.emailContacts().getFirst().getValue();
            String pageLink = baseUrl + "/privacy/" + link.token();
            EmailMessage email = renewing != null
                    ? templates.buildConsentRenewalEmail(recipient, person.getFullName(), firm.controllerName(),
                            firm.privacyContactEmail(), ConsentWording.dayOf(renewing.answeredAt()),
                            ConsentWording.dayOf(renewing.validUntil()), firm.keepOnFileMonths(), pageLink,
                            firm.noticeUrl(), ConsentWording.dayOf(expiresAt))
                    : templates.buildConsentRequestEmail(recipient, person.getFullName(), firm.controllerName(),
                            firm.privacyContactEmail(), PrivacyNoticeWording.sourceOf(person.getSource()), asks,
                            pageLink, firm.noticeUrl(), ConsentWording.dayOf(expiresAt));
            queued.add(new QueuedConsentRequest(link.id(), person.getId(), now, previouslyNotifiedAt,
                    previousChannel, List.copyOf(priors), email));
        });
        events.publishEvent(new ConsentRequestsQueued(workspaceId, senderId, List.copyOf(queued)));
    }

    /** The keep-on-file consent a renewal asks about; the scan only names people who hold one. */
    private PersonConsent keptOnFile(UUID personId) {
        return consents.consentsOf(personId).stream()
                .filter(consent -> consent.purpose() == ConsentPurpose.KEEP_ON_FILE && consent.projectId() == null
                        && consent.validUntil() != null && consent.answeredAt() != null)
                .findFirst().orElse(null);
    }

    /** A renewal extends from the day the consent ends, so answering early loses nothing. */
    static Instant renewedUntil(PersonConsent consent, CandidatePrivacySettings firm) {
        return consent.validUntil().atZone(ZoneOffset.UTC).plusMonths(firm.keepOnFileMonths()).toInstant();
    }

    /**
     * Fills {@code askable} with the purposes still worth asking: not already given beyond the renewal window, and not
     * asked within the last 14 days. Answers why nothing is, or null.
     */
    private ConsentRequestSkipReason skipReasonOf(Person person, Set<UUID> suppressed, List<PersonConsent> held,
                                                  Set<ConsentPurpose> asked, CandidatePrivacySettings firm,
                                                  Instant now, Set<ConsentPurpose> askable) {
        PrivacyNoticeSkipReason unreachable = PrivacyEmailBudget.unreachableReasonOf(person, suppressed);
        if (unreachable != null) {
            return ConsentRequestSkipReason.valueOf(unreachable.name());
        }
        Instant renewalWindowEnd = now.plus(Duration.ofDays(firm.renewalDaysBefore()));
        boolean anyRecentlyAsked = false;
        for (ConsentPurpose purpose : asked) {
            PersonConsent consent = held.stream()
                    .filter(candidate -> candidate.purpose() == purpose && candidate.projectId() == null)
                    .findFirst().orElse(null);
            if (consent != null && consent.isLiveAt(now)
                    && (consent.validUntil() == null || consent.validUntil().isAfter(renewalWindowEnd))) {
                continue;
            }
            if (consent != null && consent.isOpenAt(now) && consent.requestedAt() != null
                    && consent.requestedAt().isAfter(now.minus(RECENTLY))) {
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

    /** The purposes a person is asked about here: offered by the firm, and never a share, which is a position's. */
    private static Set<ConsentPurpose> personalPurposesOf(List<String> wirePurposes, CandidatePrivacySettings firm) {
        Set<ConsentPurpose> purposes = EnumSet.noneOf(ConsentPurpose.class);
        for (String wire : wirePurposes) {
            ConsentPurpose purpose = ApiValueEnum.require(ConsentPurpose.class, wire, "purpose");
            if (purpose == ConsentPurpose.SHARE_WITH_CLIENT) {
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

    /** Microseconds, as the columns keep them, so a refused send can tell its own telling from a later one. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
