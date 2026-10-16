package app.uncava.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoteViewpointTest {

    private static final UUID CFO = UUID.randomUUID();
    private static final UUID CREDIT = UUID.randomUUID();

    @Test
    @DisplayName("a position reads its own notes and those about no position, never another position's")
    void aPositionReadsItsOwnAndTheShared() {
        NoteViewpoint fromCfo = NoteViewpoint.position(CFO);

        assertThat(fromCfo.reads(CFO)).isTrue();
        assertThat(fromCfo.reads(null)).isTrue();
        assertThat(fromCfo.reads(CREDIT)).isFalse();
    }

    @Test
    @DisplayName("the workspace's Candidates page reads every note")
    void theWorkspaceReadsEveryNote() {
        assertThat(NoteViewpoint.workspace().reads(CFO)).isTrue();
        assertThat(NoteViewpoint.workspace().reads(null)).isTrue();
    }

    @Test
    @DisplayName("a position's viewpoint without a position fails rather than reading everything")
    void aLostPositionFailsClosed() {
        assertThatNullPointerException().isThrownBy(() -> NoteViewpoint.position(null));
    }
}
