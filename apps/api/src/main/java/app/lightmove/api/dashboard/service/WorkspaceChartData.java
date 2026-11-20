package app.lightmove.api.dashboard.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The workspace queries' aggregates over executives across positions, each one grouped statement. Every statement
 * names the workspace and the positions the caller may browse — row-level security behind it is the backstop, not
 * the filter — and runs under a {@value #TIMEOUT_SECONDS}-second limit, so a dashboard can never hold a connection
 * the rest of the app is waiting for.
 */
@Component
class WorkspaceChartData {

    static final int TIMEOUT_SECONDS = 5;

    private final NamedParameterJdbcTemplate jdbc;

    WorkspaceChartData(DataSource dataSource) {
        JdbcTemplate bounded = new JdbcTemplate(dataSource);
        bounded.setQueryTimeout(TIMEOUT_SECONDS);
        this.jdbc = new NamedParameterJdbcTemplate(bounded);
    }

    record StageCount(String stage, int position, long executives) {}

    record LabelledCount(String label, long count) {}

    record UnitStageCount(String unit, String stage, int position, long executives) {}

    List<StageCount> executivesByStage(UUID workspaceId, Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT st.label AS stage, min(st.position) AS position, count(*) AS executives
                FROM app_lm_project_candidate c
                JOIN app_lm_workspace_candidate_status s ON s.id = c.status_id
                JOIN app_lm_workspace_candidate_stage st ON st.id = s.stage_id
                WHERE c.workspace_id = :workspaceId AND c.project_id IN (:projectIds)
                GROUP BY st.label
                ORDER BY min(st.position), st.label""",
                scope(workspaceId, projectIds),
                (row, index) -> new StageCount(row.getString("stage"), row.getInt("position"),
                        row.getLong("executives")));
    }

    List<UnitStageCount> executivesByUnitAndStage(UUID workspaceId, Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT cl.name AS unit, st.label AS stage, min(st.position) AS position, count(*) AS executives
                FROM app_lm_project_candidate c
                JOIN app_lm_project p ON p.id = c.project_id AND p.workspace_id = c.workspace_id
                JOIN app_lm_client cl ON cl.id = p.client_id
                JOIN app_lm_workspace_candidate_status s ON s.id = c.status_id
                JOIN app_lm_workspace_candidate_stage st ON st.id = s.stage_id
                WHERE c.workspace_id = :workspaceId AND c.project_id IN (:projectIds)
                GROUP BY cl.name, st.label
                ORDER BY cl.name, min(st.position)""",
                scope(workspaceId, projectIds),
                (row, index) -> new UnitStageCount(row.getString("unit"), row.getString("stage"),
                        row.getInt("position"), row.getLong("executives")));
    }

    /** Executives filed from {@code since}, counted per day (UTC) so the caller can bucket weeks itself. */
    List<LabelledCount> executivesFiledPerDay(UUID workspaceId, Collection<UUID> projectIds, Instant since) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = scope(workspaceId, projectIds).addValue("since",
                java.sql.Timestamp.from(since));
        return jdbc.query("""
                SELECT to_char((c.created_at AT TIME ZONE 'UTC')::date, 'YYYY-MM-DD') AS day, count(*) AS executives
                FROM app_lm_project_candidate c
                WHERE c.workspace_id = :workspaceId AND c.project_id IN (:projectIds) AND c.created_at >= :since
                GROUP BY 1
                ORDER BY 1""",
                parameters,
                (row, index) -> new LabelledCount(row.getString("day"), row.getLong("executives")));
    }

    /** Executives each member filed from {@code since}, most first; a departed member keeps their name. */
    List<LabelledCount> executivesFiledPerMember(UUID workspaceId, Collection<UUID> projectIds, Instant since) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = scope(workspaceId, projectIds).addValue("since",
                java.sql.Timestamp.from(since));
        return jdbc.query("""
                SELECT u.full_name AS member, count(*) AS executives
                FROM app_lm_project_candidate c
                JOIN app_lm_user u ON u.id = c.added_by
                WHERE c.workspace_id = :workspaceId AND c.project_id IN (:projectIds) AND c.created_at >= :since
                GROUP BY u.id, u.full_name
                ORDER BY count(*) DESC, u.full_name""",
                parameters,
                (row, index) -> new LabelledCount(row.getString("member"), row.getLong("executives")));
    }

    private static MapSqlParameterSource scope(UUID workspaceId, Collection<UUID> projectIds) {
        return new MapSqlParameterSource().addValue("workspaceId", workspaceId).addValue("projectIds", projectIds);
    }
}
