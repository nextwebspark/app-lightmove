package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * A person's documents — {@code lightmove.person-documents.*}. The multipart ceiling in
 * {@code spring.servlet.multipart} must stay above {@code maxFileSizeBytes}, or the container refuses
 * the body before this rule can say why.
 */
public record PersonDocumentSettings(
        @DefaultValue("20971520") long maxFileSizeBytes,
        @DefaultValue("50") int maxDocumentsPerPerson,
        @DefaultValue("20") int maxVersionsPerDocument
) {

    public PersonDocumentSettings {
        if (maxFileSizeBytes < 1 || maxDocumentsPerPerson < 1 || maxVersionsPerDocument < 1) {
            throw new IllegalArgumentException("lightmove.person-documents limits must all be positive");
        }
    }
}
