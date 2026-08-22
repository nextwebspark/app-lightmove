package app.lightmove.api.strategy.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The curated grouping of Apollo's 148 flat industry labels into the 20 sectors a consultant thinks
 * in. Editorial judgement, so a classpath resource rather than a migration, which is immutable.
 *
 * <p>A group is never stored: the panel expands one to its industries and the filter records those,
 * so re-tuning this file cannot widen a mandate saved months ago. Coverage over the live vocabulary
 * is asserted by {@code SectorTaxonomyCoverageIntegrationTest} — an industry no group claims falls
 * out of the sidebar silently, which is why it is a test.
 */
@Component
public class SectorTaxonomy {

    private static final String RESOURCE = "data/sector-taxonomy.json";

    private final Map<String, List<String>> industriesByGroup;

    public SectorTaxonomy(ObjectMapper json) {
        this.industriesByGroup = ClasspathVocabulary.read(json, RESOURCE);
        checkNoIndustryInTwoGroups(industriesByGroup);
    }

    /** Fails at startup on a label filed under two sectors. */
    private static void checkNoIndustryInTwoGroups(Map<String, List<String>> industriesByGroup) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> group : industriesByGroup.entrySet()) {
            for (String industry : group.getValue()) {
                String previous = seen.putIfAbsent(industry, group.getKey());
                if (previous != null) {
                    // One label under two sectors would render the same row twice, each disagreeing about
                    // whether it is selected.
                    throw new IllegalStateException(
                            "%s lists '%s' under both '%s' and '%s'"
                                    .formatted(RESOURCE, industry, previous, group.getKey()));
                }
            }
        }
    }

    /** Every group in file order, with the industries it covers. */
    public Map<String, List<String>> groups() {
        return industriesByGroup;
    }

}
