package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantQuestion;
import app.lightmove.api.assistant.model.AssistantQuestionOption;
import app.lightmove.api.assistant.model.AssistantTurn;
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

    /**
     * The message the model reads for the turn at {@code index}. When the turn before it asked, the message is
     * that turn's answers and is sent beside the request they answer, followed back through any questions asked
     * since. Sent bare ("Country: United Arab Emirates"), it read as a remark, and the model answered it from
     * the chat's earlier answers without loading a playbook or searching: it named 25 companies and a match
     * count that no tool had returned.
     */
    static String answering(List<AssistantTurn> history, int index, String question) {
        if (index == 0 || history.get(index - 1).getQuestions().isEmpty()) {
            return question;
        }
        int asked = index - 1;
        while (asked > 0 && !history.get(asked - 1).getQuestions().isEmpty()) {
            asked--;
        }
        return "<consultant_answers>\n" + question + "\n</consultant_answers>\n"
                + "These answer the questions you asked about my request: \"" + history.get(asked).getQuestion()
                + "\". Carry on with that request now — load its playbook and run its tools as you would have.";
    }

    /** A model that copies the block into its own answer would show the consultant raw markup. */
    static String stripFrom(String answer) {
        return QUESTIONS_BLOCK.matcher(answer).replaceAll("").strip();
    }
}
