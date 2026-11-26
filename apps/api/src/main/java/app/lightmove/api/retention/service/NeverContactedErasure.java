package app.lightmove.api.retention.service;

import app.lightmove.api.candidate.service.PersonErasureService;
import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RetentionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.tenant.EveryWorkspace;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Erases people held without consent and never contacted (#1490, decisions B2, B6 and E1): grey, never told, never
 * reached, never answering, six months after they were added or after the last position they were on was archived.
 * Unlike the candidate review it asks nobody, so it keeps anyone on a live position, restricted, marked do not contact
 * or kept by an admin. Exists only with both {@code RETENTION_ENABLED} and {@code DATA_PROTECTION_ENABLED} on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"lightmove.retention.enabled", DataProtectionSettings.SWITCH}, havingValue = "true")
public class NeverContactedErasure {

    public static final String REASON = "retention-never-contacted";
    private static final List<String> CONTACT_NOTE_KINDS = List.of("CALL", "MEETING", "EMAIL", "LINKEDIN", "WHATSAPP");
    private static final List<String> UNANSWERED_OUTCOMES = List.of("NO_ANSWER", "WRONG_NUMBER");

    /**
     * Over {@code p}, bound to {@code :workspaceId} and {@code :cutoff}. Told is contacted: a privacy notice, a footer, a
     * consent request or a colleague's mark all set {@code privacy_notified_at}. A call that rang out never reached them.
     */
    private static final String DUE_WHERE = """
            WHERE p.workspace_id = :workspaceId
              AND p.privacy_notified_at IS NULL
              AND NOT p.processing_restricted
              AND NOT p.do_not_contact
              AND p.uncava_notice_sent_at IS NULL
              AND p.created_at < :cutoff
              AND (p.retention_kept_at IS NULL OR p.retention_kept_at < :cutoff)
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_project_candidate c JOIN app_lm_project pr ON pr.id = c.project_id
                  WHERE c.person_id = p.id AND (pr.archived_at IS NULL OR pr.archived_at >= :cutoff))
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_person_consent k WHERE k.person_id = p.id AND k.status = 'GIVEN')
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_person_consent_event e
                  WHERE e.person_id = p.id AND e.action IN ('ANSWERED', 'RECORDED'))
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_outreach_message m
                  WHERE m.person_id = p.id
                    AND ((m.direction = 'OUTBOUND' AND m.status = 'SENT') OR m.direction = 'INBOUND'))
              AND NOT EXISTS (SELECT 1 FROM app_lm_person_meeting mt WHERE mt.person_id = p.id)
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_person_note n
                  WHERE n.person_id = p.id AND n.deleted_at IS NULL AND n.kind IN (:contactNoteKinds)
                    AND (n.outcome IS NULL OR n.outcome NOT IN (:unansweredOutcomes)))
            """;

    private final EveryWorkspace workspaces;
    private final JdbcTemplate plainJdbc;
    private final NamedParameterJdbcTemplate jdbc;
    private final PersonErasureService erasure;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${lightmove.retention.never-contacted-scan}", zone = "UTC")
    public void scheduledPass() {
        eraseAt(clock.instant());
    }

    /** Each person in a transaction of their own; one workspace failing never stops the rest. */
    public int eraseAt(Instant now) {
        RetentionSettings settings = properties.retention();
        if (settings.neverContactedMonths() == null) {
            return 0;
        }
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(settings.neverContactedMonths()).toInstant();
        AtomicInteger budget = new AtomicInteger(settings.neverContactedPerRun());
        int erased = workspaces.sum(liveWorkspaces(), workspaceId -> eraseIn(workspaceId, cutoff, budget));
        if (erased > 0) {
            log.info("Erased {} never-contacted people past {}", erased, cutoff);
        }
        return erased;
    }

    private int eraseIn(UUID workspaceId, Instant cutoff, AtomicInteger budget) {
        if (budget.get() <= 0) {
            return 0;
        }
        List<UUID> due = jdbc.queryForList("SELECT p.id FROM app_lm_person p " + DUE_WHERE
                + " ORDER BY p.created_at, p.id LIMIT :limit", parameters(workspaceId, cutoff)
                .addValue("limit", budget.get()), UUID.class);
        int erased = 0;
        for (UUID personId : due) {
            try {
                if (erasure.eraseUnderRetention(null, workspaceId, personId,
                        held -> isDue(workspaceId, held, cutoff), null, REASON)) {
                    erased++;
                    budget.decrementAndGet();
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

    /** A deleted workspace is the purge's to empty, not this pass's. */
    private List<UUID> liveWorkspaces() {
        return plainJdbc.queryForList("SELECT id FROM app_lm_workspace WHERE deleted_at IS NULL ORDER BY id",
                UUID.class);
    }

    private static MapSqlParameterSource parameters(UUID workspaceId, Instant cutoff) {
        return new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("contactNoteKinds", CONTACT_NOTE_KINDS)
                .addValue("unansweredOutcomes", UNANSWERED_OUTCOMES);
    }
}
