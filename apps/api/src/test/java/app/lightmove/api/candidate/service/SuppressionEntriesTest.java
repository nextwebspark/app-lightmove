package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.TestKeyedHashers;
import app.lightmove.api.candidate.constant.SuppressionKind;
import app.lightmove.api.candidate.model.SuppressionKey;
import app.lightmove.api.candidate.model.WorkspaceSuppression;
import app.lightmove.api.candidate.repository.WorkspaceSuppressionRepository;
import app.lightmove.api.core.crypto.service.KeyedHasher;
import app.lightmove.api.core.crypto.service.UnconfiguredKeyedHasher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SuppressionEntriesTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final SuppressionKey NOOR = SuppressionKey.ofProfileSlug("noor-haddad");

    private final WorkspaceSuppressionRepository suppressions = mock(WorkspaceSuppressionRepository.class);

    @Test
    @DisplayName("an entry written before a rotation still matches under the rotated keyset")
    void anEntryUnderTheOldKeyStillMatches() {
        String writtenUnder = TestKeyedHashers.dev().hash(NOOR.hashInput(WORKSPACE)).hex();
        WorkspaceSuppression entry = entry(SuppressionKind.PROFILE_SLUG, writtenUnder);
        when(suppressions.findByWorkspaceIdAndValueHashIn(eq(WORKSPACE), anyCollection())).thenAnswer(call ->
                call.<Collection<String>>getArgument(1).contains(writtenUnder) ? List.of(entry) : List.of());
        KeyedHasher rotated = TestKeyedHashers.of(TestKeyedHashers.rotated(TestKeyedHashers.DEV_KEYSET));

        assertThat(new SuppressionEntries(rotated, suppressions).entriesFor(WORKSPACE, List.of(NOOR)))
                .containsEntry(NOOR, entry);
    }

    @Test
    @DisplayName("an entry under a key the keyset no longer holds refuses the door rather than letting anyone through")
    void anUnreadableEntryRefuses() {
        when(suppressions.existsByWorkspaceIdAndKeyIdNotIn(WORKSPACE, Set.of(TestKeyedHashers.DEV_KEY_ID)))
                .thenReturn(true);

        assertThatThrownBy(() -> new SuppressionEntries(TestKeyedHashers.dev(), suppressions)
                .entriesFor(WORKSPACE, List.of(NOOR)))
                .isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getCode()).isEqualTo(ErrorCode.SUPPRESSION_UNAVAILABLE));
    }

    @Test
    @DisplayName("with no keyset, a workspace holding entries is refused and one holding none is not")
    void noKeysetRefusesOnlyAWorkspaceWithEntries() {
        SuppressionEntries entries = new SuppressionEntries(new UnconfiguredKeyedHasher(), suppressions);
        UUID listing = UUID.randomUUID();
        when(suppressions.existsByWorkspaceId(listing)).thenReturn(true);

        assertThat(entries.entriesFor(WORKSPACE, List.of(NOOR))).isEmpty();
        assertThatThrownBy(() -> entries.entriesFor(listing, List.of(NOOR))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> entries.hashToStore(WORKSPACE, NOOR))
                .isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getCode()).isEqualTo(ErrorCode.SUPPRESSION_UNAVAILABLE));
    }

    @Test
    @DisplayName("a new entry is hashed under the keyset's primary key")
    void aNewEntryIsHashedUnderThePrimary() {
        String rotatedKeyset = TestKeyedHashers.rotated(TestKeyedHashers.DEV_KEYSET);
        KeyedHasher rotated = TestKeyedHashers.of(rotatedKeyset);

        assertThat(new SuppressionEntries(rotated, suppressions).hashToStore(WORKSPACE, NOOR))
                .isEqualTo(rotated.hash(NOOR.hashInput(WORKSPACE)));
    }

    private static WorkspaceSuppression entry(SuppressionKind kind, String valueHash) {
        WorkspaceSuppression entry = mock(WorkspaceSuppression.class);
        when(entry.getKind()).thenReturn(kind);
        when(entry.getValueHash()).thenReturn(valueHash);
        return entry;
    }
}
