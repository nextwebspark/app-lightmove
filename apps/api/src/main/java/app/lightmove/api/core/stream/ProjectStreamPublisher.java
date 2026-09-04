package app.lightmove.api.core.stream;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Announces a change by firing {@code pg_notify} on the caller's own transaction — Postgres holds the
 * notification and delivers it at commit, which is the entire correctness argument: the event rides
 * the same commit as the data, so no listener can act on it early, and a rollback silently swallows
 * it. {@code MANDATORY} because a publish outside a transaction would deliver immediately and lose
 * exactly that guarantee.
 */
@Component
@RequiredArgsConstructor
public class ProjectStreamPublisher {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID projectId, ProjectStreamKind kind) {
        String payload = json.writeValueAsString(new ProjectStreamNotification(projectId, kind.wire()));
        jdbc.query("SELECT pg_notify(?, ?)", resultRow -> { },
                PostgresStreamListener.CHANNEL, payload);
    }
}
