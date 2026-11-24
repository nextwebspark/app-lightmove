package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.core.llm.constant.PromptOutcome;
import app.lightmove.api.core.llm.model.PromptVersionRef;
import app.lightmove.api.core.llm.service.PromptOutcomes;
import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.constant.PositionFieldKeys;
import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.model.PositionCompetency;
import app.lightmove.api.position.model.PositionCriterion;
import app.lightmove.api.position.model.PositionOrgNode;
import app.lightmove.api.position.model.PositionPriority;
import app.lightmove.api.position.model.PositionResponsibility;
import java.sql.Types;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * What a document reading filled on a brief, held until the brief is published and then counted once per field
 * against the prompt version that suggested it: kept while it still carries the document as its source, changed
 * once a person edited, cleared or undid it (#1452, V244). Keys and counts only; never a value.
 */
@Service
@RequiredArgsConstructor
public class PositionSuggestions {

    static final String RESPONSIBILITIES = "responsibilities";
    static final String STRATEGIC_PRIORITIES = "strategicPriorities";
    static final String ORG_CHART = "orgChart";
    static final String CRITERIA = "criteria";
    static final String TECHNICAL_COMPETENCIES = "technicalCompetencies";
    static final String BEHAVIOURAL_COMPETENCIES = "behaviouralCompetencies";

    private final JdbcClient jdbc;
    private final PromptOutcomes outcomes;

    void recordDetails(Position position, Integer readingVersion) {
        record(position, PositionDetailsProposer.PROMPT_ID, readingVersion, PositionFieldKeys.DETAILS,
                List.of(RESPONSIBILITIES));
    }

    void recordContext(Position position, Integer readingVersion) {
        record(position, PositionContextProposer.PROMPT_ID, readingVersion, PositionFieldKeys.CONTEXT,
                List.of(STRATEGIC_PRIORITIES));
    }

    void recordReporting(Position position, Integer readingVersion) {
        record(position, PositionReportingProposer.PROMPT_ID, readingVersion, PositionFieldKeys.REPORTING,
                List.of(ORG_CHART));
    }

    void recordCriteria(Position position, Integer readingVersion) {
        record(position, PositionAssessmentProposer.PROMPT_ID, readingVersion, Set.of(), List.of(CRITERIA));
    }

    void recordCompetencies(Position position, Integer readingVersion) {
        record(position, PositionAssessmentProposer.PROMPT_ID, readingVersion, Set.of(),
                List.of(TECHNICAL_COMPETENCIES, BEHAVIOURAL_COMPETENCIES));
    }

    /**
     * Each field the readings in force filled, counted against its version, then forgotten: a later publish counts
     * only what a later reading fills. A field a person changed and a re-read then filled again counts once, as the
     * re-read's.
     */
    void countAtPublish(Position position) {
        List<SuggestedField> suggested = jdbc.sql("""
                        SELECT field_key, prompt_id, version, document_items
                        FROM app_lm_position_suggested_field
                        WHERE position_id = :positionId
                        """)
                .param("positionId", position.getId())
                .query((row, index) -> new SuggestedField(row.getString("field_key"), row.getString("prompt_id"),
                        row.getInt("version"), (Integer) row.getObject("document_items")))
                .list();
        if (suggested.isEmpty()) {
            return;
        }
        Map<String, Long> documentRows = documentRowsOf(position);
        for (SuggestedField field : suggested) {
            boolean kept = field.documentItems() == null
                    ? position.getFieldSources().get(field.fieldKey()) == FieldSource.DOCUMENT
                    : documentRows.getOrDefault(field.fieldKey(), 0L) == field.documentItems().longValue();
            outcomes.record(new PromptVersionRef(field.promptId(), field.version()),
                    kept ? PromptOutcome.ACCEPTED : PromptOutcome.CHANGED);
        }
        jdbc.sql("DELETE FROM app_lm_position_suggested_field WHERE position_id = :positionId")
                .param("positionId", position.getId())
                .update();
    }

    /** {@code readingVersion} null: an ordinary save, which leaves what earlier readings filled as it was. */
    private void record(Position position, String promptId, Integer readingVersion, Set<String> scalarKeys,
                        List<String> lists) {
        if (readingVersion == null) {
            return;
        }
        scalarKeys.stream()
                .filter(key -> position.getFieldSources().get(key) == FieldSource.DOCUMENT)
                .forEach(key -> upsert(position, key, promptId, readingVersion, null));
        Map<String, Long> documentRows = documentRowsOf(position);
        for (String list : lists) {
            long filled = documentRows.getOrDefault(list, 0L);
            if (filled > 0) {
                upsert(position, list, promptId, readingVersion, (int) filled);
            }
        }
    }

    private void upsert(Position position, String fieldKey, String promptId, int version, Integer documentItems) {
        jdbc.sql("""
                        INSERT INTO app_lm_position_suggested_field
                            (position_id, field_key, prompt_id, version, document_items)
                        VALUES (:positionId, :fieldKey, :promptId, :version, :documentItems)
                        ON CONFLICT (position_id, field_key) DO UPDATE
                            SET prompt_id = EXCLUDED.prompt_id, version = EXCLUDED.version,
                                document_items = EXCLUDED.document_items, suggested_at = now()
                        """)
                .param("positionId", position.getId())
                .param("fieldKey", fieldKey)
                .param("promptId", promptId)
                .param("version", version)
                .param("documentItems", documentItems, Types.INTEGER)
                .update();
    }

    private static Map<String, Long> documentRowsOf(Position position) {
        Map<String, Long> rows = new LinkedHashMap<>();
        rows.put(RESPONSIBILITIES, fromDocument(position.getResponsibilities(), PositionResponsibility::getSource));
        rows.put(STRATEGIC_PRIORITIES, fromDocument(position.getStrategicPriorities(), PositionPriority::getSource));
        rows.put(ORG_CHART, fromDocument(position.getOrgChart(), PositionOrgNode::getSource));
        rows.put(CRITERIA, fromDocument(position.getCriteria(), PositionCriterion::getSource));
        rows.put(TECHNICAL_COMPETENCIES, fromDocument(competenciesOf(position, CompetencyPanel.TECHNICAL),
                PositionCompetency::getSource));
        rows.put(BEHAVIOURAL_COMPETENCIES, fromDocument(competenciesOf(position, CompetencyPanel.BEHAVIOURAL),
                PositionCompetency::getSource));
        return rows;
    }

    private static List<PositionCompetency> competenciesOf(Position position, CompetencyPanel panel) {
        return position.getCompetencies().stream().filter(competency -> competency.getPanel() == panel).toList();
    }

    private static <T> long fromDocument(Collection<T> rows, Function<T, FieldSource> sourceOf) {
        return rows.stream().filter(row -> sourceOf.apply(row) == FieldSource.DOCUMENT).count();
    }

    private record SuggestedField(String fieldKey, String promptId, int version, Integer documentItems) {
    }
}
