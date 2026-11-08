package app.lightmove.api.candidate.service;

import static app.lightmove.api.candidate.service.PersonErasureQueries.count;
import static app.lightmove.api.candidate.service.PersonErasureQueries.scopeOf;

import app.lightmove.api.candidate.constant.ErasedHolding;
import app.lightmove.api.core.error.constant.ErrorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** What an erasure would take, counted for its warning and its audit line, and what would stop it now. */
@Component
@RequiredArgsConstructor
class PersonErasureHoldings {

    private final NamedParameterJdbcTemplate jdbc;
    private final List<PersonErasureContributor> contributors;

    Map<ErasedHolding, Long> holdingsOf(UUID workspaceId, UUID personId) {
        MapSqlParameterSource person = scopeOf(workspaceId, personId);
        Map<ErasedHolding, Long> holdings = new EnumMap<>(ErasedHolding.class);
        holdings.put(ErasedHolding.POSITIONS, count(jdbc, """
                SELECT count(*) FROM app_lm_project_candidate WHERE person_id = :personId
                """, person));
        holdings.put(ErasedHolding.NOTES, count(jdbc, """
                SELECT count(*) FROM app_lm_person_note
                WHERE person_id = :personId AND workspace_id = :workspaceId AND deleted_at IS NULL
                """, person));
        holdings.put(ErasedHolding.DOCUMENTS, count(jdbc, """
                SELECT count(*) FROM app_lm_person_document WHERE person_id = :personId AND workspace_id = :workspaceId
                """, person));
        holdings.put(ErasedHolding.FILES, count(jdbc, """
                SELECT count(*) FROM app_lm_person_document_version WHERE person_id = :personId
                """, person));
        contributors.forEach(contributor -> contributor.holdingsOf(workspaceId, personId)
                .forEach((holding, counted) -> holdings.merge(holding, counted, Long::sum)));
        return holdings;
    }

    Optional<ErrorCode> blockerOf(UUID workspaceId, UUID personId) {
        return contributors.stream()
                .map(contributor -> contributor.blockerOf(workspaceId, personId))
                .flatMap(Optional::stream)
                .findFirst();
    }
}
