package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.llm.constant.FinishKind;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.PromptTrialResult;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.core.llm.service.PromptTrialRunner;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.prompteval.constant.JudgePick;
import app.lightmove.api.prompteval.model.JudgeReading;
import app.lightmove.api.prompteval.model.JudgedPick;
import app.lightmove.api.prompteval.model.RubricCriterion;
import app.lightmove.api.promptregistry.constant.PlatformPrompt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The LLM judge: one call compares two answers to the same fictional case on every criterion of a rubric. It is a
 * registry prompt like any other, asked here under a named version so a run and a check each know which judge
 * made their picks. It is never told which version wrote which answer.
 */
@Slf4j
@Component
public class EvalJudge {

    public static final String PROMPT_ID = PlatformPrompt.EVAL_JUDGE.getPromptId();

    static final String USER_MESSAGE = """
            RUBRIC
            {rubric}

            CASE
            {case}

            ANSWER A
            {answerA}

            ANSWER B
            {answerB}
            """;

    /** Names no criterion of any rubric, so a call the guard refused reads as no pick on every one. */
    private static final String BLOCKED =
            "{\"picks\":[{\"criterion\":\"" + BlockedAnswer.MARKER + "\",\"better\":\"tie\",\"reason\":null}]}";
    private static final String NO_ANSWER = "(no answer)";
    private static final int MAX_REASON_LENGTH = 600;

    private final StructuredPrompt prompt;
    private final PromptTrialRunner runner;
    private final PromptRegistryStore registry;
    private final ObjectMapper json;

    EvalJudge(StructuredPromptFactory prompts, PromptTrialRunner runner, PromptRegistryStore registry,
              ObjectMapper json) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED, PlatformPrompt.EVAL_JUDGE.getPinnedModel());
        this.runner = runner;
        this.registry = registry;
        this.json = json;
    }

    /** The judge's version serving now. Empty where the registry holds none. */
    public Optional<ServedPrompt> liveVersion() {
        return registry.productionOf(PROMPT_ID).flatMap(live -> registry.version(PROMPT_ID, live.version()));
    }

    /**
     * Asks the judge once, and never throws. A criterion it names no readable pick for is
     * {@link JudgedPick#UNREADABLE}; a model that answers nothing at all gives a reading that
     * {@link JudgeReading#isAnswered() is not answered}.
     */
    public JudgeReading judge(ServedPrompt version, List<RubricCriterion> criteria, JsonNode caseInput,
                              JsonNode answerA, JsonNode answerB) {
        PromptTrialResult asked;
        try {
            asked = runner.run(version, () -> ask(textOf(criteria), textOf(caseInput), textOf(answerA),
                    textOf(answerB)));
        } catch (RuntimeException unanswered) {
            // The exception's own text is left out: a transport failure can quote what was sent.
            log.warn("The eval judge gave no answer: {}", unanswered.getClass().getSimpleName());
            asked = new PromptTrialResult(null, null, null, 0, 0, FinishKind.OTHER, 0, 0);
        }
        Map<String, JudgedPick> picks = new LinkedHashMap<>();
        for (RubricCriterion criterion : criteria) {
            picks.put(criterion.key(), pickOn(criterion, asked.parsedAnswer()));
        }
        return new JudgeReading(picks, asked);
    }

    void ask(String rubric, String evalCase, String answerA, String answerB) {
        prompt.ask(JudgeAnswer.class, user -> user.text(USER_MESSAGE)
                .param("rubric", rubric)
                .param("case", evalCase)
                .param("answerA", answerA)
                .param("answerB", answerB));
    }

    static String textOf(List<RubricCriterion> criteria) {
        return criteria.stream()
                .map(criterion -> "- %s: %s. The better answer: %s".formatted(criterion.key(), criterion.name(),
                        criterion.better()))
                .collect(Collectors.joining("\n"));
    }

    private String textOf(JsonNode node) {
        return node == null || node.isNull() ? NO_ANSWER
                : json.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    private static JudgedPick pickOn(RubricCriterion criterion, JsonNode answer) {
        if (answer == null) {
            return JudgedPick.UNREADABLE;
        }
        for (JsonNode pick : answer.path("picks")) {
            if (!criterion.key().equalsIgnoreCase(pick.path("criterion").asString("").strip())) {
                continue;
            }
            JudgePick better = switch (pick.path("better").asString("").strip().toLowerCase(Locale.ROOT)) {
                case "a" -> JudgePick.A;
                case "b" -> JudgePick.B;
                case "tie" -> JudgePick.TIE;
                default -> null;
            };
            if (better == null) {
                return JudgedPick.UNREADABLE;
            }
            String reason = pick.path("reason").asString("").strip();
            return new JudgedPick(better, reason.isEmpty() ? null
                    : reason.substring(0, Math.min(reason.length(), MAX_REASON_LENGTH)));
        }
        return JudgedPick.UNREADABLE;
    }

    private record JudgeAnswer(List<Pick> picks) {}

    private record Pick(String criterion, String better, String reason) {}
}
