package app.lightmove.api.task.service;

import app.lightmove.api.candidate.constant.ErasedHolding;
import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** A person's tasks, counted for their erasure's warning; V133's cascade deletes them with the person. */
@Component
@RequiredArgsConstructor
class PersonErasureTasks implements PersonErasureContributor {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Map<ErasedHolding, Long> holdingsOf(UUID workspaceId, UUID personId) {
        Long tasks = jdbc.queryForObject("SELECT count(*) FROM app_lm_task WHERE person_id = :personId"
                        + " AND workspace_id = :workspaceId",
                new MapSqlParameterSource("personId", personId).addValue("workspaceId", workspaceId), Long.class);
        return Map.of(ErasedHolding.TASKS, tasks == null ? 0 : tasks);
    }

    @Override
    public void erase(ErasedPerson erased) {
        // Nothing beyond the cascade: a task is always about one person, and goes with them.
    }
}
