package app.lightmove.api.core.llm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DisabledLlmConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(DisabledLlmConfig.class);

    @Test
    @DisplayName("registers no model unless AI is switched off explicitly")
    void staysOutOfAnOrdinaryBoot() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(ChatModel.class);
            assertThat(context).doesNotHaveBean(EmbeddingModel.class);
        });
        runner.withPropertyValues("lightmove.llm.enabled=true").run(context ->
                assertThat(context).doesNotHaveBean(ChatModel.class));
    }

    @Test
    @DisplayName("with AI off, both models fail every call, which every caller reads as Vertex unreachable")
    void failsEveryCallWhenSwitchedOff() {
        runner.withPropertyValues("lightmove.llm.enabled=false").run(context -> {
            assertThatThrownBy(() -> context.getBean(ChatModel.class).call(new Prompt("hello")))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> context.getBean(EmbeddingModel.class).embed(new Document("hello")))
                    .isInstanceOf(IllegalStateException.class);
        });
    }
}
