package app.lightmove.api.strategy.service;

import app.lightmove.api.common.service.ClasspathJsonLoader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return ClasspathJsonLoader.load(json, resource, new TypeReference<LinkedHashMap<String, List<String>>>() {});
    }
}
