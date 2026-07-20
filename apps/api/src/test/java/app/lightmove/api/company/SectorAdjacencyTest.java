package app.lightmove.api.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The sector-adjacency map is a closed world: every value must itself be a key, so every suggestion
 * is a real sector, and no sector may list itself. This guards the hand-authored JSON on the build —
 * an edit that introduces an unknown sector or a self-reference fails here rather than in production.
 */
class SectorAdjacencyTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Map<String, List<String>> loadMap() throws Exception {
        try (InputStream in = new ClassPathResource("data/sector-adjacency.json").getInputStream()) {
            return JSON.readValue(in, new TypeReference<Map<String, List<String>>>() {});
        }
    }

    @Test
    @DisplayName("every adjacent value is itself a key — a real sector")
    void everyValueIsAKey() throws Exception {
        Map<String, List<String>> map = loadMap();

        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            assertThat(map.keySet())
                    .as("adjacents of '%s' must all be known sectors", entry.getKey())
                    .containsAll(entry.getValue());
        }
    }

    @Test
    @DisplayName("no sector lists itself as its own adjacent")
    void noSelfReference() throws Exception {
        Map<String, List<String>> map = loadMap();

        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            assertThat(entry.getValue())
                    .as("'%s' must not be adjacent to itself", entry.getKey())
                    .doesNotContain(entry.getKey());
        }
    }

    @Test
    @DisplayName("the map is non-trivial and parses")
    void mapParses() throws Exception {
        assertThat(loadMap()).hasSizeGreaterThan(400);
    }
}
