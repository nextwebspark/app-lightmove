package app.lightmove.api.core.crypto.service;

import app.lightmove.api.core.crypto.model.EncryptionContext;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.KmsEnvelopeAead;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Envelope encryption: each value gets its own AES-256-GCM data key, stored beside it wrapped by the
 * key-encryption key (a Tink keyset held in Secret Manager). Rotating that keyset means adding a new primary key;
 * the key id travels in the ciphertext, so what was written under the old one still decrypts.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TinkSecretCipher implements SecretCipher {

    private final Aead envelope;

    /** @param keysetJson a Tink JSON keyset of AES-256-GCM keys, the key-encryption key */
    public static TinkSecretCipher fromKeyset(String keysetJson) throws GeneralSecurityException {
        AeadConfig.register();
        KeysetHandle keyEncryptionKeyset = TinkJsonProtoKeysetFormat.parseKeyset(keysetJson,
                InsecureSecretKeyAccess.get());
        Aead keyEncryptionKey = keyEncryptionKeyset.getPrimitive(RegistryConfiguration.get(), Aead.class);
        return new TinkSecretCipher(KmsEnvelopeAead.create(PredefinedAeadParameters.AES256_GCM, keyEncryptionKey));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String encrypt(String plaintext, EncryptionContext context) {
        try {
            byte[] ciphertext = envelope.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), context.associatedData());
            return Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("A secret could not be encrypted", failure);
        }
    }

    /** A value that fails here was altered, moved to another owner, or written under a key since removed. */
    @Override
    public String decrypt(String ciphertext, EncryptionContext context) {
        try {
            byte[] plaintext = envelope.decrypt(Base64.getDecoder().decode(ciphertext), context.associatedData());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw new IllegalStateException("A stored secret could not be decrypted", failure);
        }
    }
}
