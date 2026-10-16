package app.uncava.api.core.config;

import app.uncava.api.core.storage.constant.StorageProvider;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where uploaded files are kept — {@code uncava.storage.*}. {@code gcs} in every deployment: Cloud
 * Run's disk is the instance's memory and is gone with it, so {@code filesystem} is for a laptop and
 * the tests only.
 */
public record StorageSettings(
        @DefaultValue("filesystem") StorageProvider provider,

        /** The private bucket, under {@code gcs}. */
        @DefaultValue("") String bucket,

        /** The directory files are written under, under {@code filesystem}. */
        @DefaultValue(".data/documents") String directory
) {

    public StorageSettings {
        if (provider == StorageProvider.GCS && bucket.isBlank()) {
            throw new IllegalArgumentException("uncava.storage.bucket is required when the provider is gcs");
        }
    }
}
