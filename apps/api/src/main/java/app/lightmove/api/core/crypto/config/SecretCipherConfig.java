package app.lightmove.api.core.crypto.config;

import app.lightmove.api.core.config.CredentialEncryptionSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.crypto.service.TinkSecretCipher;
import app.lightmove.api.core.crypto.service.UnconfiguredSecretCipher;
import java.security.GeneralSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one {@link SecretCipher}. The key-encryption key is a Tink keyset delivered from Secret Manager like every
 * other secret, rather than a Cloud KMS key: a KMS round trip on every decrypt would sit in front of every send
 * and token refresh, and a KMS outage would stop outreach. A keyset that does not parse fails the boot — a
 * deployment that silently stopped decrypting would be worse — while a missing one leaves encryption unoffered.
 */
@Slf4j
@Configuration
public class SecretCipherConfig {

    @Bean
    SecretCipher secretCipher(LightMoveProperties properties) throws GeneralSecurityException {
        CredentialEncryptionSettings settings = properties.crypto();
        if (settings == null || !settings.isConfigured()) {
            log.warn("No credential encryption key is configured (lightmove.crypto.keyset), so no workspace can "
                    + "store its own app's keys.");
            return new UnconfiguredSecretCipher();
        }
        return TinkSecretCipher.fromKeyset(settings.keyset());
    }
}
