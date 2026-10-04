package app.lightmove.api.core.crypto.service;

import app.lightmove.api.core.crypto.model.EncryptionContext;

/**
 * Encrypts the secrets the app has to hold — a customer's OAuth client secret, a mailbox's refresh token — for
 * storage. The ciphertext is text, for a {@code text} column; it must be decrypted with the context it was
 * encrypted under. Neither side of it is ever logged or returned to a client.
 */
public interface SecretCipher {

    /** False on a deployment with no key: nothing may then be stored encrypted, and callers refuse the write. */
    boolean isAvailable();

    String encrypt(String plaintext, EncryptionContext context);

    String decrypt(String ciphertext, EncryptionContext context);
}
