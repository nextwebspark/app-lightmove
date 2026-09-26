package app.lightmove.api.strategy.service;

import app.lightmove.api.common.service.ClasspathJsonLoader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Reads a name-to-members vocabulary from the classpath, in file order — the sidebar's order. */
final class ClasspathVocabulary {

    private ClasspathVocabulary() {
    }

    static Map<String, List<String>> read(ObjectMapper json, String resource) {
        return ClasspathJsonLoader.load(json, resource, new TypeReference<LinkedHashMap<String, List<String>>>() {});
    }
}
