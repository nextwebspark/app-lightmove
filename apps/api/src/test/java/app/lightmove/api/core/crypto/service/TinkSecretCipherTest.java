package app.lightmove.api.core.crypto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.TestSecretCiphers;
import app.lightmove.api.core.crypto.model.EncryptionContext;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Envelope encryption of the secrets the app holds, under the dev-only keyset the tests share with npm run dev. */
class TinkSecretCipherTest {

    private final SecretCipher cipher = TestSecretCiphers.dev();
    private final EncryptionContext context = new EncryptionContext(UUID.randomUUID(), "integration-client-secret:GOOGLE");

    @Test
    @DisplayName("a secret comes back as it went in, and is not readable in between")
    void roundTrips() {
        String ciphertext = cipher.encrypt("s3cret~value", context);

        assertThat(ciphertext).doesNotContain("s3cret");
        assertThat(new String(Base64.getDecoder().decode(ciphertext))).doesNotContain("s3cret");
        assertThat(cipher.decrypt(ciphertext, context)).isEqualTo("s3cret~value");
    }

    @Test
    @DisplayName("the same secret encrypts differently every time, each under its own data key")
    void everyEncryptionDiffers() {
        assertThat(cipher.encrypt("same", context)).isNotEqualTo(cipher.encrypt("same", context));
    }

    @Test
    @DisplayName("a ciphertext moved to another workspace or another purpose does not decrypt there")
    void boundToItsContext() {
        String ciphertext = cipher.encrypt("s3cret", context);

        assertThatThrownBy(() -> cipher.decrypt(ciphertext,
                new EncryptionContext(UUID.randomUUID(), context.purpose())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(ciphertext,
                new EncryptionContext(context.workspaceId(), "integration-client-secret:ZOOM")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an altered ciphertext is refused rather than read")
    void tamperingIsRefused() {
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("s3cret", context));
        raw[raw.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(raw), context))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("with no key configured nothing is stored encrypted, and the refusal says why")
    void unconfiguredRefuses() {
        SecretCipher unconfigured = new UnconfiguredSecretCipher();

        assertThat(unconfigured.isAvailable()).isFalse();
        assertThatThrownBy(() -> unconfigured.encrypt("s3cret", context))
                .isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getCode()).isEqualTo(ErrorCode.INTEGRATION_ENCRYPTION_UNAVAILABLE));
    }
}
