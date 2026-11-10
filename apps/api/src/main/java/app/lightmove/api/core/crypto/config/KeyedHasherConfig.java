package app.lightmove.api.core.crypto.config;

import app.lightmove.api.core.config.CredentialEncryptionSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.crypto.service.KeyedHasher;
import app.lightmove.api.core.crypto.service.TinkKeyedHasher;
import app.lightmove.api.core.crypto.service.UnconfiguredKeyedHasher;
import java.security.GeneralSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The one {@link KeyedHasher}. A keyset that does not parse fails the boot; a missing one leaves it unoffered. */
@Slf4j
@Configuration
public class KeyedHasherConfig {

    @Bean
    KeyedHasher keyedHasher(LightMoveProperties properties) throws GeneralSecurityException {
        CredentialEncryptionSettings settings = properties.crypto();
        if (settings == null || !settings.isSuppressionKeysetConfigured()) {
            log.warn("No suppression keyset is configured (lightmove.crypto.suppression-keyset), so no workspace "
                    + "can add to its suppression list.");
            return new UnconfiguredKeyedHasher();
        }
        return TinkKeyedHasher.fromKeyset(settings.suppressionKeyset());
    }
}
