package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.llm.service.EvalSubject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The background proposal's eval: a profile missing its seniority, labelled with the level the person holds. The
 * prompt proposes gender and years of experience too; only the level has a labelled set.
 */
@Component
@RequiredArgsConstructor
class CandidateSeniorityEvalSubject implements EvalSubject {

    static final String LABEL = "seniority";

    private final CandidateAiEnricher enricher;
    private final ObjectMapper json;

    @Override
    public String promptId() {
        return CandidateAiEnricher.PROMPT_ID;
    }

    @Override
    public List<String> problemsOf(JsonNode input, JsonNode expected) {
        List<String> problems = new ArrayList<>(GoldenProfileCase.problemsOf(input, json));
        String label = GoldenProfileCase.labelOf(expected, LABEL);
        if (label == null) {
            problems.add("expected.seniority is missing.");
        } else if (Seniority.fromValue(label) == null) {
            problems.add("expected.seniority is not a seniority level.");
        }
        return problems;
    }

    @Override
    public void answer(JsonNode input) {
        enricher.infer(GoldenProfileCase.profileOf(input, json)
                .dossierMissing(EnumSet.of(BackgroundField.SENIORITY)), MaterialText.NONE);
    }
}
