package app.lightmove.api.strategy.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The curated grouping of Apollo's flat industry vocabulary into the sectors a consultant thinks in.
 *
 * <p>{@code app_lm_apollo_companies.industry} holds 148 distinct lower-cased labels and no hierarchy
 * — "computer networking" and "semiconductors" sit beside "judiciary" as peers. Rendering 148 chips
 * in a 340px sidebar is not a filter, so the labels are grouped into 15 sectors here: Technology &
 * Telecom, Financial Services, Healthcare & Life Sciences, and so on. Selecting a group selects all
 * of its industries.
 *
 * <p><b>The grouping is editorial judgement, so it lives as a classpath JSON resource</b> rather than
 * a database table — the same call the sector-adjacency map it replaces made, and for the same
 * reason: an applied migration is immutable, and every re-tuning of a judgement call would otherwise
 * need a new one. A few labels sit defensibly in two places ("automotive" under Transport rather than
 * Industrial, "e-learning" under Education rather than Technology); moving one is a one-line edit to
 * a reviewable file.
 *
 * <p><b>A group is never stored.</b> Selecting one expands to its industries and the filter records
 * those, so re-tuning this file cannot silently widen the scope of a mandate that was saved months
 * ago. Everything here is therefore read-side only.
 *
 * <p>The file is exhaustive over the live vocabulary and holds no label the universe lacks — both
 * halves are asserted by {@code SectorTaxonomyTest} and, against the real table, by
 * {@code SectorTaxonomyCoverageIntegrationTest}. An industry that appears upstream after a future
 * load and is missing here would fall out of the sidebar entirely, which is why that is a test and
 * not a comment.
 */
@Component
public class SectorTaxonomy {

    private static final String RESOURCE = "data/sector-taxonomy.json";

    private final Map<String, List<String>> industriesByGroup;
    private final Map<String, String> groupByIndustry;

    public SectorTaxonomy(ObjectMapper json) {
        this.industriesByGroup = load(json);
        this.groupByIndustry = invert(industriesByGroup);
    }

    private static Map<String, List<String>> load(ObjectMapper json) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            return json.readValue(in, new TypeReference<LinkedHashMap<String, List<String>>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + RESOURCE, e);
        }
    }

    private static Map<String, String> invert(Map<String, List<String>> industriesByGroup) {
        Map<String, String> inverted = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> group : industriesByGroup.entrySet()) {
            for (String industry : group.getValue()) {
                String previous = inverted.putIfAbsent(industry, group.getKey());
                if (previous != null) {
                    // Fail at startup rather than serve a sidebar where one chip appears twice and
                    // its two group headers disagree about whether it is selected.
                    throw new IllegalStateException(
                            "%s lists '%s' under both '%s' and '%s'"
                                    .formatted(RESOURCE, industry, previous, group.getKey()));
                }
            }
        }
        return inverted;
    }

    /** Every group in file order, with the industries it covers. */
    public Map<String, List<String>> groups() {
        return industriesByGroup;
    }

    /**
     * The industries covered by the named groups, de-duplicated, in file order. An unknown group name
     * contributes nothing rather than throwing: a client holding a stale taxonomy should lose a chip,
     * not lose the whole request.
     */
    public List<String> industriesOf(Collection<String> groupNames) {
        Set<String> industries = new LinkedHashSet<>();
        for (String groupName : groupNames) {
            industries.addAll(industriesByGroup.getOrDefault(groupName, List.of()));
        }
        return List.copyOf(industries);
    }

    /** Which group an industry belongs to, or {@code null} if the taxonomy does not cover it. */
    public String groupOf(String industry) {
        return industry == null ? null : groupByIndustry.get(industry);
    }

    /** Every industry the taxonomy covers. Used by the invariant checks. */
    public Set<String> coveredIndustries() {
        return groupByIndustry.keySet();
    }

    /**
     * Industries present in the universe that no group claims — the check that keeps a newly-loaded
     * Apollo label from disappearing out of the sidebar unnoticed.
     */
    public List<String> uncovered(Collection<String> liveIndustries) {
        List<String> missing = new ArrayList<>();
        for (String industry : liveIndustries) {
            if (industry != null && !groupByIndustry.containsKey(industry)) {
                missing.add(industry);
            }
        }
        return missing;
    }
}
