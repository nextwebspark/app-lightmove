package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.core.llm.service.EvalCheck;
import app.lightmove.api.core.llm.service.EvalSubject;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The assessment draft's eval: a fictional executive with notes, calls and documents on file, drafted against a
 * brief by the drafter itself. The material goes through {@link CandidateMaterialReader}'s own writing, so the
 * contacts planted in a case are taken out before the model sees them, as a real person's are.
 */
@Component
@RequiredArgsConstructor
class CandidateScorecardEvalSubject implements EvalSubject {

    /** Assessment answers vary from one call to the next even at temperature 0. */
    static final int REPEATS = 3;

    private final CandidateScorecardDrafter drafter;
    private final ObjectMapper json;

    @Override
    public String promptId() {
        return "candidate-scorecard-draft";
    }

    @Override
    public List<String> problemsOf(JsonNode input, JsonNode expected) {
        List<String> problems = AssessmentCase.problemsOf(input, json);
        return problems.isEmpty() ? AssessmentExpectation.problemsOf(input, expected) : problems;
    }

    @Override
    public void answer(JsonNode input) {
        drafter.draft(AssessmentCase.dossierOf(input, json), AssessmentCase.briefOf(input),
                AssessmentCase.materialOf(input));
    }

    @Override
    public List<EvalCheck> checks() {
        return AssessmentDraftChecks.ALL;
    }

    @Override
    public int minimumRepeats() {
        return REPEATS;
    }

    /** Pack v1 is drafted and waits for a consultant's review (#1326); until then a run reports and gates nothing. */
    @Override
    public boolean isGateReady() {
        return false;
    }
}
