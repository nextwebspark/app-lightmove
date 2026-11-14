package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.LawfulBasis;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.PrivacyLinkPurpose;
import app.lightmove.api.candidate.constant.PrivacyNoticeChannel;
import app.lightmove.api.candidate.constant.PrivacyNoticeSkipReason;
import app.lightmove.api.candidate.dto.BulkPrivacyNoticeResponse;
import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.dto.PrivacyNoticeSkipResponse;
import app.lightmove.api.candidate.model.CandidatePrivacySettings;
import app.lightmove.api.candidate.model.MintedPrivacyLink;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PrivacyNoticesQueued;
import app.lightmove.api.candidate.model.QueuedPrivacyNotice;
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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's lawful basis and Art. 14 notice (#923): recording the basis, marking them told by hand, and sending them a
 * privacy notice through Uncava in the firm's name. Each change is a timeline line and an audit event, by id. The
 * emails themselves leave after the commit, through {@link PrivacyNoticeMailer}.
 */
@Service
public class PersonPrivacyService {

    private static final String PERSON_TARGET = "person";
    private static final Duration LINK_LIFETIME = Duration.ofDays(30);
    private static final Duration RECENTLY = Duration.ofDays(14);

    private final PersonRepository people;
    private final PersonRecordService records;
    private final PersonActivityRecorder activity;
    private final CandidatePrivacySettingsService settings;
    private final SuppressionGate suppression;
    private final NamedParameterJdbcTemplate jdbc;
    private final PrivacyLinkStore privacyLinks;
    private final EmailTemplates templates;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final Clock clock;
    private final String baseUrl;
    private final int noticesPerDay;

    public PersonPrivacyService(PersonRepository people, PersonRecordService records, PersonActivityRecorder activity,
                                CandidatePrivacySettingsService settings, SuppressionGate suppression,
                                NamedParameterJdbcTemplate jdbc, PrivacyLinkStore privacyLinks,
                                EmailTemplates templates,
                                ApplicationEventPublisher events, AuditService audit, Clock clock,
                                LightMoveProperties properties) {
        this.people = people;
        this.records = records;
        this.activity = activity;
        this.settings = settings;
        this.suppression = suppression;
        this.jdbc = jdbc;
        this.privacyLinks = privacyLinks;
        this.templates = templates;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
        this.baseUrl = properties.web().baseUrl();
        this.noticesPerDay = properties.dataProtection().privacyNoticesPerDay();
    }

    @Transactional
    public PersonRecordResponse changeLawfulBasis(UUID userId, UUID workspaceId, UUID personId, String wireBasis,
                                                  HttpServletRequest httpRequest) {
        LawfulBasis basis = wireBasis == null || wireBasis.isBlank() ? null
                : ApiValueEnum.require(LawfulBasis.class, wireBasis, "lawful basis");
        Person person = people.requireInWorkspace(personId, workspaceId);
        if (person.recordLawfulBasis(basis, userId)) {
            activity.record(person, null, userId, PersonActivityKind.LAWFUL_BASIS_CHANGED,
                    PersonActivityDetails.of("basis", basis));
            audit.event(ProjectEventType.PERSON_LAWFUL_BASIS_CHANGED)
                    .actor(userId).workspace(workspaceId).target(PERSON_TARGET, personId).from(httpRequest)
                    .detail("basis", basis == null ? "default" : basis.value())
                    .record();
        }
        return records.toRecord(userId, person);
    }

    /** Told another way: a call, a meeting, the firm's own email. Only the day is kept, never what was said. */
    @Transactional
    public PersonRecordResponse markNotified(UUID userId, UUID workspaceId, UUID personId, LocalDate notifiedOn,
                                             HttpServletRequest httpRequest) {
        // A day ahead of UTC is still today somewhere a consultant works.
        if (notifiedOn.isAfter(LocalDate.now(clock).plusDays(1))) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "notifiedOn", "Choose today or an earlier day");
        }
        Person person = people.requireInWorkspace(personId, workspaceId);
        person.recordNotified(notifiedOn.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC),
                PrivacyNoticeChannel.MARKED_BY_HAND);
        activity.record(person, null, userId, PersonActivityKind.PRIVACY_NOTICE_MARKED,
                PersonActivityDetails.of("notifiedOn", notifiedOn.toString()));
        audit.event(ProjectEventType.PERSON_PRIVACY_NOTICE_MARKED)
                .actor(userId).workspace(workspaceId).target(PERSON_TARGET, personId).from(httpRequest)
                .record();
        return records.toRecord(userId, person);
    }

    @Transactional
    public PersonRecordResponse sendNotice(UUID userId, UUID workspaceId, UUID personId,
                                           HttpServletRequest httpRequest) {
        CandidatePrivacySettings firm = requireReady(workspaceId);
        holdDailyBudget(workspaceId);
        Person person = people.requireInWorkspace(personId, workspaceId);
        PrivacyNoticeSkipReason skip = skipReasonOf(person,
                suppression.suppressedPeopleOf(workspaceId, List.of(person)));
        if (skip == null && sentToday(workspaceId) >= noticesPerDay) {
            skip = PrivacyNoticeSkipReason.DAILY_CAP;
        }
        if (skip != null) {
            throw refusalOf(skip);
        }
        queue(userId, workspaceId, List.of(person), firm, httpRequest);
        return records.toRecord(userId, person);
    }

    /** Everyone it can, in the order asked; who is skipped is answered with why, never dropped silently. */
    @Transactional
    public BulkPrivacyNoticeResponse sendNotices(UUID userId, UUID workspaceId, List<UUID> personIds,
                                                 HttpServletRequest httpRequest) {
        CandidatePrivacySettings firm = requireReady(workspaceId);
        holdDailyBudget(workspaceId);
        Set<UUID> asked = new LinkedHashSet<>(personIds);
        Map<UUID, Person> found = people.findByWorkspaceIdAndIdIn(workspaceId, asked).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        Set<UUID> suppressed = suppression.suppressedPeopleOf(workspaceId, found.values());
        int capacity = noticesPerDay - sentToday(workspaceId);
        List<Person> sending = new ArrayList<>();
        List<PrivacyNoticeSkipResponse> skipped = new ArrayList<>();
        for (UUID personId : asked) {
            Person person = found.get(personId);
            if (person == null) {
                throw ApiException.of(ErrorCode.NOT_FOUND);
            }
            PrivacyNoticeSkipReason skip = skipReasonOf(person, suppressed);
            if (skip == null && sending.size() >= capacity) {
                skip = PrivacyNoticeSkipReason.DAILY_CAP;
            }
            if (skip != null) {
                skipped.add(new PrivacyNoticeSkipResponse(personId, skip.value()));
                continue;
            }
            sending.add(person);
        }
        queue(userId, workspaceId, sending, firm, httpRequest);
        return new BulkPrivacyNoticeResponse(sending.size(), skipped);
    }

    /** The basis in force: the person's own, else the workspace's default. */
    public static LawfulBasis basisOf(Person person, CandidatePrivacySettings firm) {
        return person.getLawfulBasis() != null ? person.getLawfulBasis() : firm.defaultLawfulBasis();
    }

    /**
     * Records every notice as sent and hands the emails to the mailer for after the commit: a rolled-back send must
     * not reach anyone, and a refused one is undone there.
     */
    private void queue(UUID userId, UUID workspaceId, List<Person> recipients, CandidatePrivacySettings firm,
                       HttpServletRequest httpRequest) {
        if (recipients.isEmpty()) {
            return;
        }
        // Microseconds, as the column keeps it, so a refused send can tell its own telling from a later one.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<QueuedPrivacyNotice> queued = new ArrayList<>(recipients.size());
        Map<UUID, MintedPrivacyLink> links = privacyLinks.mintEach(workspaceId,
                recipients.stream().map(Person::getId).toList(), PrivacyLinkPurpose.NOTICE, userId, now, LINK_LIFETIME);
        for (Person person : recipients) {
            MintedPrivacyLink link = links.get(person.getId());
            Instant previouslyNotifiedAt = person.getPrivacyNotifiedAt();
            PrivacyNoticeChannel previousChannel = person.getPrivacyNoticeChannel();
            person.recordNotified(now, PrivacyNoticeChannel.PRIVACY_EMAIL);
            activity.record(person, null, userId, PersonActivityKind.PRIVACY_NOTICE_SENT,
                    PersonActivityDetails.of("channel", PrivacyNoticeChannel.PRIVACY_EMAIL));
            audit.event(ProjectEventType.PERSON_PRIVACY_NOTICE_SENT)
                    .actor(userId).workspace(workspaceId).target(PERSON_TARGET, person.getId()).from(httpRequest)
                    .record();
            EmailMessage email = templates.buildPrivacyNoticeEmail(person.emailContacts().getFirst().getValue(),
                    firm.controllerName(), firm.privacyContactEmail(),
                    PrivacyNoticeWording.sourceOf(person.getSource()),
                    PrivacyNoticeWording.basisOf(basisOf(person, firm)), baseUrl + "/privacy/" + link.token(),
                    firm.noticeUrl());
            queued.add(new QueuedPrivacyNotice(link.id(), person.getId(), now, previouslyNotifiedAt, previousChannel,
                    email));
        }
        events.publishEvent(new PrivacyNoticesQueued(workspaceId, userId, List.copyOf(queued)));
    }

    private PrivacyNoticeSkipReason skipReasonOf(Person person, Set<UUID> suppressed) {
        if (person.emailContacts().isEmpty()) {
            return PrivacyNoticeSkipReason.NO_EMAIL;
        }
        if (person.isDoNotContact()) {
            return PrivacyNoticeSkipReason.DO_NOT_CONTACT;
        }
        if (person.isProcessingRestricted()) {
            return PrivacyNoticeSkipReason.PROCESSING_RESTRICTED;
        }
        if (suppressed.contains(person.getId())) {
            return PrivacyNoticeSkipReason.SUPPRESSED;
        }
        Instant told = person.getPrivacyNotifiedAt();
        if (told != null && told.isAfter(clock.instant().minus(RECENTLY))) {
            return PrivacyNoticeSkipReason.RECENTLY_NOTIFIED;
        }
        return null;
    }

    private static ApiException refusalOf(PrivacyNoticeSkipReason skip) {
        return ApiException.of(switch (skip) {
            case NO_EMAIL -> ErrorCode.PRIVACY_NOTICE_NO_EMAIL;
            case DO_NOT_CONTACT -> ErrorCode.PERSON_DO_NOT_CONTACT;
            case PROCESSING_RESTRICTED -> ErrorCode.PERSON_PROCESSING_RESTRICTED;
            case SUPPRESSED -> ErrorCode.PERSON_SUPPRESSED;
            case RECENTLY_NOTIFIED -> ErrorCode.PRIVACY_NOTICE_RECENTLY_SENT;
            case DAILY_CAP -> ErrorCode.PRIVACY_NOTICE_DAILY_LIMIT;
        });
    }

    private CandidatePrivacySettings requireReady(UUID workspaceId) {
        CandidatePrivacySettings firm = settings.settingsOf(workspaceId);
        if (!firm.isReadyForNotices()) {
            throw ApiException.of(ErrorCode.CANDIDATE_PRIVACY_NOT_SET_UP);
        }
        return firm;
    }

    /**
     * Held until the commit, so two sends at once cannot each read the same count and together pass the cap, nor both
     * find the same person not yet told.
     */
    private void holdDailyBudget(UUID workspaceId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "privacy-notice:" + workspaceId));
    }

    /** Notices this workspace sent since midnight UTC; the cap is a brake on Uncava's sender, not a calendar. */
    private int sentToday(UUID workspaceId) {
        Instant midnight = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_person_privacy_link
                WHERE workspace_id = :workspaceId AND purpose = :purpose AND created_at >= :since
                """, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("purpose", PrivacyLinkPurpose.NOTICE.name())
                .addValue("since", Timestamp.from(midnight)), Integer.class);
        return count == null ? 0 : count;
    }
}
