package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.model.SuppressionKey;
import app.lightmove.api.candidate.model.WorkspaceSuppression;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SuppressionGateTest {

    private static final UUID WORKSPACE = UUID.randomUUID();

    @Test
    @DisplayName("a page carrying one suppressed slug in two spellings has both left out")
    void everySpellingOfASuppressedSlugIsSuppressed() {
        SuppressionEntries entries = mock(SuppressionEntries.class);
        SuppressionKey janeDoe = SuppressionKey.ofProfileSlug("jane-doe");
        when(entries.entriesFor(eq(WORKSPACE), anyCollection())).thenAnswer(call ->
                call.<Collection<SuppressionKey>>getArgument(1).contains(janeDoe)
                        ? Map.of(janeDoe, mock(WorkspaceSuppression.class))
                        : Map.of());

        assertThat(new SuppressionGate(entries)
                .suppressedProfileSlugsOf(WORKSPACE, List.of("Jane-Doe", "jane-doe", "omar-saleh")))
                .containsExactlyInAnyOrder("Jane-Doe", "jane-doe");
    }
}
