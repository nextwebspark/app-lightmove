package app.lightmove.api.common.industry;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.common.industry.model.ResolvedIndustry;
import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.strategy.service.SectorTaxonomy;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The two lookup tables are a projection of {@code data/industry-map.json}, written once by a
 * migration that can never be edited again. Nothing at runtime reads them — {@link Industries} is
 * static and reads the file — so if the two drift apart, every SQL consumer quietly answers with a
 * vocabulary the application no longer uses. This is what notices.
 */
@IntegrationTest
class IndustryVocabularyIntegrationTest {

    @Autowired JdbcTemplate db;
    @Autowired SectorTaxonomy taxonomy;

    @Test
    @DisplayName("app_lm_industry holds exactly the labels the map resolves to")
    void industryTableMatchesTheMap() {
        Set<String> inFile = new HashSet<>(mapValues("apollo"));
        Set<String> inTable = new HashSet<>(
                db.queryForList("SELECT v1_label FROM app_lm_industry", String.class));

        assertThat(inTable).isEqualTo(inFile);
    }

    @Test
    @DisplayName("every row's V2 name and sector are the ones the resolver answers with")
    void industryTableMatchesTheResolver() {
        // The table is the map's projection, and the map is what the application writes rows from.
        // Drift here would have SQL grouping companies one way and the grid filing them another.
        db.queryForList("SELECT v1_label, v2_label, sector_group FROM app_lm_industry")
                .forEach(row -> assertThat(Industries.resolve((String) row.get("v1_label")))
                        .as("resolved '%s'", row.get("v1_label"))
                        .extracting(ResolvedIndustry::v2Label, ResolvedIndustry::sectorGroup)
                        .containsExactly(row.get("v2_label"), row.get("sector_group")));
    }

    @Test
    @DisplayName("every row carries a sector a consultant can actually tick")
    void everySectorGroupIsOfferable() {
        Set<String> groups = taxonomy.groups().keySet();

        db.queryForList("SELECT v1_label, sector_group FROM app_lm_industry").forEach(row ->
                assertThat(groups).as("sector for '%s'", row.get("v1_label"))
                        .contains((String) row.get("sector_group")));
    }

    @Test
    @DisplayName("app_lm_industry_v2 resolves every V2 industry the way the application would")
    void v2TableAgreesWithTheResolver() {
        List<Map<String, Object>> rows =
                db.queryForList("SELECT v2_code, v2_label, v1_label FROM app_lm_industry_v2");

        // The table exists so SQL can answer without Java. If it answered differently from Java, a
        // query and a capture of the same company would file it under two sectors — which is the
        // whole failure this vocabulary was introduced to end.
        assertThat(rows).hasSize(434).allSatisfy(row ->
                assertThat(Industries.nameOf((String) row.get("v2_label")))
                        .as("V2 %s '%s'", row.get("v2_code"), row.get("v2_label"))
                        .isEqualTo(row.get("v1_label")));
    }

    @Test
    @DisplayName("a V2 branch expands to the universe labels it covers")
    void aBranchExpandsToItsLabels() {
        List<String> expanded = db.queryForList("""
                SELECT DISTINCT v1_label
                FROM app_lm_industry_v2
                WHERE v2_hierarchy LIKE 'Technology, Information and Media%'
                ORDER BY 1
                """, String.class);

        // This is the shape a V2 search runs: resolve the selection to labels, then the existing
        // indexed `lower(industry) IN (…)` against the universe.
        assertThat(expanded).contains("information technology & services", "computer software",
                "internet", "telecommunications", "broadcast media", "online media");
    }

    @Test
    @DisplayName("a V2 leaf resolves to one label, and the universe may barely use it")
    void aLeafExpandsToOneLabel() {
        assertThat(expansionOf(4)).containsExactly("computer software");
        assertThat(expansionOf(3134)).containsExactly("internet");   // Blockchain Services, new in V2
    }

    private List<String> expansionOf(int v2Code) {
        return db.queryForList("SELECT v1_label FROM app_lm_industry_v2 WHERE v2_code = ?",
                String.class, v2Code);
    }

    private List<String> mapValues(String field) {
        try (InputStream in = new ClassPathResource("data/industry-map.json").getInputStream()) {
            Map<String, Map<String, Object>> file = new ObjectMapper()
                    .readValue(in, new TypeReference<LinkedHashMap<String, Map<String, Object>>>() {});
            return file.values().stream().map(entry -> (String) entry.get(field)).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not load data/industry-map.json", e);
        }
    }
}
