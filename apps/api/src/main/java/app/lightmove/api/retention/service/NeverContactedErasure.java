package app.lightmove.api.retention.service;

import app.lightmove.api.candidate.constant.ConsentEventAction;
import app.lightmove.api.candidate.constant.ConsentStatus;
import app.lightmove.api.candidate.constant.NoteOutcome;
import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.service.PersonErasureService;
import app.lightmove.api.core.audit.constant.PersonErasureReason;
import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RetentionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.outreach.constant.MessageDirection;
import app.lightmove.api.outreach.constant.MessageStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Erases the grey and never contacted (#1490) without review, so it keeps anyone on a live position, restricted, do
 * not contact or ever kept by an admin. Exists only with both {@code RETENTION_ENABLED} and
 * {@code DATA_PROTECTION_ENABLED}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"lightmove.retention.enabled", DataProtectionSettings.SWITCH}, havingValue = "true")
public class NeverContactedErasure {

    private static final List<String> CONTACT_NOTE_KINDS = Arrays.stream(PersonNoteKind.values())
            .filter(kind -> kind != PersonNoteKind.GENERAL).map(Enum::name).toList();
    private static final List<String> UNANSWERED_OUTCOMES = NoteOutcome.UNANSWERED.stream().map(Enum::name).toList();
    private static final List<String> ANSWERING_CONSENT_ACTIONS =
            List.of(ConsentEventAction.ANSWERED.name(), ConsentEventAction.RECORDED.name());

    /**
     * Over {@code p}, every value bound by {@link #parameters}. Told is contacted: a privacy notice, a footer, a
     * consent request or a colleague's mark all set {@code privacy_notified_at}. A call that rang out never reached
     * them. An admin's Keep is for good: this rule asks nobody, so it never overrides one, however long ago.
     */
    private static final String DUE_WHERE = """
            WHERE p.workspace_id = :workspaceId
              AND p.privacy_notified_at IS NULL
              AND NOT p.processing_restricted
              AND NOT p.do_not_contact
              AND p.uncava_notice_sent_at IS NULL
              AND p.created_at < :cutoff
              AND p.retention_kept_at IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_project_candidate c JOIN app_lm_project pr ON pr.id = c.project_id
                  WHERE c.person_id = p.id AND (pr.archived_at IS NULL OR pr.archived_at >= :cutoff))
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_person_consent k WHERE k.person_id = p.id AND k.status = :consentGiven)
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_person_consent_event e
                  WHERE e.person_id = p.id AND e.action IN (:answeringConsentActions))
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_outreach_message m
                  WHERE m.person_id = p.id
                    AND ((m.direction = :outbound AND m.status = :sent) OR m.direction = :inbound))
              AND NOT EXISTS (SELECT 1 FROM app_lm_person_meeting mt WHERE mt.person_id = p.id)
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_person_note n
                  WHERE n.person_id = p.id AND n.deleted_at IS NULL AND n.kind IN (:contactNoteKinds)
                    AND (n.outcome IS NULL OR n.outcome NOT IN (:unansweredOutcomes)))
            """;

    private final EveryWorkspace workspaces;
    private final NamedParameterJdbcTemplate jdbc;
    private final PersonErasureService erasure;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${lightmove.retention.never-contacted-scan}", zone = "UTC")
    public void scheduledPass() {
        eraseAt(clock.instant());
    }

    /** The period in force, null while the pass erases nobody. */
    public Integer months() {
        return properties.retention().neverContactedMonths();
    }

    /** Each person in a transaction of their own; one workspace failing never stops the rest. */
    public int eraseAt(Instant now) {
        RetentionSettings settings = properties.retention();
        if (settings.neverContactedMonths() == null) {
            return 0;
        }
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(settings.neverContactedMonths()).toInstant();
        int erased = 0;
        for (UUID workspaceId : workspaces.liveIds()) {
            int budget = settings.neverContactedPerRun() - erased;
            if (budget <= 0) {
                break;
            }
            erased += workspaces.sum(List.of(workspaceId), id -> eraseIn(id, cutoff, budget));
        }
        if (erased > 0) {
            log.info("Erased {} never-contacted people past {}", erased, cutoff);
        }
        return erased;
    }

    private int eraseIn(UUID workspaceId, Instant cutoff, int budget) {
        List<UUID> due = jdbc.queryForList("SELECT p.id FROM app_lm_person p " + DUE_WHERE
                + " ORDER BY p.created_at, p.id LIMIT :limit", parameters(workspaceId, cutoff)
                .addValue("limit", budget), UUID.class);
        int erased = 0;
        for (UUID personId : due) {
            try {
                if (erasure.eraseUnderRetention(null, workspaceId, personId,
                        held -> isDue(workspaceId, held, cutoff), null, PersonErasureReason.NEVER_CONTACTED)) {
                    erased++;
                }
            } catch (ApiException refused) {
                if (refused.getCode() != ErrorCode.PERSON_ERASE_SEND_IN_FLIGHT) {
                    throw refused;
                }
            }
        }
        return erased;
    }

    private boolean isDue(UUID workspaceId, UUID personId, Instant cutoff) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM app_lm_person p " + DUE_WHERE
                + " AND p.id = :personId)", parameters(workspaceId, cutoff).addValue("personId", personId),
                Boolean.class));
    }

    private static MapSqlParameterSource parameters(UUID workspaceId, Instant cutoff) {
        return new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("contactNoteKinds", CONTACT_NOTE_KINDS)
                .addValue("unansweredOutcomes", UNANSWERED_OUTCOMES)
                .addValue("consentGiven", ConsentStatus.GIVEN.name())
                .addValue("answeringConsentActions", ANSWERING_CONSENT_ACTIONS)
                .addValue("outbound", MessageDirection.OUTBOUND.name())
                .addValue("inbound", MessageDirection.INBOUND.name())
                .addValue("sent", MessageStatus.SENT.name());
    }
}
