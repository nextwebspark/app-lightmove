package app.lightmove.api.enrichment.sourcing.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

/** Answers as {@code GoogleGenAiChatModel} would, so the call's own Google options are merged onto these. */
final class RecordingChatModel implements ChatModel {

    private final String reply;
    final List<String> prompts = new ArrayList<>();
    final List<ChatOptions> options = new ArrayList<>();

    RecordingChatModel(String reply) {
        this.reply = reply;
    }

    @Override
    public ChatOptions getOptions() {
        return GoogleGenAiChatOptions.builder().model("gemini-2.5-flash").build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt.getInstructions().stream().map(message -> message.getText())
                .reduce("", (all, text) -> all + text + "\n"));
        options.add(prompt.getOptions());
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    }
}
