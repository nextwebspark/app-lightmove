package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.sourcing.constant.FoundBy;
import app.lightmove.api.enrichment.sourcing.constant.SuggestionVerdict;
import app.lightmove.api.enrichment.sourcing.model.Suggestion;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What each run found at each company (V209), written by the worker in its own short transactions and read back
 * by the panel. Every read carries the workspace and the project — suggestions are mandate content.
 */
@Component
@RequiredArgsConstructor
public class SuggestionStore {

    private static final String INSERT = """
            INSERT INTO app_lm_executive_sourcing_suggestion (workspace_id, project_id, run_id, triage_company_id,
                                                              linkedin_slug, verdict, score, rank, reasons, found_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (run_id, triage_company_id, linkedin_slug) DO NOTHING
            """;

    private static final String SELECT = """
            SELECT run_id, triage_company_id, linkedin_slug, verdict, score, rank, reasons, found_by, created_at
            FROM app_lm_executive_sourcing_suggestion
            WHERE workspace_id = ? AND project_id = ? AND run_id = ANY (?) AND triage_company_id = ANY (?)
            ORDER BY triage_company_id, rank
            """;

    private final JdbcTemplate jdbc;

    /** One company's people as one run found them; a company searched again by the same run keeps its first answer. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(UUID workspaceId, UUID projectId, UUID runId, UUID triageCompanyId, List<Suggestion> found) {
        jdbc.batchUpdate(INSERT, found, found.size(), (ps, suggestion) -> {
            ps.setObject(1, workspaceId);
            ps.setObject(2, projectId);
            ps.setObject(3, runId);
            ps.setObject(4, triageCompanyId);
            ps.setString(5, suggestion.linkedinSlug());
            ps.setString(6, suggestion.verdict().name());
            if (suggestion.score() == null) {
                ps.setNull(7, Types.SMALLINT);
            } else {
                ps.setShort(7, suggestion.score().shortValue());
            }
            ps.setShort(8, (short) suggestion.rank());
            ps.setArray(9, ps.getConnection().createArrayOf("text", suggestion.reasons().toArray(String[]::new)));
            ps.setString(10, suggestion.foundBy().name());
        });
    }

    /** The people the given runs found at the given companies, each company's in rank order. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<StoredSuggestion> foundBy(UUID workspaceId, UUID projectId, Collection<UUID> runIds,
                                          Collection<UUID> triageCompanyIds) {
        if (runIds.isEmpty() || triageCompanyIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(SELECT);
            ps.setObject(1, workspaceId);
            ps.setObject(2, projectId);
            ps.setArray(3, con.createArrayOf("uuid", runIds.toArray()));
            ps.setArray(4, con.createArrayOf("uuid", triageCompanyIds.toArray()));
            return ps;
        }, (rs, row) -> stored(rs));
    }

    /** The latest run that found this person at this company since {@code freshAfter}: what Add may file, and for which run. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<UUID> runThatFound(UUID workspaceId, UUID projectId, UUID triageCompanyId, String linkedinSlug,
                                       Instant freshAfter) {
        return jdbc.query("""
                        SELECT run_id FROM app_lm_executive_sourcing_suggestion
                        WHERE workspace_id = ? AND project_id = ? AND triage_company_id = ?
                          AND lower(linkedin_slug) = lower(?) AND created_at > ?
                        ORDER BY created_at DESC LIMIT 1
                        """,
                (rs, row) -> rs.getObject("run_id", UUID.class), workspaceId, projectId, triageCompanyId,
                linkedinSlug, Timestamp.from(freshAfter)).stream().findFirst();
    }

    /** Forgets an erased person wherever a run of the workspace found them. Rides the erasure's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void forget(UUID workspaceId, String linkedinSlug) {
        jdbc.update("DELETE FROM app_lm_executive_sourcing_suggestion"
                + " WHERE workspace_id = ? AND lower(linkedin_slug) = lower(?)", workspaceId, linkedinSlug);
    }

    /** Lets go of what was found before {@code cutoff}: the people cache no longer holds who they were. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeCreatedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM app_lm_executive_sourcing_suggestion WHERE created_at < ?",
                Timestamp.from(cutoff));
    }

    private static StoredSuggestion stored(ResultSet rs) throws SQLException {
        int score = rs.getInt("score");
        Integer scoreOrNull = rs.wasNull() ? null : score;
        return new StoredSuggestion(rs.getObject("run_id", UUID.class), rs.getObject("triage_company_id", UUID.class),
                new Suggestion(rs.getString("linkedin_slug"), SuggestionVerdict.valueOf(rs.getString("verdict")),
                        scoreOrNull, rs.getInt("rank"), reasonsOf(rs.getArray("reasons")),
                        FoundBy.valueOf(rs.getString("found_by"))),
                rs.getTimestamp("created_at").toInstant());
    }

    private static List<String> reasonsOf(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        for (Object reason : (Object[]) array.getArray()) {
            reasons.add((String) reason);
        }
        return reasons;
    }

    /** One stored suggestion and where it came from. */
    public record StoredSuggestion(UUID runId, UUID triageCompanyId, Suggestion suggestion, Instant createdAt) {}
}
