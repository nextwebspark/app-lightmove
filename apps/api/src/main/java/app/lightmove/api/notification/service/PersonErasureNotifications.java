package app.lightmove.api.notification.service;

import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The bell's copies of a person's name. A row about them goes by cascade, but a folded row keeps the names of
 * everyone it gathered ({@code details.names}) under whoever came last, so an erased name is taken out of every row
 * of the workspace that still lists it. Matched on the name itself, since the list keeps nothing else: a namesake's
 * entry may go with it, which costs a line of the bell and never a record.
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
