package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.common.constant.NationalityGroup;
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

/** The nationality reading's eval: a profile, labelled with the group a careful reader would give or Unknown. */
@Component
@RequiredArgsConstructor
class CandidateNationalityEvalSubject implements EvalSubject {

    static final String LABEL = "nationality";

    private static final List<EvalCheck> CHECKS = List.of(
            EvalCheck.of("nationality-group", "Nationality group is the labelled one",
                    (input, expected, answer) -> labelled(expected).equals(answered(answer))
                            ? EvalCheckVerdict.PASSED : EvalCheckVerdict.FAILED),
            EvalCheck.of("no-gcc-false-positive", "No Gulf nationality without the evidence for it",
                    (input, expected, answer) -> GoldenRowScorer.isGcc(labelled(expected))
                            ? EvalCheckVerdict.NOT_APPLICABLE
                            : GoldenRowScorer.isGcc(answered(answer))
                                    ? EvalCheckVerdict.FAILED : EvalCheckVerdict.PASSED));

    private final CandidateNationalityClassifier classifier;
    private final ObjectMapper json;

    @Override
    public String promptId() {
        return CandidateNationalityClassifier.PROMPT_ID;
    }

    @Override
    public List<String> problemsOf(JsonNode input, JsonNode expected) {
        List<String> problems = new ArrayList<>(GoldenProfileCase.problemsOf(input, json));
        String label = GoldenProfileCase.labelOf(expected, LABEL);
        if (label == null) {
            problems.add("expected.nationality is missing.");
        } else if (!GoldenRowScorer.UNKNOWN.equals(label) && NationalityGroup.ofLabel(label) == null) {
            problems.add("expected.nationality is not a nationality group or Unknown.");
        }
        return problems;
    }

    @Override
    public List<EvalCheck> checks() {
        return CHECKS;
    }

    @Override
    public boolean isGateReady() {
        return true;
    }

    private static String labelled(JsonNode expected) {
        return GoldenRowScorer.canonical(GoldenProfileCase.labelOf(expected, LABEL));
    }

    /** No answer is scored as Unknown, as the offline suite scores a row the classifier could not read. */
    private static String answered(JsonNode answer) {
        return GoldenRowScorer.canonical(GoldenProfileCase.labelOf(answer, "category"));
    }

    @Override
    public void answer(JsonNode input) {
        classifier.classify(GoldenProfileCase.profileOf(input, json)
                .dossierMissing(EnumSet.of(BackgroundField.NATIONALITY)), MaterialText.NONE);
    }
}
