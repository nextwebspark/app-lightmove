package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.common.constant.NationalityGroup;
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
    public void answer(JsonNode input) {
        classifier.classify(GoldenProfileCase.profileOf(input, json)
                .dossierMissing(EnumSet.of(BackgroundField.NATIONALITY)), MaterialText.NONE);
    }
}
