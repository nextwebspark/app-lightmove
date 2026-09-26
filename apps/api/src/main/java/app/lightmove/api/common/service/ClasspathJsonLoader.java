package app.lightmove.api.common.service;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Reads a JSON reference file bundled on the classpath; a missing or malformed one fails startup. */
public final class ClasspathJsonLoader {

    private ClasspathJsonLoader() {}

    public static <T> T load(ObjectMapper json, String resource, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return json.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + resource, e);
        }
    }
}
