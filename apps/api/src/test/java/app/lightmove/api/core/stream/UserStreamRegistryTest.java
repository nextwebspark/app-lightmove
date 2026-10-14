package app.lightmove.api.core.stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** A person's own stream: one session may hold a handful of tabs open, never an unbounded number. */
class UserStreamRegistryTest {

    private final UserStreamRegistry registry = new UserStreamRegistry();

    @Test
    @DisplayName("a stream past the cap closes the person's oldest and keeps the rest")
    void aStreamPastTheCapClosesTheOldest() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        List<SseEmitter> opened = new ArrayList<>();
        for (int tab = 0; tab <= UserStreamRegistry.MAX_STREAMS_PER_PERSON; tab++) {
            opened.add(registry.subscribe(userId, workspaceId));
        }

        assertThatThrownBy(() -> opened.getFirst().send("ping")).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> opened.get(1).send("ping")).doesNotThrowAnyException();
        assertThatCode(() -> opened.getLast().send("ping")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the cap is per person and workspace, so another's streams are never closed")
    void theCapIsPerPersonAndWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        SseEmitter someoneElse = registry.subscribe(UUID.randomUUID(), workspaceId);
        UUID userId = UUID.randomUUID();
        for (int tab = 0; tab <= UserStreamRegistry.MAX_STREAMS_PER_PERSON; tab++) {
            registry.subscribe(userId, workspaceId);
        }

        assertThatCode(() -> someoneElse.send("ping")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a dead stream is dropped rather than breaking the nudge")
    void aDeadStreamIsDroppedRatherThanBreakingTheNudge() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        registry.subscribe(userId, workspaceId).complete();

        assertThatCode(() -> {
            registry.nudge(userId, workspaceId);
            registry.nudge(userId, workspaceId);
        }).doesNotThrowAnyException();
    }
}
