package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.assistant.model.AssistantQuestion;
import app.lightmove.api.assistant.model.AssistantQuestionOption;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionMemoryTest {

    private static final List<AssistantQuestion> ASKED = List.of(new AssistantQuestion(
            "Which markets should the companies operate in?", "Region",
            List.of(new AssistantQuestionOption("GCC only", "The six Gulf states"),
                    new AssistantQuestionOption("MENA", "Adds Egypt, Jordan and Morocco")),
            false));

    @Test
    @DisplayName("an earlier answer's questions are written back with every option it offered")
    void rendersTheQuestionsAsked() {
        assertThat(QuestionMemory.render(ASKED)).isEqualTo("""
                <asked_consultant>
                - Region: Which markets should the companies operate in?
                  - GCC only — The six Gulf states
                  - MENA — Adds Egypt, Jordan and Morocco
                </asked_consultant>""");
    }

    @Test
    @DisplayName("a block the model copies into its answer is taken out")
    void stripsAnEchoedBlock() {
        assertThat(QuestionMemory.stripFrom("Searching the GCC.\n\n" + QuestionMemory.render(ASKED)))
                .isEqualTo("Searching the GCC.");
    }
}
