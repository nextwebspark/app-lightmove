package app.lightmove.api.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestSecretCiphers;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A settings record holding a secret prints everything but the secret: log lines and bind failures show records whole. */
class SecretSettingsToStringTest {

    @Test
    @DisplayName("the key-encryption keyset never prints, alone or inside the root properties")
    void keysetIsRedacted() {
        CredentialEncryptionSettings crypto = new CredentialEncryptionSettings(TestSecretCiphers.DEV_KEYSET);
        LightMoveProperties properties = new LightMoveProperties(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, crypto, null, null);

        assertThat(crypto.toString()).doesNotContain("GiDd").contains("<redacted>");
        assertThat(properties.toString()).doesNotContain("GiDd");
    }

    @Test
    @DisplayName("a shared app's client secret never prints; its client id still does")
    void clientSecretIsRedacted() {
        ProviderAppSettings app = new ProviderAppSettings("uncava-client", "uncava-secret", List.of("Mail.Send"), "", "");

        assertThat(app.toString()).doesNotContain("uncava-secret").contains("uncava-client");
    }

    @Test
    @DisplayName("Recall's key and webhook secret never print")
    void recallSecretsAreRedacted() {
        RecallSettings recall = new RecallSettings("recall-key", "recall-webhook-secret", "https://us-east-1.recall.ai");

        assertThat(recall.toString()).doesNotContain("recall-key").doesNotContain("recall-webhook-secret");
    }
}
