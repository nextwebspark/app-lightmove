package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.strategy.service.IndustryAdjacency;
import app.lightmove.api.strategy.service.SectorTaxonomy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The adjacency file against the taxonomy beside it. Symmetry is checked at startup, so what is left
 * here is coverage: a chip naming an industry the sidebar cannot offer selects nothing when clicked.
 */
class IndustryAdjacencyTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, List<String>> neighbours = new IndustryAdjacency(json).neighbours();
    private final Set<String> industries = allIndustries();

    @Test
    @DisplayName("names only industries the taxonomy also holds, and never itself")
    void namesOnlyRealIndustries() {
        neighbours.forEach((industry, listed) -> {
            assertThat(industries).as(industry).contains(industry);
            assertThat(listed).as(industry).doesNotContain(industry).doesNotHaveDuplicates();
            assertThat(industries).as(industry).containsAll(listed);
        });
    }

    @Test
    @DisplayName("every industry suggests something")
    void everyIndustrySuggestsSomething() {
        // A leaf with no neighbours is a dead end: picking it offers nothing and the panel looks
        // broken rather than exhausted.
        industries.forEach(industry ->
                assertThat(neighbours.get(industry)).as(industry).isNotNull().isNotEmpty());
    }

    private static Set<String> allIndustries() {
        Set<String> all = new HashSet<>();
        new SectorTaxonomy(JsonMapper.builder().build()).groups().values().forEach(all::addAll);
        return all;
    }
}
