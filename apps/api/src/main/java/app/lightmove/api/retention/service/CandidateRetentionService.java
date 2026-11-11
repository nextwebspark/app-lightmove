package app.lightmove.api.retention.service;

import app.lightmove.api.candidate.service.PersonErasureService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RetentionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.retention.dto.CandidateRetentionResponse;
import app.lightmove.api.retention.dto.RetentionDuePersonResponse;
import app.lightmove.api.retention.dto.RetentionDueResponse;
import app.lightmove.api.retention.dto.RetentionEraseResponse;
import app.lightmove.api.retention.dto.RetentionKeepResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate retention (#921): never deletes on its own; an admin deletes each due person through
 * {@link PersonErasureService} or keeps them. Due: last touch older than the period, no live position, no restriction.
 * Reads run in the workspace scope; without one, row-level security shows nobody due.
 */
@Service
public class CandidateRetentionService {

    private static final String KEPT_KIND = "RETENTION_KEPT";
    public static final int MAX_PAGE = 500;

    /**
     * Over {@code p}, the person, bound to {@code :workspaceId} and {@code :cutoff}. Each touch is its own range test
     * rather than one {@code greatest(…)}, so the latest line is an index probe and only the due are read further.
     */
    private static final String DUE_WHERE = """
            WHERE p.workspace_id = :workspaceId
              AND NOT p.processing_restricted
              AND p.created_at < :cutoff
              AND (p.retention_kept_at IS NULL OR p.retention_kept_at < :cutoff)
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_person_activity a WHERE a.person_id = p.id AND a.occurred_at >= :cutoff)
              AND NOT EXISTS (
                  SELECT 1 FROM app_lm_project_candidate c JOIN app_lm_project pr ON pr.id = c.project_id
                  WHERE c.person_id = p.id AND pr.archived_at IS NULL)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonErasureService erasure;
    private final AuditService audit;
    private final Clock clock;
    private final RetentionSettings settings;

    public CandidateRetentionService(NamedParameterJdbcTemplate jdbc, PersonErasureService erasure,
                                     AuditService audit, Clock clock, LightMoveProperties properties) {
        this.jdbc = jdbc;
        this.erasure = erasure;
        this.audit = audit;
        this.clock = clock;
        this.settings = properties.retention();
    }

    @Transactional(readOnly = true)
    public CandidateRetentionResponse period(UUID workspaceId) {
        Optional<ChosenPeriod> chosen = chosenPeriodOf(workspaceId);
        // Not Optional.map: a chosen "never" is a null period, which map would read as no choice at all.
        Integer months = chosen.isPresent() ? chosen.get().months() : settings.candidateMonths();
        return new CandidateRetentionResponse(months, chosen.isPresent(), settings.candidateMonths(),
                RetentionSettings.CANDIDATE_MONTHS);
    }

    /** Recorded only where something changed, as every workspace setting is. */
    @Transactional
    public CandidateRetentionResponse changePeriod(UUID actorId, UUID workspaceId, Integer months,
                                                   HttpServletRequest httpRequest) {
        if (months != null && !RetentionSettings.CANDIDATE_MONTHS.contains(months)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "months",
                    "Choose 12, 24, 36 or 60 months, or never");
        }
        if (chosenPeriodOf(workspaceId).filter(held -> Objects.equals(held.months(), months)).isPresent()) {
            return period(workspaceId);
        }
        jdbc.update("""
                INSERT INTO app_lm_workspace_retention (workspace_id, candidate_retention_months)
                VALUES (:workspaceId, :months)
                ON CONFLICT (workspace_id) DO UPDATE SET candidate_retention_months = EXCLUDED.candidate_retention_months
                """, new MapSqlParameterSource("workspaceId", workspaceId).addValue("months", months));
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(httpRequest)
                .detail("section", "retention")
                .detail("months", months == null ? "never" : months.toString())
                .record();
        return period(workspaceId);
    }

    @Transactional(readOnly = true)
    public RetentionDueResponse due(UUID workspaceId, int limit, int offset) {
        Integer months = period(workspaceId).months();
        if (months == null) {
            return new RetentionDueResponse(null, null, 0, List.of());
        }
        Instant cutoff = cutoffOf(months);
        MapSqlParameterSource parameters = dueParameters(workspaceId, cutoff)
                .addValue("limit", limit)
                .addValue("offset", offset)
                .addValue("keptKind", KEPT_KIND);
        Long total = jdbc.queryForObject("SELECT count(*) FROM app_lm_person p " + DUE_WHERE, parameters, Long.class);
        List<RetentionDuePersonResponse> people = jdbc.query("""
                SELECT p.id, p.full_name, p.title, owner.full_name AS owner_name,
                       greatest(last.occurred_at, p.retention_kept_at, p.created_at) AS touched_at,
                       CASE WHEN p.retention_kept_at >= coalesce(last.occurred_at, '-infinity')
                            THEN :keptKind ELSE last.kind END AS touched_by,
                       (SELECT count(*) FROM app_lm_project_candidate c WHERE c.person_id = p.id) AS positions,
                       coalesce((SELECT c.company_name FROM app_lm_project_candidate c
                                 WHERE c.person_id = p.id AND c.company_name <> ''
                                 ORDER BY c.created_at DESC LIMIT 1),
                                p.profile -> 'career' -> 0 ->> 'company') AS employer
                FROM app_lm_person p
                LEFT JOIN LATERAL (
                    SELECT a.kind, a.occurred_at FROM app_lm_person_activity a
                    WHERE a.person_id = p.id ORDER BY a.occurred_at DESC, a.id DESC LIMIT 1) last ON true
                LEFT JOIN app_lm_user owner ON owner.id = p.owner_user_id
                """ + DUE_WHERE + """
                ORDER BY touched_at, p.id
                LIMIT :limit OFFSET :offset
                """, parameters, (row, index) -> new RetentionDuePersonResponse(
                        row.getObject("id", UUID.class), row.getString("full_name"), row.getString("title"),
                        row.getString("employer"), row.getTimestamp("touched_at").toInstant(),
                        row.getString("touched_by"), row.getInt("positions"), row.getString("owner_name")));
        return new RetentionDueResponse(months, cutoff, total == null ? 0 : total, people);
    }

    /**
     * Deliberately not {@code @Transactional}: each person is erased in a transaction of their own
     * ({@link PersonErasureService#eraseUnderRetention}), so one still being emailed waits for the next review rather
     * than keeping everyone else. Whether they are still due is asked again once erasure holds their row.
     */
    public RetentionEraseResponse erase(UUID actorId, UUID workspaceId, List<UUID> personIds,
                                        HttpServletRequest httpRequest) {
        Integer months = period(workspaceId).months();
        if (months == null) {
            return new RetentionEraseResponse(0, personIds.size(), 0);
        }
        Instant cutoff = cutoffOf(months);
        int erased = 0;
        int noLongerDue = 0;
        int sendInFlight = 0;
        for (UUID personId : personIds.stream().distinct().toList()) {
            try {
                if (erasure.eraseUnderRetention(actorId, workspaceId, personId,
                        held -> isDue(workspaceId, held, cutoff), httpRequest)) {
                    erased++;
                } else {
                    noLongerDue++;
                }
            } catch (ApiException refused) {
                if (refused.getCode() != ErrorCode.PERSON_ERASE_SEND_IN_FLIGHT) {
                    throw refused;
                }
                sendInFlight++;
            }
        }
        return new RetentionEraseResponse(erased, noLongerDue, sendInFlight);
    }

    /** Anyone of the workspace ticked, due or not: a keep only ever puts deletion further off. */
    @Transactional
    public RetentionKeepResponse keep(UUID actorId, UUID workspaceId, List<UUID> personIds,
                                      HttpServletRequest httpRequest) {
        List<UUID> kept = jdbc.queryForList("""
                UPDATE app_lm_person SET retention_kept_at = :now, retention_kept_by = :actorId
                WHERE workspace_id = :workspaceId AND id IN (:personIds)
                RETURNING id
                """, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("now", Timestamp.from(clock.instant()))
                .addValue("actorId", actorId)
                .addValue("personIds", personIds.stream().distinct().toList()), UUID.class);
        kept.forEach(personId -> audit.event(ProjectEventType.PERSON_RETENTION_KEPT)
                .actor(actorId).workspace(workspaceId).target("person", personId).from(httpRequest)
                .record());
        return new RetentionKeepResponse(kept.size());
    }

    private boolean isDue(UUID workspaceId, UUID personId, Instant cutoff) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM app_lm_person p " + DUE_WHERE
                        + " AND p.id = :personId)",
                dueParameters(workspaceId, cutoff).addValue("personId", personId), Boolean.class));
    }

    /** Empty where the workspace has not chosen. */
    private Optional<ChosenPeriod> chosenPeriodOf(UUID workspaceId) {
        return jdbc.query("""
                SELECT candidate_retention_months FROM app_lm_workspace_retention WHERE workspace_id = :workspaceId
                """, new MapSqlParameterSource("workspaceId", workspaceId),
                (row, index) -> new ChosenPeriod(row.getObject("candidate_retention_months", Integer.class)))
                .stream().findFirst();
    }

    private Instant cutoffOf(int months) {
        return clock.instant().atZone(ZoneOffset.UTC).minusMonths(months).toInstant();
    }

    private static MapSqlParameterSource dueParameters(UUID workspaceId, Instant cutoff) {
        return new MapSqlParameterSource("workspaceId", workspaceId).addValue("cutoff", Timestamp.from(cutoff));
    }

    /** A workspace's own choice; null months is never. */
    private record ChosenPeriod(Integer months) {}
}
