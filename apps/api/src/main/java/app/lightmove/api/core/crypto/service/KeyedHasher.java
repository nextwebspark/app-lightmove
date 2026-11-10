package app.lightmove.api.core.crypto.service;

import app.lightmove.api.core.crypto.model.KeyedHash;
import java.util.List;
import java.util.Set;

/**
 * A deterministic hash under a secret key, for a value the app must recognise without holding it. A plain hash
 * of a small space (an email, a LinkedIn slug) is reversed by hashing guesses; without the key nobody can.
 */
public interface KeyedHasher {

    /** False on a deployment with no keyset: nothing can be hashed, and callers refuse the write. */
    boolean isAvailable();

    /** Under the primary key: what a new row stores. */
    KeyedHash hash(String value);

    /** Under every key the keyset holds, so a row written before a rotation still matches. */
    List<KeyedHash> hashUnderEveryKey(String value);

    /** The ids of the keys the keyset holds; a row under any other can no longer be matched. */
    Set<Integer> keyIds();
}
