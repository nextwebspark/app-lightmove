package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.prompteval.model.RubricCriterion;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The judge's Try it: two invented openers to one invented profile, on two criteria. Nobody here exists. */
@Component
@RequiredArgsConstructor
class EvalJudgeTrial implements PromptTrial {

    private static final List<RubricCriterion> CRITERIA = List.of(
            new RubricCriterion("specific", "Specific to the person",
                    "It could only have been written to this person: it names something from their profile."),
            new RubricCriterion("plain", "Plain and brief",
                    "One or two plain sentences, with no praise and no question."));

    private static final String CASE = """
            POSITION
            Chief Financial Officer, energy, Dubai

            PROFILE
            Chief financial officer, Kestrel Grid, September 2026 to now.
            Finance director, Aldermoor Power, 2019 to August 2026.
            """;

    private static final String ANSWER_A = "Your impressive career in energy finance caught my attention.";
    private static final String ANSWER_B =
            "You moved from Aldermoor Power to lead finance at Kestrel Grid in September.";

    private final EvalJudge judge;

    @Override
    public String promptId() {
        return "eval-judge";
    }

    @Override
    public String inputName() {
        return "Two openers to one profile";
    }

    @Override
    public String inputDescription() {
        return "A fictional finance chief's profile, two invented opening lines written to it, and a rubric of "
                + "two criteria. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return EvalJudge.USER_MESSAGE;
    }

    @Override
    public void run() {
        judge.ask(EvalJudge.textOf(CRITERIA), CASE, ANSWER_A, ANSWER_B);
    }
}
