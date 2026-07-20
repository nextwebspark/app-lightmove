package app.lightmove.api.company.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The curated sector-adjacency map: for each of the ~523 primary_industry values in the company
 * universe, the sectors a candidate from it could plausibly transfer from. Adjacency is editorial
 * judgement, not something the data can derive, so it lives as a classpath JSON resource authored and
 * reviewed by hand — the sibling of the Java-coded position template library, not a database table
 * (an applied migration is immutable, and every re-tuning would otherwise need a new one).
 *
 * <p>The file is a closed world: every value is itself a key, so every suggestion is a real sector.
 * {@code SectorAdjacencyTest} enforces that invariant on the build. A sector that appears upstream
 * after a future sync is simply absent here and yields no adjacents — a graceful gap, not a failure.
 */
@Component
public class SectorAdjacency {

    private static final String RESOURCE = "data/sector-adjacency.json";

    private final Map<String, List<String>> adjacentBySector;

    public SectorAdjacency(ObjectMapper json) {
        this.adjacentBySector = load(json);
    }

    private static Map<String, List<String>> load(ObjectMapper json) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            return json.readValue(in, new TypeReference<Map<String, List<String>>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + RESOURCE, e);
        }
    }

    /**
     * The adjacent sectors for a set of chosen sectors, in the map's ranked order, de-duplicated and
     * with the chosen sectors themselves removed (a direct sector is never also a suggestion). Capped
     * so the panel stays readable.
     */
    public List<String> suggestFor(List<String> sectors, int limit) {
        Set<String> chosen = Set.copyOf(sectors);
        Set<String> merged = new LinkedHashSet<>();
        for (String sector : sectors) {
            for (String adjacent : adjacentBySector.getOrDefault(sector, List.of())) {
                if (!chosen.contains(adjacent)) {
                    merged.add(adjacent);
                }
            }
        }
        return merged.stream().limit(limit).toList();
    }

    /** Exposed for the build-time invariant check; not part of the request path. */
    Map<String, List<String>> asMap() {
        return adjacentBySector;
    }
}
