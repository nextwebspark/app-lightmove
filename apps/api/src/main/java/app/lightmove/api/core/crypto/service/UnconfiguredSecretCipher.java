package app.lightmove.api.core.crypto.service;

import app.lightmove.api.core.crypto.model.EncryptionContext;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;

/** A deployment with no key-encryption key: it boots, and anything that would store a secret is refused. */
public class UnconfiguredSecretCipher implements SecretCipher {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String encrypt(String plaintext, EncryptionContext context) {
        throw ApiException.of(ErrorCode.INTEGRATION_ENCRYPTION_UNAVAILABLE);
    }

    @Override
    public String decrypt(String ciphertext, EncryptionContext context) {
        throw ApiException.of(ErrorCode.INTEGRATION_ENCRYPTION_UNAVAILABLE);
    }
}
