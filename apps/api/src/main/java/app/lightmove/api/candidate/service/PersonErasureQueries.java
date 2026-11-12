package app.lightmove.api.candidate.service;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** The one scoping every erasure query binds: {@code :personId} within {@code :workspaceId}. */
public final class PersonErasureQueries {

    private PersonErasureQueries() {
    }

    public static MapSqlParameterSource scopeOf(UUID workspaceId, UUID personId) {
        return new MapSqlParameterSource("personId", personId).addValue("workspaceId", workspaceId);
    }

    public static long count(NamedParameterJdbcTemplate jdbc, String sql, MapSqlParameterSource scope) {
        Long counted = jdbc.queryForObject(sql, scope, Long.class);
        return counted == null ? 0 : counted;
    }
}
