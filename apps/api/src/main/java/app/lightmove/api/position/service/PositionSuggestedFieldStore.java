package app.lightmove.api.position.service;

import app.lightmove.api.position.model.PositionSuggestedField;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code app_lm_position_suggested_field} (V244): what document readings filled on a brief, until its publish. */
@Component
@RequiredArgsConstructor
class PositionSuggestedFieldStore {

    private final JdbcClient jdbc;

    void remember(UUID positionId, PositionSuggestedField field) {
        jdbc.sql("""
                        INSERT INTO app_lm_position_suggested_field
                            (position_id, field_key, prompt_id, version, document_items)
                        VALUES (:positionId, :fieldKey, :promptId, :version, :documentItems)
                        ON CONFLICT (position_id, field_key) DO UPDATE
                            SET prompt_id = EXCLUDED.prompt_id, version = EXCLUDED.version,
                                document_items = EXCLUDED.document_items, suggested_at = now()
                        """)
                .param("positionId", positionId)
                .param("fieldKey", field.fieldKey())
                .param("promptId", field.promptId())
                .param("version", field.version())
                .param("documentItems", field.documentItems(), Types.INTEGER)
                .update();
    }

    void forget(UUID positionId, Collection<String> fieldKeys) {
        if (fieldKeys.isEmpty()) {
            return;
        }
        jdbc.sql("DELETE FROM app_lm_position_suggested_field WHERE position_id = :positionId AND field_key IN (:keys)")
                .param("positionId", positionId)
                .param("keys", List.copyOf(fieldKeys))
                .update();
    }

    List<PositionSuggestedField> suggestedOn(UUID positionId) {
        return jdbc.sql("""
                        SELECT field_key, prompt_id, version, document_items
                        FROM app_lm_position_suggested_field
                        WHERE position_id = :positionId
                        """)
                .param("positionId", positionId)
                .query((row, index) -> new PositionSuggestedField(row.getString("field_key"),
                        row.getString("prompt_id"), row.getInt("version"),
                        row.getObject("document_items", Integer.class)))
                .list();
    }

    void forgetAll(UUID positionId) {
        jdbc.sql("DELETE FROM app_lm_position_suggested_field WHERE position_id = :positionId")
                .param("positionId", positionId)
                .update();
    }
}
