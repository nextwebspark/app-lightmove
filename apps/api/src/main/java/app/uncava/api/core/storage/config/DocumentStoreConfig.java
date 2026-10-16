package app.uncava.api.core.storage.config;

import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.core.config.StorageSettings;
import app.uncava.api.core.storage.service.DocumentStore;
import app.uncava.api.core.storage.service.FilesystemDocumentStore;
import app.uncava.api.core.storage.service.GcsDocumentStore;
import com.google.cloud.storage.StorageOptions;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The one {@link DocumentStore}, chosen by {@code uncava.storage.provider}. */
@Slf4j
@Configuration
public class DocumentStoreConfig {

    @Bean
    DocumentStore documentStore(UncavaProperties properties) {
        StorageSettings settings = properties.storage();
        return switch (settings.provider()) {
            case GCS -> new GcsDocumentStore(StorageOptions.getDefaultInstance().getService(), settings.bucket());
            case FILESYSTEM -> {
                log.info("Uploaded documents are kept on local disk under {}", settings.directory());
                yield new FilesystemDocumentStore(Path.of(settings.directory()));
            }
        };
    }
}
