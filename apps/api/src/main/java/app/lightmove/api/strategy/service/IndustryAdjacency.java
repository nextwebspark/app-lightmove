package app.lightmove.api.strategy.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** The "Adjacent Industries" chips: editorial judgement, kept as a classpath resource. */
@Component
public class IndustryAdjacency {

    private static final String RESOURCE = "data/industry-adjacency.json";

    private final Map<String, List<String>> neighboursByIndustry;

    public IndustryAdjacency(ObjectMapper json) {
        this.neighboursByIndustry = Map.copyOf(ClasspathVocabulary.read(json, RESOURCE));
        checkSymmetric(neighboursByIndustry);
    }

    /** Fails at startup on a one-way edge. */
    private static void checkSymmetric(Map<String, List<String>> neighbours) {
        neighbours.forEach((industry, listed) -> listed.forEach(neighbour -> {
            if (!neighbours.getOrDefault(neighbour, List.of()).contains(industry)) {
                throw new IllegalStateException(
                        "%s has '%s' beside '%s' but not the reverse".formatted(RESOURCE, neighbour, industry));
            }
        }));
    }

    /** Case-insensitive; empty for an industry the list does not hold. */
    public List<String> neighboursOf(String industry) {
        return industry == null ? List.of()
                : neighboursByIndustry.getOrDefault(industry.strip().toLowerCase(Locale.ROOT), List.of());
    }

    public Map<String, List<String>> neighbours() {
        return neighboursByIndustry;
    }
}
