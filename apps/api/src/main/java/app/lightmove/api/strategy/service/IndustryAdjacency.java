package app.lightmove.api.strategy.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Which industries sit beside which — the panel's "Adjacent Industries" chips. Editorial judgement
 * about transferable talent, so a classpath resource like the taxonomy beside it.
 */
@Component
public class IndustryAdjacency {

    private static final String RESOURCE = "data/industry-adjacency.json";

    private final Map<String, List<String>> neighboursByIndustry;

    public IndustryAdjacency(ObjectMapper json) {
        this.neighboursByIndustry = Map.copyOf(ClasspathVocabulary.read(json, RESOURCE));
        checkSymmetric(neighboursByIndustry);
    }

    /**
     * Fails at startup on a one-way edge. It would put the chip under one industry and not the
     * other, which reads as the suggestions having an opinion they do not have.
     */
    private static void checkSymmetric(Map<String, List<String>> neighbours) {
        neighbours.forEach((industry, listed) -> listed.forEach(neighbour -> {
            if (!neighbours.getOrDefault(neighbour, List.of()).contains(industry)) {
                throw new IllegalStateException(
                        "%s has '%s' beside '%s' but not the reverse".formatted(RESOURCE, neighbour, industry));
            }
        }));
    }

    /** Every industry, with the industries beside it. */
    public Map<String, List<String>> neighbours() {
        return neighboursByIndustry;
    }
}
