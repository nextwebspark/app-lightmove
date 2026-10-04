package app.lightmove.api.enrichment.sourcing.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * One company's refinement dialogue, held only while that company is searched. The model keeps
 * nothing between calls, so every earlier turn — its own answers included — is replayed on each one;
 * that replay is what stops it proposing words a round already tried.
 */
final class RefineConversation {

    private final List<Message> turns = new ArrayList<>();
    private final List<String> untold = new ArrayList<>();

    void tell(String text) {
        untold.add(text);
    }

    List<Message> history() {
        return List.copyOf(turns);
    }

    String nextTurn() {
        return String.join("\n\n", untold);
    }

    void answered(String answer) {
        turns.add(new UserMessage(nextTurn()));
        turns.add(new AssistantMessage(answer));
        untold.clear();
    }
}
