package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmSettings;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.stereotype.Service;

/**
 * What every call to the model gets, whichever feature makes it: log attribution, a refusal of text
 * that reads like an instruction, and — where the reply is a document — one corrected try at an answer
 * that does not fit.
 *
 * <p>Here rather than at each call site because none of it is a property of any one prompt. What
 * varies is a {@link PromptGuardSpec}; the policy does not.
 */
@Service
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
