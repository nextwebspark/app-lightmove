package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.model.AssistantQuestion;
import app.lightmove.api.assistant.model.AssistantQuestionOption;
import java.util.List;
import java.util.Objects;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question.Option;

/**
 * The model's questions, held to what the card can draw: at most four, each with a question, a header of at
 * most twelve characters and two to four labelled options. The library only logs a question outside those
 * limits, so one that cannot be answered on the card is dropped here rather than drawn with nothing to pick.
 */
public final class AssistantQuestions {

    private static final int MAX_QUESTIONS = 4;
    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 4;
    private static final int MAX_HEADER = 12;

    private AssistantQuestions() {
    }

    public static List<AssistantQuestion> from(List<Question> asked) {
        if (asked == null) {
            return List.of();
        }
        return asked.stream()
                .filter(Objects::nonNull)
                .map(AssistantQuestions::toCard)
                .filter(Objects::nonNull)
                .limit(MAX_QUESTIONS)
                .toList();
    }

    private static AssistantQuestion toCard(Question asked) {
        String question = stripped(asked.question());
        String header = stripped(asked.header());
        if (question == null || header == null || asked.options() == null) {
            return null;
        }
        List<AssistantQuestionOption> options = asked.options().stream()
                .map(AssistantQuestions::toOption)
                .filter(Objects::nonNull)
                .limit(MAX_OPTIONS)
                .toList();
        if (options.size() < MIN_OPTIONS) {
            return null;
        }
        return new AssistantQuestion(question,
                header.length() <= MAX_HEADER ? header : header.substring(0, MAX_HEADER).strip(),
                options, Boolean.TRUE.equals(asked.multiSelect()));
    }

    private static AssistantQuestionOption toOption(Option option) {
        String label = option == null ? null : stripped(option.label());
        if (label == null) {
            return null;
        }
        String description = stripped(option.description());
        return new AssistantQuestionOption(label, description == null ? "" : description);
    }

    private static String stripped(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
