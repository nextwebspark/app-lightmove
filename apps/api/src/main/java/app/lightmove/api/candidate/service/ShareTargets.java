package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.ShareTarget;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads what a share consent says about its position: the role, the client and the brief's confidentiality. */
@Component
@RequiredArgsConstructor
class ShareTargets {

    private static final String SELECT = """
            SELECT p.id, p.position_title, c.name AS client_name, coalesce(pos.confidential, false) AS confidential
            FROM app_lm_project p
            JOIN app_lm_client c ON c.id = p.client_id
            LEFT JOIN app_lm_position pos ON pos.project_id = p.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    ShareTarget require(UUID workspaceId, UUID projectId) {
        return jdbc.query(SELECT + " WHERE p.id = :projectId AND p.workspace_id = :workspaceId",
                        new MapSqlParameterSource("projectId", projectId).addValue("workspaceId", workspaceId),
                        (row, index) -> targetOf(row.getObject("id", UUID.class), row.getString("position_title"),
                                row.getString("client_name"), row.getBoolean("confidential")))
                .stream().findFirst().orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    Map<UUID, ShareTarget> each(Collection<UUID> projectIds) {
        Map<UUID, ShareTarget> targets = new LinkedHashMap<>();
        if (projectIds.isEmpty()) {
            return targets;
        }
        jdbc.query(SELECT + " WHERE p.id IN (:projectIds)", new MapSqlParameterSource("projectIds", projectIds),
                (row, index) -> targetOf(row.getObject("id", UUID.class), row.getString("position_title"),
                        row.getString("client_name"), row.getBoolean("confidential")))
                .forEach(target -> targets.put(target.projectId(), target));
        return targets;
    }

    private static ShareTarget targetOf(UUID projectId, String roleTitle, String clientName, boolean confidential) {
        return new ShareTarget(projectId, roleTitle, clientName, confidential);
    }
}
