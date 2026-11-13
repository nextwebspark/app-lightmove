package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ContactKind;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.PrivacyRequestKind;
import app.lightmove.api.candidate.constant.SuppressionReason;
import app.lightmove.api.candidate.dto.PrivacyPageResponse;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidatePrivacySettings;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonPrivacyRequested;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
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
    private static final String OBJECTION_REASON = "Objected through their privacy page";

    /** A second click on the same request within a day tells nobody again. */
    private static final Duration REPEAT_WINDOW = Duration.ofDays(1);

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

    public PrivacyPageService(TenantResolvers resolvers, RateLimitGuard rateLimit, NamedParameterJdbcTemplate jdbc,
                              PersonRepository people, CandidatePrivacySettingsService settings,
                              SuppressionListService suppression, PersonActivityRecorder activity, AuditService audit,
                              ApplicationEventPublisher events, Clock clock,
                              PlatformTransactionManager transactionManager) {
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
    }

    public PrivacyPageResponse open(String token, HttpServletRequest request) {
        String tokenHash = Tokens.hash(token);
        rateLimit.checkPrivacyPageOpen(tokenHash, request);
        return inLinksWorkspace(tokenHash, link -> {
            CandidatePrivacySettings firm = settings.settingsOf(link.workspaceId());
            if (link.isExpired(clock.instant())) {
                return new PrivacyPageResponse(true, firm.controllerName(), firm.privacyContactEmail(),
                        firm.noticeUrl(), null, null, null, List.of(), link.expiresAt());
            }
            jdbc.update("UPDATE app_lm_person_privacy_link SET last_opened_at = now() WHERE id = :id",
                    new MapSqlParameterSource("id", link.id()));
            Person person = people.requireInWorkspace(link.personId(), link.workspaceId());
            return new PrivacyPageResponse(false, firm.controllerName(), firm.privacyContactEmail(), firm.noticeUrl(),
                    PersonPrivacyService.basisOf(person, firm).value(), person.getSource().value(),
                    person.getCreatedAt(), categoriesOf(person), link.expiresAt());
        });
    }

    /** Do not contact, and the suppression list as an objection, so no door files them again. Asking twice is fine. */
    public void object(String token, HttpServletRequest request) {
        String tokenHash = Tokens.hash(token);
        rateLimit.checkPrivacyPageRequest(tokenHash, request);
        inLinksWorkspace(tokenHash, link -> {
            Person person = liveLinksPerson(link);
            boolean marked = person.markDoNotContact(OBJECTION_REASON, null);
            int suppressed = suppression.suppressPerson(person, null, SuppressionReason.OBJECTED);
            if (marked || suppressed > 0) {
                activity.record(person, null, null, PersonActivityKind.PRIVACY_OBJECTED, PersonActivityDetails.none());
                audit.event(ProjectEventType.PERSON_OBJECTED)
                        .workspace(link.workspaceId()).target(PERSON_TARGET, person.getId()).from(request)
                        .detail("suppressionEntries", suppressed)
                        .record();
                events.publishEvent(new PersonPrivacyRequested(link.workspaceId(), person.getId(),
                        PrivacyRequestKind.OBJECTION));
            }
            return null;
        });
    }

    /** A copy or a deletion: told to the admins, answered by a person, never fulfilled from here. */
    public void request(String token, String wireKind, HttpServletRequest request) {
        PrivacyRequestKind kind = ApiValueEnum.require(PrivacyRequestKind.class, wireKind, "request");
        if (kind == PrivacyRequestKind.OBJECTION) {
            object(token, request);
            return;
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

    private <T> T inLinksWorkspace(String tokenHash, Function<PrivacyLink, T> work) {
        ResolvedRow resolved = resolvers.personPrivacyLink(tokenHash)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return WorkspaceScope.call(resolved.workspaceId(), () -> transactions.execute(status -> {
            PrivacyLink link = jdbc.query("""
                    SELECT id, workspace_id, person_id, expires_at FROM app_lm_person_privacy_link WHERE id = :id
                    """, new MapSqlParameterSource("id", resolved.id()), (row, index) -> new PrivacyLink(
                    row.getObject("id", UUID.class), row.getObject("workspace_id", UUID.class),
                    row.getObject("person_id", UUID.class), row.getTimestamp("expires_at").toInstant()))
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

    private record PrivacyLink(UUID id, UUID workspaceId, UUID personId, Instant expiresAt) {

        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
