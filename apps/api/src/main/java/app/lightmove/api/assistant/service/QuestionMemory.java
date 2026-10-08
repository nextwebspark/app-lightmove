package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantQuestion;
import app.lightmove.api.assistant.model.AssistantQuestionOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The questions an earlier answer asked, written back into the chat the model reads, so the consultant's
 * next message ("Region: GCC only · Size: 500+") reads as the answers to them. Copied from the stored turn,
 * never from the model's text.
 */
final class QuestionMemory {

    private static final Pattern QUESTIONS_BLOCK =
            Pattern.compile("(?s)<asked_consultant\\b[^>]*>.*?</asked_consultant>");

    private QuestionMemory() {
    }

    static String render(List<AssistantQuestion> questions) {
        List<String> lines = new ArrayList<>();
        lines.add("<asked_consultant>");
        for (AssistantQuestion question : questions) {
            lines.add("- " + question.header() + ": " + question.question()
                    + (question.multiSelect() ? " (several allowed)" : ""));
            for (AssistantQuestionOption option : question.options()) {
                lines.add("  - " + option.label() + " — " + option.description());
            }
        }
        lines.add("</asked_consultant>");
        return String.join("\n", lines);
    }

    /** A model that copies the block into its own answer would show the consultant raw markup. */
    static String stripFrom(String answer) {
        return QUESTIONS_BLOCK.matcher(answer).replaceAll("").strip();
    }
}
