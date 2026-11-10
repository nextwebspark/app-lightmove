package app.lightmove.api.notification.service;

import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The bell's copies of a person's name: a folded row lists everyone it gathered ({@code details.names}), so the name
 * is matched as text, the only thing the list keeps: a namesake's entry may go with it, costing a line, never a
 * record.
 */
@Component
@RequiredArgsConstructor
class PersonErasureNotifications implements PersonErasureContributor {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void erase(ErasedPerson erased) {
        if (erased.fullName() == null || erased.fullName().isBlank()) {
            return;
        }
        jdbc.update("""
                UPDATE app_lm_notification
                SET details = jsonb_set(details, '{names}', coalesce((
                        SELECT jsonb_agg(listed) FROM jsonb_array_elements(details -> 'names') AS listed
                        WHERE listed #>> '{}' <> :fullName), '[]'::jsonb))
                WHERE workspace_id = :workspaceId AND jsonb_exists(details -> 'names', :fullName)
                """, new MapSqlParameterSource("fullName", erased.fullName())
                .addValue("workspaceId", erased.workspaceId()));
    }
}
