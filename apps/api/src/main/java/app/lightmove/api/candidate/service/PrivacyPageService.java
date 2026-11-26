package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ConsentEventAction;
import app.lightmove.api.candidate.constant.ConsentEventVia;
import app.lightmove.api.candidate.constant.ConsentPurpose;
import app.lightmove.api.candidate.constant.ConsentStatus;
import app.lightmove.api.candidate.constant.ContactKind;
import app.lightmove.api.candidate.constant.DoNotContactReasons;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.PrivacyLinkPurpose;
import app.lightmove.api.candidate.constant.PrivacyRequestKind;
import app.lightmove.api.candidate.constant.SuppressionReason;
import app.lightmove.api.candidate.dto.ConsentAnswerRequest;
import app.lightmove.api.candidate.dto.PrivacyPageConsentResponse;
import app.lightmove.api.candidate.dto.PrivacyPageResponse;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidatePrivacySettings;
import app.lightmove.api.candidate.model.ConsentAnswered;
import app.lightmove.api.candidate.model.ConsentConfirmationQueued;
import app.lightmove.api.candidate.model.MintedPrivacyLink;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonConsent;
import app.lightmove.api.candidate.model.PersonOptedOut;
import app.lightmove.api.candidate.model.PersonPrivacyRequested;
import app.lightmove.api.candidate.model.ShareTarget;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.core.tenant.ResolvedRow;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.core.tenant.WorkspaceScope;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The public page a privacy notice links to (#923). No session: the token names one person in one workspace, so the
 * workspace is resolved from its hash first (V218's resolver) and everything else is read under that workspace's
 * fence. An objection is acted on at once; a copy or a deletion is a request the firm's admins answer.
 */
@Service
public class PrivacyPageService {

    private static final String PERSON_TARGET = "person";
    private static final ContactStop OBJECTION = new ContactStop(DoNotContactReasons.OBJECTED_THROUGH_PRIVACY_PAGE,
            PersonActivityKind.PRIVACY_OBJECTED, ProjectEventType.PERSON_OBJECTED);
    private static final ContactStop OPT_OUT = new ContactStop(DoNotContactReasons.OPTED_OUT_BY_EMAIL,
            PersonActivityKind.PRIVACY_OPTED_OUT, ProjectEventType.PERSON_OPTED_OUT);

    /** A second click on the same request within a day tells nobody again. */
    private static final Duration REPEAT_WINDOW = Duration.ofDays(1);
    private static final int MANAGE_LINK_MONTHS = 36;

    private final TenantResolvers resolvers;
    private final RateLimitGuard rateLimit;
    private final NamedParameterJdbcTemplate jdbc;
    private final PersonRepository people;
    private final CandidatePrivacySettingsService settings;
    private final SuppressionListService suppression;
    private final PersonActivityRecorder activity;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final PersonConsentStore consents;
    private final PrivacyLinkStore privacyLinks;
    private final EmailTemplates templates;
    private final ShareTargets shareTargets;
    private final String baseUrl;
    private final String candidateNoticeUrl;

    public PrivacyPageService(TenantResolvers resolvers, RateLimitGuard rateLimit, NamedParameterJdbcTemplate jdbc,
                              PersonRepository people, CandidatePrivacySettingsService settings,
                              SuppressionListService suppression, PersonActivityRecorder activity, AuditService audit,
                              ApplicationEventPublisher events, Clock clock,
                              PlatformTransactionManager transactionManager, PersonConsentStore consents,
                              PrivacyLinkStore privacyLinks, EmailTemplates templates, ShareTargets shareTargets,
                              LightMoveProperties properties) {
        this.resolvers = resolvers;
        this.rateLimit = rateLimit;
        this.jdbc = jdbc;
        this.people = people;
        this.settings = settings;
        this.suppression = suppression;
        this.activity = activity;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.consents = consents;
        this.privacyLinks = privacyLinks;
        this.templates = templates;
        this.shareTargets = shareTargets;
        this.baseUrl = properties.web().baseUrl();
        this.candidateNoticeUrl = properties.legal().candidateNoticeUrl();
    }

    public PrivacyPageResponse open(String token, HttpServletRequest request) {
        String tokenHash = Tokens.hash(token);
        rateLimit.checkPrivacyPageOpen(tokenHash, request);
        return inLinksWorkspace(tokenHash, link -> {
            CandidatePrivacySettings firm = settings.settingsOf(link.workspaceId());
            if (link.isExpired(clock.instant())) {
                return new PrivacyPageResponse(true, firm.controllerName(), firm.privacyContactEmail(),
                        firm.noticeUrl(), null, null, null, List.of(), link.expiresAt(), link.purpose().value(),
                        List.of(), candidateNoticeUrl);
            }
            jdbc.update("UPDATE app_lm_person_privacy_link SET last_opened_at = now() WHERE id = :id",
                    new MapSqlParameterSource("id", link.id()));
            Person person = people.requireInWorkspace(link.personId(), link.workspaceId());
            return new PrivacyPageResponse(false, firm.controllerName(), firm.privacyContactEmail(), firm.noticeUrl(),
                    PersonPrivacyService.basisOf(person, firm).value(), person.getSource().value(),
                    person.getCreatedAt(), categoriesOf(person), link.expiresAt(), link.purpose().value(),
                    consentsShownOn(link, person, firm), candidateNoticeUrl);
        });
    }

    /** Do not contact, and the suppression list as an objection, so no door files them again. Asking twice is fine. */
    public void object(String token, HttpServletRequest request) {
        String tokenHash = Tokens.hash(token);
        rateLimit.checkPrivacyPageRequest(tokenHash, request);
        inLinksWorkspace(tokenHash, link -> {
            Person person = liveLinksPerson(link);
            stopContact(link, person, false, OBJECTION, request,
                    new PersonPrivacyRequested(link.workspaceId(), person.getId(), PrivacyRequestKind.OBJECTION));
            return null;
        });
    }

    /**
     * The footer's link and a mail app's one-click Unsubscribe (RFC 8058) both land here; outreach stops the person's
     * runs in this transaction ({@link PersonOptedOut}). Only the first use of a link is recorded, so a mail app
     * posting twice tells nobody twice.
     */
    public void optOut(String token, HttpServletRequest request) {
        String tokenHash = Tokens.hash(token);
        rateLimit.checkPrivacyOptOut(tokenHash, request);
        inLinksWorkspace(tokenHash, link -> {
            Person person = liveLinksPerson(link);
            boolean linkFirstUsed = jdbc.update("""
                    UPDATE app_lm_person_privacy_link SET used_at = :usedAt WHERE id = :id AND used_at IS NULL
                    """, new MapSqlParameterSource("id", link.id())
                    .addValue("usedAt", Timestamp.from(clock.instant()))) > 0;
            stopContact(link, person, linkFirstUsed, OPT_OUT, request, new PersonOptedOut(link.workspaceId(),
                    person.getId(), person.getOwnerUserId() != null ? person.getOwnerUserId() : link.sentBy()));
            return null;
        });
    }

    /**
     * The person's answers (#1267): given or declined on a consent request while it is open, withdrawn on a manage
     * link. Each change is an event, a timeline line and an audit event with no actor; an answer already given is not
     * given twice. Every answer that changed something earns a confirmation, carrying the link to change their mind
     * while anything they agreed to is in force.
     */
    public void answer(String token, List<ConsentAnswerRequest> answers, HttpServletRequest request) {
        String tokenHash = Tokens.hash(token);
        rateLimit.checkPrivacyPageRequest(tokenHash, request);
        inLinksWorkspace(tokenHash, link -> {
            Person person = liveLinksPerson(link);
            Instant now = clock.instant();
            Map<UUID, PersonConsent> answerable = switch (link.purpose()) {
                case CONSENT -> consents.askedOnLink(link.id()).stream()
                        .collect(Collectors.toMap(PersonConsent::id, Function.identity()));
                case MANAGE -> consents.consentsOf(person.getId()).stream()
                        .filter(consent -> consent.isLiveAt(now))
                        .collect(Collectors.toMap(PersonConsent::id, Function.identity()));
                case NOTICE, OPT_OUT -> throw ApiException.of(ErrorCode.NOT_FOUND);
            };
            CandidatePrivacySettings firm = settings.settingsOf(link.workspaceId());
            List<AnsweredConsent> recorded = new ArrayList<>();
            UUID asker = null;
            for (ConsentAnswerRequest answer : answers) {
                PersonConsent consent = answerable.get(answer.consentId());
                if (consent == null) {
                    throw ApiException.of(ErrorCode.NOT_FOUND);
                }
                ConsentStatus status = answerOf(link.purpose(), answer.answer());
                Instant validUntil = validUntilOf(consent, status, firm, now);
                if (!recordAnswer(link, person, consent, status, validUntil, now, request)) {
                    continue;
                }
                asker = asker != null ? asker : consent.requestedBy();
                recorded.add(new AnsweredConsent(consent, status, validUntil, consent.isLiveAt(now)));
            }
            if (recorded.isEmpty()) {
                return null;
            }
            List<UUID> recipients = Stream.of(asker, person.getOwnerUserId(), link.sentBy())
                    .filter(Objects::nonNull).distinct().toList();
            if (!recipients.isEmpty()) {
                events.publishEvent(new ConsentAnswered(link.workspaceId(), person.getId(), recipients));
            }
            confirm(link, person, recorded, firm, now);
            return null;
        });
    }

    /** A copy or a deletion: told to the admins, answered by a person, never fulfilled from here. */
    public void request(String token, String wireKind, HttpServletRequest request) {
        PrivacyRequestKind kind = ApiValueEnum.require(PrivacyRequestKind.class, wireKind, "request");
        if (kind == PrivacyRequestKind.OBJECTION) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "kind", "Choose copy or deletion");
        }
        String tokenHash = Tokens.hash(token);
        rateLimit.checkPrivacyPageRequest(tokenHash, request);
        inLinksWorkspace(tokenHash, link -> {
            Person person = liveLinksPerson(link);
            if (askedRecently(person.getId(), kind)) {
                return null;
            }
            activity.record(person, null, null, PersonActivityKind.PRIVACY_REQUEST_RECEIVED,
                    PersonActivityDetails.of("kind", kind.value()));
            audit.event(ProjectEventType.PERSON_PRIVACY_REQUESTED)
                    .workspace(link.workspaceId()).target(PERSON_TARGET, person.getId()).from(request)
                    .detail("kind", kind.value())
                    .record();
            events.publishEvent(new PersonPrivacyRequested(link.workspaceId(), person.getId(), kind));
            return null;
        });
    }

    /**
     * Do not contact and the suppression list as an objection, so no door files them again. Recorded and announced
     * only when something changed.
     */
    private void stopContact(PrivacyLink link, Person person, boolean linkFirstUsed, ContactStop stop,
                             HttpServletRequest request, Object announcement) {
        boolean marked = person.markDoNotContact(stop.doNotContactReason(), null);
        int suppressed = suppression.suppressPerson(person, null, SuppressionReason.OBJECTED);
        if (!linkFirstUsed && !marked && suppressed == 0) {
            return;
        }
        activity.record(person, null, null, stop.activityKind(), PersonActivityDetails.none());
        audit.event(stop.auditType())
                .workspace(link.workspaceId()).target(PERSON_TARGET, person.getId()).from(request)
                .detail("suppressionEntries", suppressed)
                .record();
        events.publishEvent(announcement);
    }

    private boolean recordAnswer(PrivacyLink link, Person person, PersonConsent consent, ConsentStatus status,
                                 Instant validUntil, Instant now, HttpServletRequest request) {
        boolean renewalDeclined = status == ConsentStatus.DECLINED && consent.isLiveAt(now);
        boolean changed = status == ConsentStatus.WITHDRAWN ? consents.withdraw(consent.id(), person.getId(), now)
                : renewalDeclined ? consents.closeRenewal(consent.id(), link.id(), now)
                : consents.answer(consent.id(), link.id(), status, now, validUntil);
        if (!changed) {
            return false;
        }
        boolean answeredRequest = status != ConsentStatus.WITHDRAWN;
        consents.appendEvent(link.workspaceId(), person.getId(), consent.id(), ConsentEventAction.ANSWERED, status,
                ConsentEventVia.PAGE, link.id(), null,
                answeredRequest ? consent.requestWordingVersion() : consent.wordingVersion(),
                answeredRequest ? consent.requestWording() : consent.wording(), now);
        activity.record(person, consent.projectId(), null, PersonActivityKind.CONSENT_ANSWERED,
                PersonActivityDetails.of("purpose", consent.purpose().value()).and("status", status.value())
                        .and("via", "page"));
        audit.event(ProjectEventType.PERSON_CONSENT_ANSWERED)
                .workspace(link.workspaceId()).target(PERSON_TARGET, person.getId()).from(request)
                .detail("purpose", consent.purpose().value())
                .detail("status", status.value())
                .record();
        return true;
    }

    /** How long a yes lasts is what the request said when it was sent, never what the settings say today. */
    private static Instant validUntilOf(PersonConsent consent, ConsentStatus status, CandidatePrivacySettings firm,
                                        Instant now) {
        if (status != ConsentStatus.GIVEN || consent.purpose() != ConsentPurpose.KEEP_ON_FILE) {
            return null;
        }
        int months = promisedMonthsOf(consent, firm);
        if (consent.isLiveAt(now) && consent.validUntil() != null) {
            return PersonConsentService.renewedUntil(consent, months);
        }
        return now.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    private static int promisedMonthsOf(PersonConsent consent, CandidatePrivacySettings firm) {
        return consent.requestMonths() != null ? consent.requestMonths() : firm.keepOnFileMonths();
    }

    private static ConsentStatus answerOf(PrivacyLinkPurpose linkPurpose, String wire) {
        ConsentStatus status = ApiValueEnum.require(ConsentStatus.class, wire, "answer");
        boolean allowed = linkPurpose == PrivacyLinkPurpose.MANAGE
                ? status == ConsentStatus.WITHDRAWN
                : status == ConsentStatus.GIVEN || status == ConsentStatus.DECLINED;
        if (!allowed) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "answer",
                    linkPurpose == PrivacyLinkPurpose.MANAGE ? "Choose withdrawn" : "Choose given or declined");
        }
        return status;
    }

    /**
     * The confirmation of what was just answered. While anything they agreed to is in force it carries a manage link,
     * lasting as long as the longest of it; a consent with no end date (market updates last until withdrawn) counts as
     * {@link #MANAGE_LINK_MONTHS} from now, so withdrawing stays as easy as agreeing (Art. 7(3)).
     */
    private void confirm(PrivacyLink link, Person person, List<AnsweredConsent> recorded,
                         CandidatePrivacySettings firm, Instant now) {
        if (person.emailContacts().isEmpty()) {
            return;
        }
        List<PersonConsent> live = consents.consentsOf(person.getId()).stream()
                .filter(consent -> consent.isLiveAt(now)).toList();
        String manageLink = null;
        String manageUntil = null;
        if (!live.isEmpty()) {
            Instant undatedUntil = now.atZone(ZoneOffset.UTC).plusMonths(MANAGE_LINK_MONTHS).toInstant();
            Instant lastsUntil = live.stream()
                    .map(consent -> consent.validUntil() != null ? consent.validUntil() : undatedUntil)
                    .max(Instant::compareTo).orElseThrow();
            MintedPrivacyLink manage = privacyLinks.mint(link.workspaceId(), person.getId(),
                    PrivacyLinkPurpose.MANAGE, null, now, Duration.between(now, lastsUntil));
            manageLink = baseUrl + "/privacy/" + manage.token();
            manageUntil = ConsentWording.dayOf(lastsUntil);
        }
        Map<UUID, ShareTarget> targets = shareTargets.each(recorded.stream()
                .map(answered -> answered.consent().projectId()).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<String> lines = recorded.stream().map(answered -> answered.line(targets)).toList();
        events.publishEvent(new ConsentConfirmationQueued(link.workspaceId(), person.getId(),
                templates.buildConsentConfirmationEmail(person.emailContacts().getFirst().getValue(),
                        person.getFullName(), firm.controllerName(), firm.privacyContactEmail(),
                        ConsentWording.dayOf(now), lines, manageLink, manageUntil, firm.noticeUrl())));
    }

    /**
     * What the page lists: a request's own purposes, or a manage link's consents in force. Never a value held. A
     * request shows the words it asked, and what was answered on it, so a reopened link says what the person chose.
     */
    private List<PrivacyPageConsentResponse> consentsShownOn(PrivacyLink link, Person person,
                                                             CandidatePrivacySettings firm) {
        Instant now = clock.instant();
        boolean request = link.purpose() == PrivacyLinkPurpose.CONSENT;
        List<PersonConsent> shown = switch (link.purpose()) {
            case CONSENT -> consents.askedOnLink(link.id());
            case MANAGE -> consents.consentsOf(person.getId()).stream().filter(consent -> consent.isLiveAt(now))
                    .toList();
            case NOTICE, OPT_OUT -> List.of();
        };
        Map<UUID, ConsentStatus> answeredHere = request ? consents.answersOnLink(link.id()) : Map.of();
        Map<UUID, ShareTarget> targets = shareTargets.each(shown.stream().map(PersonConsent::projectId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return shown.stream().map(consent -> {
            boolean open = request && consent.isOpenAt(now) && link.id().equals(consent.requestLinkId());
            ConsentStatus answer = answeredHere.get(consent.id());
            ShareTarget target = consent.projectId() == null ? null : targets.get(consent.projectId());
            return new PrivacyPageConsentResponse(consent.id(), consent.purpose().value(), consent.status().value(),
                    open, request ? consent.requestWording() : consent.wording(), consent.validUntil(),
                    consent.answeredAt(),
                    consent.purpose() == ConsentPurpose.KEEP_ON_FILE ? promisedMonthsOf(consent, firm) : null,
                    answer == null ? null : answer.value(),
                    target == null ? null : target.roleTitle(),
                    target == null ? null : target.clientNamedOrNull(request ? consent.requestClientNamed()
                            : consent.clientNamed()));
        }).toList();
    }

    private <T> T inLinksWorkspace(String tokenHash, Function<PrivacyLink, T> work) {
        ResolvedRow resolved = resolvers.personPrivacyLink(tokenHash)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return WorkspaceScope.call(resolved.workspaceId(), () -> transactions.execute(status -> {
            PrivacyLink link = jdbc.query("""
                    SELECT id, workspace_id, person_id, sent_by, expires_at, purpose FROM app_lm_person_privacy_link
                    WHERE id = :id
                    """, new MapSqlParameterSource("id", resolved.id()), (row, index) -> new PrivacyLink(
                    row.getObject("id", UUID.class), row.getObject("workspace_id", UUID.class),
                    row.getObject("person_id", UUID.class), row.getObject("sent_by", UUID.class),
                    row.getTimestamp("expires_at").toInstant(), PrivacyLinkPurpose.valueOf(row.getString("purpose"))))
                    .stream().findFirst().orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            return work.apply(link);
        }));
    }

    private Person liveLinksPerson(PrivacyLink link) {
        if (link.isExpired(clock.instant())) {
            throw ApiException.of(ErrorCode.PRIVACY_LINK_EXPIRED);
        }
        return people.requireInWorkspace(link.personId(), link.workspaceId());
    }

    private boolean askedRecently(UUID personId, PrivacyRequestKind kind) {
        Boolean asked = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM app_lm_person_activity
                               WHERE person_id = :personId AND kind = :activityKind AND details ->> 'kind' = :kind
                                 AND occurred_at >= :since)
                """, new MapSqlParameterSource("personId", personId)
                .addValue("activityKind", PersonActivityKind.PRIVACY_REQUEST_RECEIVED.name())
                .addValue("kind", kind.value())
                .addValue("since", Timestamp.from(clock.instant().minus(REPEAT_WINDOW))), Boolean.class);
        return Boolean.TRUE.equals(asked);
    }

    /** The kinds of data held, as fixed tokens; never a value, since the link can be forwarded. */
    private List<String> categoriesOf(Person person) {
        List<String> categories = new ArrayList<>(List.of("name_and_role"));
        if (!person.getProfile().career().isEmpty()) {
            categories.add("career");
        }
        if (person.getLocationCity() != null || person.getLocationCountry() != null) {
            categories.add("location");
        }
        List<CandidateContact> emails = person.emailContacts();
        if (emails.stream().anyMatch(email -> email.getKind() != ContactKind.PERSONAL)) {
            categories.add("work_email");
        }
        if (emails.stream().anyMatch(email -> email.getKind() == ContactKind.PERSONAL)) {
            categories.add("personal_email");
        }
        if (!person.phoneContacts().isEmpty()) {
            categories.add("phone");
        }
        if (person.getLinkedinUrl() != null) {
            categories.add("linkedin");
        }
        CandidateCompensation pay = person.compensation();
        if (pay.baseSalary() != null || pay.bonus() != null || pay.allowances() != null
                || pay.longTermIncentive() != null || pay.noticePeriod() != null) {
            categories.add("compensation");
        }
        if (person.getNationality() != null || person.getGender() != null || person.getYearsExperience() != null) {
            categories.add("background");
        }
        MapSqlParameterSource byPerson = new MapSqlParameterSource("personId", person.getId());
        if (exists("SELECT 1 FROM app_lm_person_note WHERE person_id = :personId", byPerson)) {
            categories.add("notes");
        }
        if (exists("SELECT 1 FROM app_lm_person_document WHERE person_id = :personId", byPerson)) {
            categories.add("documents");
        }
        if (exists("""
                SELECT 1 FROM app_lm_candidate_scorecard s JOIN app_lm_project_candidate c ON c.id = s.candidate_id
                WHERE c.person_id = :personId""", byPerson)) {
            categories.add("assessments");
        }
        return categories;
    }

    private boolean exists(String query, MapSqlParameterSource parameters) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (" + query + ")", parameters, Boolean.class));
    }

    private record ContactStop(String doNotContactReason, PersonActivityKind activityKind,
                               ProjectEventType auditType) {
    }

    /** {@code wasLive}: in force when answered, so a no only closed its renewal and it runs to its own date. */
    private record AnsweredConsent(PersonConsent consent, ConsentStatus status, Instant validUntil, boolean wasLive) {

        String line(Map<UUID, ShareTarget> targets) {
            ShareTarget target = consent.projectId() == null ? null : targets.get(consent.projectId());
            String label = ConsentWording.labelOf(consent.purpose(),
                    target == null ? null : target.clientNamedOrNull(status == ConsentStatus.WITHDRAWN
                            ? consent.clientNamed() : consent.requestClientNamed()),
                    target == null ? null : target.roleTitle());
            return label + ": " + switch (status) {
                case GIVEN -> validUntil == null ? "Yes" : "Yes, until " + ConsentWording.dayOf(validUntil);
                case DECLINED -> wasLive && consent.validUntil() != null
                        ? "Not renewed, ends on " + ConsentWording.dayOf(consent.validUntil())
                        : "No";
                default -> "Withdrawn";
            };
        }
    }

    private record PrivacyLink(UUID id, UUID workspaceId, UUID personId, UUID sentBy, Instant expiresAt,
                               PrivacyLinkPurpose purpose) {

        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
