package app.lightmove.api.core.stream;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The registry's one hard job is surviving its emitters: a browser that navigated away mid-broadcast
 * must cost the mandate's other watchers nothing.
 */
class ProjectStreamRegistryTest {

    private final ProjectStreamRegistry registry = new ProjectStreamRegistry();

    @Test
    @DisplayName("a dead stream is dropped rather than breaking the broadcast")
    void aDeadStreamIsDroppedRatherThanBreakingTheBroadcast() {
        UUID projectId = UUID.randomUUID();
        SseEmitter departed = registry.subscribe(projectId);
        departed.complete();

        assertThatCode(() -> {
            registry.broadcast(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
            registry.broadcast(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a mandate nobody is watching swallows its broadcast")
    void aMandateNobodyIsWatchingSwallowsItsBroadcast() {
        assertThatCode(() -> registry.broadcast(UUID.randomUUID(), ProjectStreamKind.COMPANY_ENRICHED))
                .doesNotThrowAnyException();
    }
}
