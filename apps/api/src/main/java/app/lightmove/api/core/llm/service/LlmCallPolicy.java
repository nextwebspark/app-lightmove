package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * What every call to the model gets, whichever feature makes it: log attribution, a refusal of text
 * that reads like an instruction, and — where the reply is a document — one corrected try at an answer
 * that does not fit.
 *
 * <p>Here rather than at each call site because none of it is a property of any one prompt. What
 * varies is a {@link PromptGuardSpec}; the policy does not.
 *
 * <p><b>It is opt-in, and cannot be otherwise.</b> The shared {@code ChatClient} is a bean of a
 * framework type, so any feature can inject it and call the model unguarded — nothing here prevents
 * that, and a reviewer noticing a {@code chatClient.prompt()} without a spec is the only check.
 */
@Service
@Slf4j
public class LlmCallPolicy {

    private final LlmSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public LlmCallPolicy(LightMoveProperties properties) {
        this.settings = properties.llm();
    }

    /**
     * The advisors and log attribution one prompt is called with, as {@code .advisors(...)} takes them.
     *
     * <p>Resolve it once in the caller's constructor and hold the result: the advisors are immutable,
     * and any schema is read here, so one that will not load fails the context at startup rather than
     * every request that needed it.
     *
     * <p>Deliberately not the shared {@code ChatClient} bean's default advisors, tempting as that is
     * for making the guard impossible to forget: a block answers in place of the model, so its canned
     * answer has to bind to whatever <i>that</i> call expects back, and one default cannot serve both
     * a prose reply and a typed record. {@link PromptGuardSpec} refuses the specs that would get that
     * wrong.
     */
    public Consumer<ChatClient.AdvisorSpec> forPrompt(PromptGuardSpec spec) {
        List<Advisor> advisors = new ArrayList<>(2);
        advisors.add(SafeGuardAdvisor.builder()
                .sensitiveWords(injectionPhrasesFor(spec))
                .failureResponse(spec.blockedAnswer())
                .build());
        if (spec.answerSchema() != null) {
            // Order left at its default, which places this inside the guard above: in front of it, a
            // blocked call's canned answer would be re-asked as though the model had answered badly.
            advisors.add(StructuredOutputValidationAdvisor.builder()
                    .outputJsonSchema(AnswerSchemas.readFrom(spec.answerSchema()))
                    .maxRepeatAttempts(settings.answerRepairAttempts())
                    .build());
        }
        return advisorSpec -> advisorSpec
                .param(ChatCallLog.PROMPT_ID_ATTRIBUTE, spec.promptId())
                .advisors(advisors);
    }

    /**
     * The model's own answer, or a refusal — never the guard's canned reply passed off as one.
     *
     * <p>Here rather than at each call site because the check is the piece most easily forgotten, and
     * forgetting it serves a canned refusal as a real answer. A null reply is refused for the same
     * reason: {@code content()} is nullable, and an empty answer rendered as a verdict is the same
     * failure in a different shape.
     */
    public String requireModelAnswer(String promptId, String answer) {
        if (BlockedAnswer.matches(answer)) {
            log.warn("Prompt {} was blocked before reaching the model: the caller's text matched the "
                    + "injection word list.", promptId);
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "That text reads like an instruction to the assistant. Reword it and try again.");
        }
        if (answer == null || answer.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "prompt " + promptId + " answered with nothing");
        }
        return answer;
    }

    /** The configured baseline, plus anything this prompt refuses on top of it. */
    private List<String> injectionPhrasesFor(PromptGuardSpec spec) {
        if (spec.additionalInjectionPhrases().isEmpty()) {
            return settings.injectionPhrases();
        }
        List<String> phrases = new ArrayList<>(settings.injectionPhrases());
        phrases.addAll(spec.additionalInjectionPhrases());
        return phrases;
    }
}
