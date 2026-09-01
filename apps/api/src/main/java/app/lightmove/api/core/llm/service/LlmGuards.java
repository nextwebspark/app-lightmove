package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmSettings;
import app.lightmove.api.core.llm.config.ChatClientConfig;
import app.lightmove.api.core.llm.model.LlmPromptSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * The advisors every call to the model gets: log attribution, a refusal of text that reads like an
 * instruction, and — where the reply is a document — one corrected try at an answer that does not fit.
 *
 * <p>Here rather than at each call site because none of it is a property of any one prompt. The first
 * two callers each had a different half of it: the import had a guard and a validator, the shortlist
 * had neither while taking a job brief and a candidate profile as free text — a far larger injection
 * surface than a spreadsheet header. What varies between prompts is a {@link LlmPromptSpec}; the
 * policy does not.
 *
 * <p>Not on the shared {@code ChatClient} bean's default advisors, tempting as that is: the answer a
 * block replies with has to bind to whatever <i>that</i> call expects back, and a single default
 * cannot. One line per call site is the price of that, and {@code LlmPromptSpec} refuses a spec that
 * would get it wrong.
 */
@Service
public class LlmGuards {

    private final LlmSettings settings;

    public LlmGuards(LightMoveProperties properties) {
        this.settings = properties.llm();
    }

    /**
     * The advisors and log attribution for one prompt, as {@code .advisors(...)} takes them.
     *
     * <p><b>Call this once, from the caller's constructor, and hold the result</b> — the advisors are
     * immutable and the schema is read here, so a schema that will not load fails the context at
     * startup rather than every request that needed it.
     *
     * <pre>{@code
     * this.guarded = guards.on(SPEC);            // in the constructor
     * chatClient.prompt().advisors(guarded)...   // per call
     * }</pre>
     */
    public Consumer<ChatClient.AdvisorSpec> on(LlmPromptSpec spec) {
        List<Advisor> advisors = new ArrayList<>(2);
        advisors.add(SafeGuardAdvisor.builder()
                .sensitiveWords(sensitiveWordsFor(spec))
                .failureResponse(spec.blockedAnswer())
                .build());
        if (spec.answerSchema() != null) {
            // Order left at its default, which places this inside the guard above: in front of it, a
            // blocked call's canned answer would be re-asked as though the model had answered badly.
            advisors.add(StructuredOutputValidationAdvisor.builder()
                    .outputJsonSchema(schemaOf(spec.answerSchema()))
                    .maxRepeatAttempts(settings.answerRepairAttempts())
                    .build());
        }
        return advisorSpec -> advisorSpec
                .param(ChatClientConfig.PROMPT_ID, spec.promptId())
                .advisors(advisors);
    }

    private List<String> sensitiveWordsFor(LlmPromptSpec spec) {
        if (spec.extraSensitive().isEmpty()) {
            return settings.injectionPhrases();
        }
        List<String> words = new ArrayList<>(settings.injectionPhrases());
        words.addAll(spec.extraSensitive());
        return words;
    }

    private static String schemaOf(Resource schema) {
        try {
            return schema.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the answer schema " + schema, e);
        }
    }
}
