package app.lightmove.api.strategy.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Apollo's flat industry labels grouped into the sectors a consultant thinks in. A group is never
 * stored — the filter records its industries — so re-tuning this file cannot widen a saved mandate.
 * {@code SectorTaxonomyCoverageIntegrationTest} asserts every live industry is covered.
 */
@Component
public class SectorTaxonomy {

    private static final String RESOURCE = "data/sector-taxonomy.json";

    private final Map<String, List<String>> industriesByGroup;

    public SectorTaxonomy(ObjectMapper json) {
        this.industriesByGroup = ClasspathVocabulary.read(json, RESOURCE);
        checkNoIndustryInTwoGroups(industriesByGroup);
    }

    /** Fails at startup on a label filed under two sectors, which would render one row twice. */
    private static void checkNoIndustryInTwoGroups(Map<String, List<String>> industriesByGroup) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> group : industriesByGroup.entrySet()) {
            for (String industry : group.getValue()) {
                String previous = seen.putIfAbsent(industry, group.getKey());
                if (previous != null) {
                    throw new IllegalStateException(
                            "%s lists '%s' under both '%s' and '%s'"
                                    .formatted(RESOURCE, industry, previous, group.getKey()));
                }
            }
        }
    }

    /** In file order. */
    public Map<String, List<String>> groups() {
        return industriesByGroup;
    }

}
