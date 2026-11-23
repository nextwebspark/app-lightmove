package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.core.llm.service.EvalCheck;
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

    private static final List<EvalCheck> CHECKS = List.of(
            EvalCheck.of("seniority-level", "Seniority is the labelled level",
                    (input, expected, answer) -> levelsApart(expected, answer) == 0
                            ? EvalCheckVerdict.PASSED : EvalCheckVerdict.FAILED),
            EvalCheck.of("seniority-within-one", "Seniority is within one level of the labelled one",
                    (input, expected, answer) -> levelsApart(expected, answer) <= 1
                            ? EvalCheckVerdict.PASSED : EvalCheckVerdict.FAILED));

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
    public List<EvalCheck> checks() {
        return CHECKS;
    }

    /** The distance between the labelled level and the answered one; no level answered is as far as can be. */
    private static int levelsApart(JsonNode expected, JsonNode answer) {
        Seniority labelled = Seniority.fromValue(GoldenProfileCase.labelOf(expected, LABEL));
        String proposed = GoldenProfileCase.labelOf(answer, LABEL);
        Seniority answered = proposed == null ? null : Seniority.fromValue(proposed.strip());
        return labelled == null || answered == null ? Integer.MAX_VALUE
                : Math.abs(labelled.ordinal() - answered.ordinal());
    }

    @Override
    public void answer(JsonNode input) {
        enricher.infer(GoldenProfileCase.profileOf(input, json)
                .dossierMissing(EnumSet.of(BackgroundField.SENIORITY)), MaterialText.NONE);
    }
}
