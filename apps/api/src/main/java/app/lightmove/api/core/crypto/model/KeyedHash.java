package app.lightmove.api.core.crypto.model;

/** A keyed hash and the id of the key it was taken under, kept beside it so a rotated keyset still reads it. */
public record KeyedHash(int keyId, String hex) {
}
