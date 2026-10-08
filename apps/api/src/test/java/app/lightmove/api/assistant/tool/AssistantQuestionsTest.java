package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.assistant.model.AssistantQuestion;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question.Option;

class AssistantQuestionsTest {

    @Test
    @DisplayName("a question is kept as asked, trimmed, with its options and whether several may be picked")
    void keepsAQuestionAsAsked() {
        List<AssistantQuestion> kept = AssistantQuestions.from(List.of(new Question(
                "  Which markets? ", " Region ", options(2), true)));

        assertThat(kept).singleElement().satisfies(question -> {
            assertThat(question.question()).isEqualTo("Which markets?");
            assertThat(question.header()).isEqualTo("Region");
            assertThat(question.multiSelect()).isTrue();
            assertThat(question.options()).extracting("label").containsExactly("Option 1", "Option 2");
        });
    }

    @Test
    @DisplayName("the card's limits hold whatever the model sent: four questions, four options, a 12-character header")
    void holdsTheCardsLimits() {
        List<Question> asked = IntStream.rangeClosed(1, 6)
                .mapToObj(index -> new Question("Question " + index + "?", "A header far too long", options(6), null))
                .toList();

        List<AssistantQuestion> kept = AssistantQuestions.from(asked);

        assertThat(kept).hasSize(4).allSatisfy(question -> {
            assertThat(question.header()).isEqualTo("A header far");
            assertThat(question.options()).hasSize(4);
            assertThat(question.multiSelect()).isFalse();
        });
    }

    @Test
    @DisplayName("a question with fewer than two options is dropped, so the card never offers nothing to pick")
    void dropsAQuestionWithNothingToPick() {
        List<AssistantQuestion> kept = AssistantQuestions.from(List.of(
                new Question("Only one way?", "One", options(1), false),
                new Question("Two ways?", "Two", options(2), false)));

        assertThat(kept).extracting(AssistantQuestion::header).containsExactly("Two");
    }

    @Test
    @DisplayName("nothing asked, or nothing usable, is no questions at all")
    void answersNothingForNothingUsable() {
        List<Question> withNull = new ArrayList<>();
        withNull.add(null);

        assertThat(AssistantQuestions.from(null)).isEmpty();
        assertThat(AssistantQuestions.from(withNull)).isEmpty();
        assertThat(AssistantQuestions.from(List.of(new Question("One?", "One", options(1), false)))).isEmpty();
    }

    private static List<Option> options(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> new Option("Option " + index, "What option " + index + " means"))
                .toList();
    }
}
