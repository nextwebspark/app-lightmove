package app.lightmove.api.strategy.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a name-to-members vocabulary out of the classpath — the shape both
 * {@link SectorTaxonomy} and {@link MarketSegments} are stored in. File order is preserved because
 * it is the order the sidebar renders.
 */
final class ClasspathVocabulary {

    private ClasspathVocabulary() {
    }

    static Map<String, List<String>> read(ObjectMapper json, String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return json.readValue(in, new TypeReference<LinkedHashMap<String, List<String>>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + resource, e);
        }
    }
}
