package app.uncava.api.core.llm.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * Drops the backslash of an escape JSON does not have before the schema advisor reads the answer: Gemini
 * at temperature 0 wrote {@code "\协"} in a snippet, and its repair attempt wrote the same answer again.
 */
@RequiredArgsConstructor
@Slf4j
final class JsonEscapeRepairAdvisor implements CallAdvisor {

    private static final String VALID_ESCAPES = "\"\\/bfnrt";
    private static final int UNICODE_ESCAPE_DIGITS = 4;

    private final int order;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        ChatResponse answer = response.chatResponse();
        if (answer == null || answer.getResults().isEmpty()) {
            return response;
        }
        boolean repaired = false;
        List<Generation> generations = new ArrayList<>();
        for (Generation generation : answer.getResults()) {
            AssistantMessage output = generation.getOutput();
            String text = output == null ? null : output.getText();
            String fixed = text == null ? null : withValidEscapes(text);
            if (fixed == null || fixed.equals(text)) {
                generations.add(generation);
                continue;
            }
            repaired = true;
            generations.add(new Generation(output.mutate().content(fixed).build(), generation.getMetadata()));
        }
        if (!repaired) {
            return response;
        }
        log.info("Prompt {} answered with an escape JSON does not have; its backslash was dropped before "
                + "validation.", request.context() == null ? null : request.context().get(ChatCallLog.PROMPT_ID_ATTRIBUTE));
        return response.mutate()
                .chatResponse(ChatResponse.builder().from(answer).generations(generations).build())
                .build();
    }

    static String withValidEscapes(String json) {
        StringBuilder repaired = null;
        int index = 0;
        while (index < json.length()) {
            char current = json.charAt(index);
            if (current != '\\' || index + 1 >= json.length()) {
                if (repaired != null) {
                    repaired.append(current);
                }
                index++;
                continue;
            }
            char escaped = json.charAt(index + 1);
            if (VALID_ESCAPES.indexOf(escaped) >= 0 || isUnicodeEscape(json, index + 1)) {
                if (repaired != null) {
                    repaired.append(current).append(escaped);
                }
            } else {
                if (repaired == null) {
                    repaired = new StringBuilder(json.length()).append(json, 0, index);
                }
                repaired.append(escaped);
            }
            index += 2;
        }
        return repaired == null ? json : repaired.toString();
    }

    private static boolean isUnicodeEscape(String json, int markerIndex) {
        if (json.charAt(markerIndex) != 'u' || markerIndex + UNICODE_ESCAPE_DIGITS >= json.length()) {
            return false;
        }
        for (int digit = 1; digit <= UNICODE_ESCAPE_DIGITS; digit++) {
            if (Character.digit(json.charAt(markerIndex + digit), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getName() {
        return "JSON escape repair";
    }

    @Override
    public int getOrder() {
        return order;
    }
}
