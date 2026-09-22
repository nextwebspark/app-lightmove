package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantContext;
import app.lightmove.api.project.model.ProjectFacts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
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

    /** Anything that is not printable text on one line, plus runs of ordinary whitespace. */
    private static final Pattern CONTROL_OR_SPACE = Pattern.compile("[\\p{Cntrl}\\s]+");

    private static final int MAX_INTERPOLATED = 120;

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
     *
     * <p>The title and the client name are quoted and flattened first. Both are free text a
     * workspace member typed, and this is the <b>system</b> message — a strictly higher-trust
     * channel than the tool results the body already warns about. A position title passes
     * {@code @NotBlank @Size(max = 160)} and is only stripped at the ends, so an embedded newline
     * survives and 160 characters is room enough to forge a turn boundary. The guard bounds what
     * that could reach — membership is re-read per call — but a member could still plant a title
     * that makes a colleague's assistant misreport, so it is flattened where it is interpolated.
     */
    private static void appendMandate(StringBuilder prompt, ProjectFacts mandate) {
        prompt.append("\nThe conversation is about one mandate, titled \"")
                .append(oneLine(mandate.positionTitle()))
                .append('"');
        if (mandate.clientName() != null) {
            prompt.append(" for the client \"").append(oneLine(mandate.clientName())).append('"');
        }
        prompt.append(". Its id is ").append(mandate.id())
                .append(", which is what a tool asking for a mandate wants. It is at the ")
                .append(mandate.stage().name().toLowerCase(Locale.ROOT))
                .append(" stage");
        if (mandate.mappingTargetDate() != null) {
            prompt.append(", with its mapping due ").append(mandate.mappingTargetDate());
        }
        if (mandate.shortlistTargetDate() != null) {
            prompt.append(" and its shortlist due ").append(mandate.shortlistTargetDate());
        }
        prompt.append('.');
    }

    /**
     * One line, and no longer than a title needs to be.
     *
     * <p>Every character that could end a line or start what looks like a new section becomes a
     * space, including the ones no editor shows. Truncation is belt to that braces: the column
     * allows 160, and a name that needs more than this is not a name.
     */
    private static String oneLine(String supplied) {
        String flattened = CONTROL_OR_SPACE.matcher(supplied).replaceAll(" ").trim();
        return flattened.length() <= MAX_INTERPOLATED
                ? flattened
                : flattened.substring(0, MAX_INTERPOLATED) + "…";
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
