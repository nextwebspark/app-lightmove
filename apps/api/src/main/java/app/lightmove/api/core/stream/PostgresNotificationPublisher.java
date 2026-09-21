package app.lightmove.api.core.stream;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The one place in the application that fires {@code pg_notify}.
 *
 * <p>{@code MANDATORY} is the correctness argument, not a convenience: Postgres holds a notification
 * issued inside a transaction and delivers it <i>at commit</i>, so the event rides the same commit as
 * the data it announces — no listener can act on it early, and a rollback silently swallows it. A
 * publish outside a transaction would deliver immediately and lose exactly that.
 */
@Component
@RequiredArgsConstructor
public class PostgresNotificationPublisher {

    /**
     * Postgres refuses a payload over 8000 bytes. Checked here rather than left to the driver because
     * the failure would otherwise surface as a constraint nobody recognises, at commit, in whichever
     * feature happened to grow its payload — and because a channel is a wake-up, not a transport:
     * anything approaching this limit is being shipped down the wrong pipe and should be read back
     * from its own table instead.
     */
    private static final int MAX_PAYLOAD_BYTES = 8000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String channel, Object payload) {
        String serialised = json.writeValueAsString(payload);
        if (serialised.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "A pg_notify payload must stay under " + MAX_PAYLOAD_BYTES
                            + " bytes; channel " + channel + " was handed " + serialised.length()
                            + " characters. Announce an id and let the reader fetch the content.");
        }
        jdbc.query("SELECT pg_notify(?, ?)", resultRow -> { }, channel, serialised);
    }
}
