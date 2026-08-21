package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.strategy.service.SectorTaxonomy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The one taxonomy invariant a unit test cannot check: whether every industry the universe carries
 * lands in a group.
 *
 * <p>An ungrouped industry does not fail loudly — it simply drops out of the sidebar, and the
 * companies carrying it become unreachable by sector while still being counted in the total. That is
 * the kind of gap nobody notices, which is why it is a test.
 *
 * <p>Against a Testcontainers database the table is empty unless a test fills it, so this seeds the
 * check itself: it asserts the mechanism, and the same assertion run against the real universe is
 * what would catch a newly-loaded label.
 */
@IntegrationTest
class SectorTaxonomyCoverageIntegrationTest {

    @Autowired JdbcTemplate db;
    @Autowired SectorTaxonomy taxonomy;

    @BeforeEach
    void freshUniverse() {
        db.execute("DELETE FROM app_lm_apollo_companies");
    }

    @Test
    @DisplayName("every industry present in the universe belongs to a group")
    void everyLiveIndustryIsGrouped() {
        // A representative slice of the real vocabulary, one per group, so the assertion exercises
        // the whole map rather than a single lookup.
        List<String> sample = List.of("information technology & services", "financial services",
                "hospital & health care", "oil & energy", "construction", "retail", "food & beverages",
                "machinery", "logistics & supply chain", "management consulting", "hospitality",
                "media production", "education management", "government administration",
                "nonprofit organization management");
        sample.forEach(this::seed);

        assertThat(ungrouped()).isEmpty();
    }

    @Test
    @DisplayName("an industry the taxonomy does not know is reported rather than silently dropped")
    void anUngroupedIndustryIsReported() {
        seed("oil & energy");
        seed("competitive underwater basket weaving");

        // This is what a newly-loaded Apollo label would look like, and it must be visible.
        assertThat(ungrouped()).containsExactly("competitive underwater basket weaving");
    }

    /**
     * Industries the universe carries that no sector claims.
     *
     * <p>Computed here rather than behind a service method: nothing in the application asks this
     * question, and a production method existing only to be tested is a method to maintain for
     * nobody's benefit.
     */
    private List<String> ungrouped() {
        Set<String> grouped = new HashSet<>();
        taxonomy.groups().values().forEach(grouped::addAll);
        return db.queryForList("""
                        SELECT DISTINCT industry
                        FROM app_lm_apollo_companies
                        WHERE industry IS NOT NULL AND industry <> ''
                        """, String.class).stream()
                .filter(industry -> !grouped.contains(industry))
                .toList();
    }

    private void seed(String industry) {
        db.update("""
                INSERT INTO app_lm_apollo_companies
                    (apollo_account_id, company_name, industry, num_employees, row_hash)
                VALUES (?, ?, ?, 10, ?)
                """, industry, "Company for " + industry, industry, industry);
    }
}
