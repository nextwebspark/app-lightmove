package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.core.llm.constant.PromptOutcome;
import app.lightmove.api.core.llm.model.PromptVersionRef;
import app.lightmove.api.core.llm.service.PromptOutcomes;
import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.constant.PositionFieldKeys;
import app.lightmove.api.position.dto.DocumentReadingRequest;
import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.model.PositionCompetency;
import app.lightmove.api.position.model.PositionCriterion;
import app.lightmove.api.position.model.PositionOrgNode;
import app.lightmove.api.position.model.PositionPriority;
import app.lightmove.api.position.model.PositionResponsibility;
import app.lightmove.api.position.model.PositionSuggestedField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
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

    private static final Map<String, Function<Position, Long>> DOCUMENT_ROWS = Map.of(
            RESPONSIBILITIES, position -> fromDocument(position.getResponsibilities(),
                    PositionResponsibility::getSource),
            STRATEGIC_PRIORITIES, position -> fromDocument(position.getStrategicPriorities(),
                    PositionPriority::getSource),
            ORG_CHART, position -> fromDocument(position.getOrgChart(), PositionOrgNode::getSource),
            CRITERIA, position -> fromDocument(position.getCriteria(), PositionCriterion::getSource),
            TECHNICAL_COMPETENCIES, position -> fromDocument(competenciesOf(position, CompetencyPanel.TECHNICAL),
                    PositionCompetency::getSource),
            BEHAVIOURAL_COMPETENCIES, position -> fromDocument(competenciesOf(position, CompetencyPanel.BEHAVIOURAL),
                    PositionCompetency::getSource));

    private final PositionSuggestedFieldStore store;
    private final PromptOutcomes outcomes;

    void recordDetails(Position position, DocumentReadingRequest reading) {
        record(position, PositionDetailsProposer.PROMPT_ID, reading, PositionFieldKeys.DETAILS,
                Set.of(RESPONSIBILITIES));
    }

    void recordContext(Position position, DocumentReadingRequest reading) {
        record(position, PositionContextProposer.PROMPT_ID, reading, PositionFieldKeys.CONTEXT,
                Set.of(STRATEGIC_PRIORITIES));
    }

    void recordReporting(Position position, DocumentReadingRequest reading) {
        record(position, PositionReportingProposer.PROMPT_ID, reading, PositionFieldKeys.REPORTING,
                Set.of(ORG_CHART));
    }

    void recordCriteria(Position position, DocumentReadingRequest reading) {
        record(position, PositionAssessmentProposer.PROMPT_ID, reading, Set.of(), Set.of(CRITERIA));
    }

    void recordCompetencies(Position position, DocumentReadingRequest reading) {
        record(position, PositionAssessmentProposer.PROMPT_ID, reading, Set.of(),
                Set.of(TECHNICAL_COMPETENCIES, BEHAVIOURAL_COMPETENCIES));
    }

    /**
     * Each field the readings in force filled, counted against its version, then forgotten: a later publish counts
     * only what a later reading fills.
     */
    void countAtPublish(Position position) {
        List<PositionSuggestedField> suggested = store.suggestedOn(position.getId());
        if (suggested.isEmpty()) {
            return;
        }
        for (PositionSuggestedField field : suggested) {
            boolean kept = field.documentItems() == null
                    ? position.getFieldSources().get(field.fieldKey()) == FieldSource.DOCUMENT
                    : DOCUMENT_ROWS.get(field.fieldKey()).apply(position) == field.documentItems().longValue();
            outcomes.record(new PromptVersionRef(field.promptId(), field.version()),
                    kept ? PromptOutcome.ACCEPTED : PromptOutcome.CHANGED);
        }
        store.forgetAll(position.getId());
    }

    /**
     * A reading no version answered (the heuristic's) forgets what an earlier one filled in the same fields, or those
     * values, now the heuristic's, would be credited to it. Keys outside this step are ignored.
     */
    private void record(Position position, String promptId, DocumentReadingRequest reading, Set<String> scalarKeys,
                        Set<String> lists) {
        if (reading == null) {
            return;
        }
        List<String> filledScalars = reading.fields().stream().filter(scalarKeys::contains).toList();
        Map<String, Integer> filledLists = new LinkedHashMap<>(reading.listItems());
        filledLists.keySet().retainAll(lists);
        if (reading.version() == null) {
            List<String> forgotten = new ArrayList<>(filledScalars);
            forgotten.addAll(filledLists.keySet());
            store.forget(position.getId(), forgotten);
            return;
        }
        int version = reading.version();
        filledScalars.forEach(key ->
                store.remember(position.getId(), new PositionSuggestedField(key, promptId, version, null)));
        filledLists.forEach((list, items) ->
                store.remember(position.getId(), new PositionSuggestedField(list, promptId, version, items)));
    }

    private static List<PositionCompetency> competenciesOf(Position position, CompetencyPanel panel) {
        return position.getCompetencies().stream().filter(competency -> competency.getPanel() == panel).toList();
    }

    private static <T> long fromDocument(Collection<T> rows, Function<T, FieldSource> sourceOf) {
        return rows.stream().filter(row -> sourceOf.apply(row) == FieldSource.DOCUMENT).count();
    }
}
