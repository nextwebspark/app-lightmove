package app.lightmove.api.core.crypto.service;

import app.lightmove.api.core.crypto.model.KeyedHash;
import java.util.List;
import java.util.Set;

/** A deployment with no keyset: it boots, nothing is hashed, and every lookup matches nothing. */
public class UnconfiguredKeyedHasher implements KeyedHasher {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public KeyedHash hash(String value) {
        throw new IllegalStateException("No keyed-hash keyset is configured; check isAvailable() first");
    }

    @Override
    public List<KeyedHash> hashUnderEveryKey(String value) {
        return List.of();
    }

    @Override
    public Set<Integer> keyIds() {
        return Set.of();
    }
}
