package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.strategy.service.SectorTaxonomy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * The sector taxonomy is what makes 148 flat industry labels into a sidebar. It is hand-authored, so
 * the invariants an edit could break are pinned here rather than discovered in the UI.
 *
 *
 * <p>The one invariant this file cannot check is coverage — whether every industry the universe
 * actually carries lands in a group. That needs the table, so it lives in
 * {@code SectorTaxonomyCoverageIntegrationTest}.
 */
class SectorTaxonomyTest {

    private final SectorTaxonomy taxonomy = new SectorTaxonomy(new ObjectMapper());

    /** Every industry in the file, in group order. */
    private List<String> allIndustries() {
        List<String> all = new ArrayList<>();
        taxonomy.groups().values().forEach(all::addAll);
        return all;
    }

    @Test
    @DisplayName("no industry appears in two groups")
    void noIndustryInTwoGroups() {
        // A duplicate would render the same row under two sectors, each disagreeing about whether it
        // is selected. The component throws at startup on this; the test says so on the build.
        assertThat(allIndustries()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("every group carries at least one industry")
    void noEmptyGroups() {
        for (Map.Entry<String, List<String>> group : taxonomy.groups().entrySet()) {
            assertThat(group.getValue())
                    .as("group '%s' must not be empty", group.getKey())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("industries are lower-case, matching the universe's own vocabulary")
    void industriesAreLowerCase() {
        for (String industry : allIndustries()) {
            // The universe stores them lower-cased. A Title Case entry here would count zero and
            // filter nothing, which reads as an empty market rather than as a typo.
            assertThat(industry).isEqualTo(industry.toLowerCase(java.util.Locale.ROOT));
        }
    }

    @Test
    @DisplayName("the taxonomy covers the whole vocabulary the universe was authored against")
    void coversTheAuthoredVocabulary() {
        // 148 is the distinct industry count of app_lm_apollo_companies at the time this file was
        // written. A drop here means an edit lost labels; a rise means new ones arrived and the
        // coverage integration test is the one that will say which.
        assertThat(allIndustries()).hasSize(148);
        assertThat(taxonomy.groups()).hasSize(20);
    }



}
