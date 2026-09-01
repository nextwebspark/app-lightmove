package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestLlmCallPolicy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/** No GCP credentials or network calls — a hand-rolled {@link ChatModel} stands in for Gemini. */
class CandidateShortlistServiceTest {

    private static final Resource SYSTEM_PROMPT = new ByteArrayResource("Assess the candidate.".getBytes());

    @Test
    void sendsBothFieldsAndReturnsTheModelsVerdict() {
        RecordingChatModel chatModel = new RecordingChatModel("SHORTLIST — strong sector match.");
        CandidateShortlistService service =
                new CandidateShortlistService(ChatClient.builder(chatModel).build(), SYSTEM_PROMPT,
                        TestLlmCallPolicy.asShipped());

        String verdict = service.shortlist(
                "Looking for a CFO in fintech.", "10 years as CFO at a fintech scale-up.");

        assertThat(verdict).isEqualTo("SHORTLIST — strong sector match.");

        String userText = chatModel.lastPrompt.getInstructions().stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .findFirst()
                .orElseThrow();
        assertThat(userText).contains("Looking for a CFO in fintech.");
        assertThat(userText).contains("10 years as CFO at a fintech scale-up.");
    }

    private static final class RecordingChatModel implements ChatModel {
        private final String reply;
        private Prompt lastPrompt;

        private RecordingChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }
}
