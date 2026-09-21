package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantContext;
import app.lightmove.api.project.model.ProjectFacts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * The system prompt: a fixed body, then what is true of this turn.
 *
 * <p><b>That order is the whole design.</b> Gemini caches on a prefix, so anything that varies
 * between turns invalidates everything after it. The body is a resource with no placeholders in it
 * at all — not a template with none filled, but a file that could not carry one — and every
 * changing fact is appended after it. A mandate's id is a UUID and a UUID is a cache miss, which is
 * why it lives in the tail even though it is the most useful thing here.
 *
 * <p>Read once at construction, so a missing or unreadable prompt fails the application rather than
 * the first conversation that needed it — the rule {@code docs/llm-guardrails.md} states for every
 * caller that resolves a spec in its constructor.
 */
@Service
public class AssistantPromptAssembler {

    private final String body;

    public AssistantPromptAssembler(
            @Value("classpath:prompts/assistant-system.st") Resource systemPrompt) {
        this.body = read(systemPrompt);
    }

    public String assemble(AssistantContext context) {
        StringBuilder prompt = new StringBuilder(body).append("\nYou are helping ")
                .append(context.consultant())
                .append('.');
        if (context.hasMandate()) {
            appendMandate(prompt, context.mandate());
        } else {
            prompt.append("\nThis conversation is not about any one mandate. A tool that needs one "
                    + "cannot be used until the consultant says which.");
        }
        return prompt.append('\n').toString();
    }

    /**
     * The id is spelled out because a tool takes it as an argument and the model has no other way to
     * learn it. It names the mandate; it authorises nothing — the guard decides that per call, and
     * a mandate named here that the consultant has no seat on is refused exactly as one that was
     * invented.
     */
    private static void appendMandate(StringBuilder prompt, ProjectFacts mandate) {
        prompt.append("\nThe conversation is about one mandate: ")
                .append(mandate.positionTitle());
        if (mandate.clientName() != null) {
            prompt.append(" for ").append(mandate.clientName());
        }
        prompt.append(". Its id is ").append(mandate.id())
                .append(", which is what a tool asking for a mandate wants. It is at the ")
                .append(mandate.stage().name().toLowerCase(java.util.Locale.ROOT))
                .append(" stage");
        if (mandate.targetDate() != null) {
            prompt.append(", targeted for ").append(mandate.targetDate());
        }
        prompt.append('.');
    }

    private static String read(Resource systemPrompt) {
        try {
            return systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(
                    "Could not read " + systemPrompt.getDescription(), unreadable);
        }
    }
}
