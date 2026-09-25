package app.lightmove.api.core.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The listener's wiring is checked at startup rather than at delivery time, because both mistakes it
 * can make are silent at runtime: an illegal channel name reaches {@code LISTEN} as concatenated SQL,
 * and two handlers on one channel each see the other's payloads and log them as malformed — which
 * reads as a serialisation bug rather than a wiring one.
 */
class PostgresStreamListenerTest {

    @Test
    @DisplayName("two handlers claiming one channel is refused, naming both")
    void twoHandlersOnOneChannelAreRefused() {
        assertThatThrownBy(() -> new PostgresStreamListener(null,
                List.of(handlerOn("lm_shared"), handlerOn("lm_shared"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lm_shared");
    }

    @Test
    @DisplayName("a channel name that is not a Postgres identifier is refused at startup")
    void anIllegalChannelNameIsRefused() {
        // LISTEN takes no bind parameter, so this would otherwise be concatenated into SQL.
        assertThatThrownBy(() -> new PostgresStreamListener(null, List.of(handlerOn("lm x; DROP TABLE t"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PostgresStreamListener(null, List.of(handlerOn("LM_Upper"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PostgresStreamListener(null, List.of(handlerOn("9leading"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PostgresStreamListener(null, List.of(handlerOn(""))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PostgresStreamListener(null, List.of(handlerOn(null))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("distinct legal channels are accepted")
    void distinctLegalChannelsAreAccepted() {
        assertThatCode(() -> new PostgresStreamListener(null,
                List.of(handlerOn("lm_project_stream"), handlerOn("lm_other_stream"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("with no handlers registered the listener holds no connection at all")
    void withNoHandlersNoConnectionIsHeld() {
        PostgresStreamListener listener = new PostgresStreamListener(null, List.of());

        listener.start();

        // The null DataSource is the proof: start() returned without ever borrowing a connection,
        // which it could not have done had it begun listening.
        assertThat(listener.isRunning()).isFalse();
    }

    private static PostgresNotificationHandler handlerOn(String channel) {
        return new PostgresNotificationHandler() {
            @Override
            public String channel() {
                return channel;
            }

            @Override
            public void handle(String payload) {
                throw new UnsupportedOperationException("not delivered in this test");
            }
        };
    }
}
