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
 * What every model call gets: log attribution, an injection-phrase guard, and a schema repair try.
 * <b>Opt-in, and cannot be otherwise</b>: a {@code chatClient.prompt()} without a spec is unguarded,
 * and review is the only check.
 */
@Service
@Slf4j
public class LlmCallPolicy {

    private final LlmSettings settings;

    public LlmCallPolicy(LightMoveProperties properties) {
        this.settings = properties.llm();
    }

    /**
     * Resolve once in the caller's constructor, so a schema that will not load fails at startup. Not the
     * bean's default advisors: a block's canned answer must bind to what that call expects back.
     */
    public Consumer<ChatClient.AdvisorSpec> forPrompt(PromptGuardSpec spec) {
        List<Advisor> advisors = new ArrayList<>(2);
        advisors.add(SafeGuardAdvisor.builder()
                .sensitiveWords(injectionPhrasesFor(spec))
                .failureResponse(spec.blockedAnswer())
                .build());
        if (spec.answerSchema() != null) {
            // Default order places this inside the guard: in front, a blocked answer would be re-asked.
            advisors.add(StructuredOutputValidationAdvisor.builder()
                    .outputJsonSchema(AnswerSchemas.readFrom(spec.answerSchema()))
                    .maxRepeatAttempts(settings.answerRepairAttempts())
                    .build());
        }
        return advisorSpec -> advisorSpec
                .param(ChatCallLog.PROMPT_ID_ATTRIBUTE, spec.promptId())
                .advisors(advisors);
    }

    /** The model's own answer, or a refusal — never the guard's canned reply, nor a null {@code content()}. */
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

    private List<String> injectionPhrasesFor(PromptGuardSpec spec) {
        if (spec.additionalInjectionPhrases().isEmpty()) {
            return settings.injectionPhrases();
        }
        List<String> phrases = new ArrayList<>(settings.injectionPhrases());
        phrases.addAll(spec.additionalInjectionPhrases());
        return phrases;
    }
}
