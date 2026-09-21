package app.lightmove.api.assistant.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.constant.AssistantEventKind;
import app.lightmove.api.assistant.model.AssistantEvent;
import app.lightmove.api.assistant.repository.AssistantEventRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * The cursor is the whole correctness argument, so these are the cases that would silently lose or
 * repeat part of an answer.
 */
class AssistantTurnSubscriberTest {

    private final UUID turnId = UUID.randomUUID();
    private final AssistantEventRepository events = mock(AssistantEventRepository.class);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("the cursor does not advance past an event the client never received")
    void theCursorDoesNotAdvancePastAFailedSend() {
        SseEmitter departed = new SseEmitter();
        departed.complete();
        AssistantTurnSubscriber subscriber = new AssistantTurnSubscriber(departed, turnId, 0, json);
        havingEvents(event(1, AssistantEventKind.TURN_STARTED));

        assertThat(subscriber.drain(events)).as("a dead emitter means drop me").isFalse();

        // Had the cursor moved before the send, a reconnect at this seq would skip event 1 entirely —
        // which for an answer chunk is a hole in the text rather than a repeated frame.
        assertThat(subscriber.mightHaveMissed(1)).isTrue();
    }

    @Test
    @DisplayName("a terminal event ends the stream instead of holding the browser to the 55s close")
    void aTerminalEventEndsTheStream() {
        AssistantTurnSubscriber subscriber = subscriberAt(0);
        havingEvents(event(1, AssistantEventKind.ANSWER), event(2, AssistantEventKind.TURN_FINISHED));

        assertThat(subscriber.drain(events)).isTrue();

        assertThat(subscriber.isFinished()).isTrue();
        assertThat(subscriber.mightHaveMissed(3))
                .as("a finished turn has nothing more to say, whatever a late NOTIFY claims")
                .isFalse();
    }

    @Test
    @DisplayName("a second wake while a drain is queued schedules nothing")
    void concurrentWakesCoalesce() {
        AssistantTurnSubscriber subscriber = subscriberAt(0);

        assertThat(subscriber.claimDrain()).as("the first wake claims it").isTrue();
        assertThat(subscriber.claimDrain()).as("the second must not queue a second task").isFalse();

        havingEvents();
        subscriber.drain(events);

        assertThat(subscriber.claimDrain()).as("claimable again once drained").isTrue();
    }

    @Test
    @DisplayName("a released claim can be taken again, so a rejected drain is not a stuck stream")
    void aReleasedClaimCanBeRetaken() {
        AssistantTurnSubscriber subscriber = subscriberAt(0);
        subscriber.claimDrain();

        subscriber.releaseDrain();

        assertThat(subscriber.claimDrain()).isTrue();
    }

    @Test
    @DisplayName("the client's cursor is where the replay starts")
    void theClientsCursorIsWhereTheReplayStarts() {
        AssistantTurnSubscriber subscriber = subscriberAt(7);

        assertThat(subscriber.mightHaveMissed(7)).as("already seen").isFalse();
        assertThat(subscriber.mightHaveMissed(8)).isTrue();

        havingEvents();
        subscriber.drain(events);

        // The read is issued from the client's cursor, not from zero — a reconnect mid-turn must not
        // resend the whole conversation.
        org.mockito.Mockito.verify(events)
                .findByTurnIdAndSeqGreaterThanOrderBySeqAsc(eq(turnId), eq(7), any());
    }

    private AssistantTurnSubscriber subscriberAt(int afterSeq) {
        return new AssistantTurnSubscriber(new SseEmitter(), turnId, afterSeq, json);
    }

    /** Returns the given events on the first read and nothing after, as a real turn would. */
    private void havingEvents(AssistantEvent... first) {
        when(events.findByTurnIdAndSeqGreaterThanOrderBySeqAsc(any(), anyInt(), any()))
                .thenReturn(List.of(first), List.of());
    }

    private AssistantEvent event(int seq, AssistantEventKind kind) {
        return AssistantEvent.of(turnId, seq, kind, Map.of("text", "anything"));
    }
}
