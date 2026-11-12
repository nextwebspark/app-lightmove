package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.workspace.model.InferredBackgroundCounts;
import app.lightmove.api.workspace.service.InferredBackgroundEraser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Clears in bulk what the AI left on a workspace's people when it turns an inference off. The person's version is
 * bumped so a drawer save racing the clear fails rather than writing the value back; V91's frozen copy on the mapping
 * is cleared alongside, since it holds the same guess.
 */
@Component
@RequiredArgsConstructor
class InferredBackgroundEraserAdapter implements InferredBackgroundEraser {

    private static final String GENDER = BackgroundField.GENDER.key();
    private static final String NATIONALITY = BackgroundField.NATIONALITY.key();

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public InferredBackgroundCounts heldIn(UUID workspaceId) {
        MapSqlParameterSource scope = scopeOf(workspaceId);
        return new InferredBackgroundCounts(
                PersonErasureQueries.count(jdbc, """
                        SELECT count(*) FROM app_lm_person
                        WHERE workspace_id = :workspaceId AND jsonb_exists(ai_inferred_fields, :gender)
                        """, scope),
                PersonErasureQueries.count(jdbc, """
                        SELECT count(*) FROM app_lm_person
                        WHERE workspace_id = :workspaceId
                          AND (jsonb_exists(ai_inferred_fields, :nationality) OR ai_nationality_reading IS NOT NULL)
                        """, scope));
    }

    @Override
    public InferredBackgroundCounts clear(UUID workspaceId, boolean gender, boolean nationality) {
        MapSqlParameterSource scope = scopeOf(workspaceId);
        long genderCleared = 0;
        long nationalityCleared = 0;
        if (gender) {
            genderCleared = jdbc.update("""
                    UPDATE app_lm_person
                    SET gender = NULL, ai_inferred_fields = ai_inferred_fields - CAST(:gender AS text),
                        version = version + 1
                    WHERE workspace_id = :workspaceId AND jsonb_exists(ai_inferred_fields, :gender)
                    """, scope);
            jdbc.update("""
                    UPDATE app_lm_project_candidate
                    SET gender = NULL, ai_inferred_fields = ai_inferred_fields - CAST(:gender AS text)
                    WHERE workspace_id = :workspaceId AND jsonb_exists(ai_inferred_fields, :gender)
                    """, scope);
        }
        if (nationality) {
            nationalityCleared = jdbc.update("""
                    UPDATE app_lm_person
                    SET nationality = CASE WHEN jsonb_exists(ai_inferred_fields, :nationality) THEN NULL
                                       ELSE nationality END,
                        ai_inferred_fields = ai_inferred_fields - CAST(:nationality AS text),
                        ai_nationality_reading = NULL,
                        version = version + 1
                    WHERE workspace_id = :workspaceId
                      AND (jsonb_exists(ai_inferred_fields, :nationality) OR ai_nationality_reading IS NOT NULL)
                    """, scope);
            jdbc.update("""
                    UPDATE app_lm_project_candidate
                    SET nationality = CASE WHEN jsonb_exists(ai_inferred_fields, :nationality) THEN NULL
                                       ELSE nationality END,
                        ai_inferred_fields = ai_inferred_fields - CAST(:nationality AS text),
                        ai_nationality_reading = NULL
                    WHERE workspace_id = :workspaceId
                      AND (jsonb_exists(ai_inferred_fields, :nationality) OR ai_nationality_reading IS NOT NULL)
                    """, scope);
        }
        return new InferredBackgroundCounts(genderCleared, nationalityCleared);
    }

    private static MapSqlParameterSource scopeOf(UUID workspaceId) {
        return new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("gender", GENDER)
                .addValue("nationality", NATIONALITY);
    }
}
