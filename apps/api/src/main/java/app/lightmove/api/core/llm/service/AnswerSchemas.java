package app.lightmove.api.core.llm.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;

/** Reads the JSON Schema a prompt holds its answer to. */
final class AnswerSchemas {

    private AnswerSchemas() {
    }

    /**
     * Reads a schema resource whole.
     *
     * <p>Called while a caller is being constructed, so a schema that will not load fails the context
     * at startup rather than every request that needed it.
     */
    static String readFrom(Resource schema) {
        try {
            return schema.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the answer schema " + schema, exception);
        }
    }
}
