package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.SuppressionKey;
import app.lightmove.api.candidate.model.WorkspaceSuppression;
import app.lightmove.api.candidate.repository.WorkspaceSuppressionRepository;
import app.lightmove.api.core.crypto.model.KeyedHash;
import app.lightmove.api.core.crypto.service.KeyedHasher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The list's entries for some keys, found by each key's hash under every key of the suppression keyset, so an
 * entry written before a rotation still matches. Where the workspace holds an entry no key of this deployment's
 * keyset can read, it refuses rather than answering "not listed", which would let that person back in.
 */
@Service
@RequiredArgsConstructor
class SuppressionEntries {

    private final KeyedHasher hasher;
    private final WorkspaceSuppressionRepository suppressions;

    Map<SuppressionKey, WorkspaceSuppression> entriesFor(UUID workspaceId, Collection<SuppressionKey> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        refuseUnreadable(workspaceId);
        Map<String, SuppressionKey> keyByHash = new HashMap<>();
        for (SuppressionKey key : keys) {
            hasher.hashUnderEveryKey(key.hashInput(workspaceId)).forEach(hash -> keyByHash.put(hash.hex(), key));
        }
        if (keyByHash.isEmpty()) {
            return Map.of();
        }
        Map<SuppressionKey, WorkspaceSuppression> entries = new HashMap<>();
        for (WorkspaceSuppression entry : suppressions.findByWorkspaceIdAndValueHashIn(workspaceId,
                keyByHash.keySet())) {
            SuppressionKey key = keyByHash.get(entry.getValueHash());
            if (key.kind() == entry.getKind()) {
                entries.put(key, entry);
            }
        }
        return entries;
    }

    /** Under the keyset's primary key: what a new entry stores. */
    KeyedHash hashToStore(UUID workspaceId, SuppressionKey key) {
        if (!hasher.isAvailable()) {
            throw ApiException.of(ErrorCode.SUPPRESSION_UNAVAILABLE);
        }
        return hasher.hash(key.hashInput(workspaceId));
    }

    private void refuseUnreadable(UUID workspaceId) {
        Set<Integer> keyIds = hasher.keyIds();
        boolean unreadable = keyIds.isEmpty()
                ? suppressions.existsByWorkspaceId(workspaceId)
                : suppressions.existsByWorkspaceIdAndKeyIdNotIn(workspaceId, keyIds);
        if (unreadable) {
            throw ApiException.of(ErrorCode.SUPPRESSION_UNAVAILABLE);
        }
    }
}
