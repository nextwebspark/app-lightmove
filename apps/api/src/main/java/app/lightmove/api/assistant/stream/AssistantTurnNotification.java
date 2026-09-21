package app.lightmove.api.assistant.stream;

import java.util.UUID;

/**
 * The {@code pg_notify} payload: a turn moved, and how far.
 *
 * <p><b>A wake-up, not a transport.</b> Postgres caps a payload at 8000 bytes and the project stream
 * sets the precedent of announcing a change rather than shipping it — but here the reason is stronger
 * than size. Nothing is ever sent to a browser from this record: the seq only tells a subscriber
 * whether it is already caught up, and the content is read back from
 * {@code app_lm_assistant_event} by cursor. That is what makes a reconnect replay from where it left
 * off rather than losing whatever committed while the socket was down.
 */
public record AssistantTurnNotification(UUID turnId, int seq) {

    /** Its own channel, not an extra kind on the project stream's — see the package docs. */
    public static final String CHANNEL = "lm_assistant_turn_stream";
}
