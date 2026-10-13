package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.AssistantRoundFailed;
import app.lightmove.api.assistant.tool.TurnRecorder;
import com.google.genai.errors.ClientException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class AssistantRoundsTest {

    private final TurnRecorder recorder = new TurnRecorder(step -> { });
    private final ChatClientRequest request = mock(ChatClientRequest.class);
    private final CallAdvisorChain chain = mock(CallAdvisorChain.class);
    private final CallAdvisorChain rest = mock(CallAdvisorChain.class);

    @Test
    @DisplayName("a round the connection lost is asked once more, and its answer is the round's")
    void asksARoundThatTimedOutAgain() {
        AssistantRounds rounds = roundsWith(Duration.ofMinutes(2));
        when(chain.copy(rounds)).thenReturn(rest);
        when(rest.nextCall(request)).thenThrow(timedOut()).thenReturn(answering("Four utilities", 100, 20));

        ChatClientResponse response = rounds.adviseCall(request, chain);

        assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo("Four utilities");
        verify(rest, times(2)).nextCall(request);
    }

    @Test
    @DisplayName("a round the provider throttled is asked again")
    void asksAThrottledRoundAgain() {
        AssistantRounds rounds = roundsWith(Duration.ofMinutes(2));
        when(chain.copy(rounds)).thenReturn(rest);
        when(rest.nextCall(request)).thenThrow(throttled()).thenReturn(answering("Four utilities", 100, 20));

        ChatClientResponse response = rounds.adviseCall(request, chain);

        assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo("Four utilities");
        assertThat(recorder.roundTimings().reAsked()).isEqualTo(1);
    }

    @Test
    @DisplayName("an attempt the model has not answered within the round's patience is left and asked again")
    void leavesAnAttemptThatOutlastsItsPatience() {
        AssistantRounds rounds = roundsWith(Duration.ofMinutes(2), Duration.ofMillis(50));
        when(chain.copy(rounds)).thenReturn(rest);
        when(rest.nextCall(request))
                .thenAnswer(call -> {
                    Thread.sleep(5_000);
                    return answering("too late", 100, 20);
                })
                .thenReturn(answering("Four utilities", 100, 20));

        ChatClientResponse response = rounds.adviseCall(request, chain);

        assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo("Four utilities");
    }

    @Test
    @DisplayName("a round's last attempt is waited for past its patience")
    void waitsForTheLastAttempt() {
        AssistantRounds rounds = roundsWith(Duration.ofMinutes(2), Duration.ofMillis(50));
        when(chain.copy(rounds)).thenReturn(rest);
        when(rest.nextCall(request))
                .thenThrow(timedOut())
                .thenThrow(timedOut())
                .thenAnswer(call -> {
                    Thread.sleep(200);
                    return answering("A long list", 100, 20);
                });

        ChatClientResponse response = rounds.adviseCall(request, chain);

        assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo("A long list");
    }

    @Test
    @DisplayName("a round that loses its connection on every attempt fails")
    void givesUpAfterTheLastTimeout() {
        AssistantRounds rounds = roundsWith(Duration.ofMinutes(2));
        when(chain.copy(rounds)).thenReturn(rest);
        when(rest.nextCall(request)).thenThrow(timedOut());

        assertThatThrownBy(() -> rounds.adviseCall(request, chain)).isInstanceOf(AssistantRoundFailed.class);

        verify(rest, times(3)).nextCall(request);
    }

    @Test
    @DisplayName("a round the model refused fails without being asked again")
    void doesNotRepeatARefusal() {
        AssistantRounds rounds = roundsWith(Duration.ofMinutes(2));
        when(chain.copy(rounds)).thenReturn(rest);
        when(rest.nextCall(request)).thenThrow(new IllegalArgumentException("bad request"));

        assertThatThrownBy(() -> rounds.adviseCall(request, chain)).isInstanceOf(AssistantRoundFailed.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);

        verify(rest, times(1)).nextCall(request);
    }

    @Test
    @DisplayName("no round starts once the answer has passed its deadline")
    void startsNoRoundPastTheDeadline() {
        AssistantRounds rounds = roundsWith(Duration.ZERO);

        assertThatThrownBy(() -> rounds.adviseCall(request, chain)).isInstanceOf(AssistantRoundFailed.class);

        verify(chain, never()).copy(any());
    }

    @Test
    @DisplayName("an answer's tokens are the sum of its rounds")
    void countsEachRoundsTokens() {
        AssistantRounds rounds = roundsWith(Duration.ofMinutes(2));
        when(chain.copy(rounds)).thenReturn(rest);
        when(rest.nextCall(request)).thenReturn(answering("searching", 100, 20), answering("done", 300, 40));

        rounds.adviseCall(request, chain);
        rounds.adviseCall(request, chain);

        assertThat(recorder.spend().model()).isEqualTo("gemini-2.5-flash");
        assertThat(recorder.spend().inputTokens()).isEqualTo(400);
        assertThat(recorder.spend().outputTokens()).isEqualTo(60);
        assertThat(recorder.roundTimings().rounds()).isEqualTo(2);
    }

    private AssistantRounds roundsWith(Duration answerDeadline) {
        return roundsWith(answerDeadline, Duration.ofSeconds(30));
    }

    private AssistantRounds roundsWith(Duration answerDeadline, Duration patience) {
        return new AssistantRounds(recorder, "gemini-2.5-flash", answerDeadline, patience,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    private static RuntimeException throttled() {
        return new RuntimeException("Failed to generate content",
                new ClientException(429, "RESOURCE_EXHAUSTED", "Resource exhausted. Please try again later."));
    }

    private static RuntimeException timedOut() {
        return new RuntimeException("Failed to generate content",
                new IllegalStateException("Failed to execute HTTP request.", new InterruptedIOException("timeout")));
    }

    private static ChatClientResponse answering(String text, int inputTokens, int outputTokens) {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(inputTokens, outputTokens)).build());
        return ChatClientResponse.builder().chatResponse(response).build();
    }
}
