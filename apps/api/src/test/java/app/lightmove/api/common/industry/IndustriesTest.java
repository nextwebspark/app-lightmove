package app.lightmove.api.common.industry;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class IndustriesTest {

    @Test
    @DisplayName("a vendor's V2 label resolves to the label the universe publishes")
    void v2LabelsResolve() {
        // What Bright Data actually returned on the rows that prompted this: LinkedIn renamed these
        // industries and kept their ids, so the universe's own label is on the other side of a rename.
        assertThat(Industries.nameOf("IT Services and IT Consulting"))
                .isEqualTo("information technology & services");
        assertThat(Industries.nameOf("Software Development")).isEqualTo("computer software");
        assertThat(Industries.nameOf("Business Consulting and Services")).isEqualTo("management consulting");
        assertThat(Industries.nameOf("Hospitals and Health Care")).isEqualTo("hospital & health care");
        assertThat(Industries.nameOf("Advertising Services")).isEqualTo("marketing & advertising");
        assertThat(Industries.nameOf("Oil and Gas")).isEqualTo("oil & energy");
        assertThat(Industries.nameOf("Motor Vehicle Manufacturing")).isEqualTo("automotive");
        assertThat(Industries.nameOf("Travel Arrangements")).isEqualTo("leisure, travel & tourism");
        assertThat(Industries.nameOf("Tobacco Manufacturing")).isEqualTo("tobacco");
        assertThat(Industries.nameOf("Staffing and Recruiting")).isEqualTo("staffing & recruiting");
    }

    @Test
    @DisplayName("case, ampersands and punctuation are one spelling")
    void spellingsFold() {
        // Three rows of real drift: the same industry counted twice by the report and rendered
        // identically by the heatmap, which capitalises its axis.
        assertThat(Industries.nameOf("Financial Services")).isEqualTo("financial services");
        assertThat(Industries.nameOf("Information Technology & Services"))
                .isEqualTo("information technology & services");
        assertThat(Industries.nameOf("International Trade and Development"))
                .isEqualTo("international trade & development");
        assertThat(Industries.nameOf("  OIL  &  ENERGY ")).isEqualTo("oil & energy");
        // V1 hyphenates what the universe writes as one word.
        assertThat(Industries.nameOf("Non-Profit Organization Management"))
                .isEqualTo("nonprofit organization management");
    }

    @Test
    @DisplayName("a V2 leaf lands on the nearest industry V1 also had")
    void leavesLandOnTheirAncestor() {
        assertThat(Industries.nameOf("Mobile Computing Software Products")).isEqualTo("computer software");
        assertThat(Industries.nameOf("Retail Pharmacies")).isEqualTo("retail");
        assertThat(Industries.nameOf("Insurance Carriers")).isEqualTo("insurance");
        // V2 gave id 25 to the Manufacturing root where V1 had it as Consumer Goods, so a leaf under
        // it lands on "consumer goods" unless the map says otherwise. These are the ones that do.
        assertThat(Industries.nameOf("Plastics and Rubber Product Manufacturing")).isEqualTo("plastics");
        assertThat(Industries.nameOf("Wood Product Manufacturing")).isEqualTo("paper & forest products");
        assertThat(Industries.nameOf("Motor Vehicle Parts Manufacturing")).isEqualTo("automotive");
        assertThat(Industries.nameOf("Apparel Manufacturing")).isEqualTo("apparel & fashion");
    }

    @Test
    @DisplayName("an industry the map does not know is kept, not dropped")
    void unknownIsKept() {
        assertThat(Industries.nameOf("  competitive underwater basket weaving  "))
                .isEqualTo("competitive underwater basket weaving");
        assertThat(Industries.isKnown("competitive underwater basket weaving")).isFalse();
        assertThat(Industries.nameOf(null)).isNull();
        assertThat(Industries.nameOf("   ")).isNull();
    }

    @Test
    @DisplayName("the universe's own labels pass through unchanged")
    void canonicalLabelsAreStable() {
        universeLabels().forEach(label -> {
            assertThat(Industries.nameOf(label)).isEqualTo(label);
            assertThat(Industries.isKnown(label)).isTrue();
        });
    }

    @Test
    @DisplayName("every label the map resolves to is one a sector claims")
    void everyTargetIsOfferableBySector() {
        // A target no group claims would file a company under a sector the filter cannot offer —
        // invisible, because SectorTaxonomyCoverageIntegrationTest only checks the other direction.
        Set<String> grouped = new HashSet<>();
        new SectorTaxonomy(new ObjectMapper()).groups().values().forEach(grouped::addAll);

        assertThat(universeLabels()).isNotEmpty().allSatisfy(label ->
                assertThat(grouped).as("sector for '%s'", label).contains(label));
    }

    /** The label every entry of the map resolves to — read from the file, not from the resolver. */
    private static List<String> universeLabels() {
        try (InputStream in = new ClassPathResource("data/industry-map.json").getInputStream()) {
            Map<String, Map<String, Object>> file = new ObjectMapper()
                    .readValue(in, new TypeReference<LinkedHashMap<String, Map<String, Object>>>() {});
            return file.values().stream().map(entry -> (String) entry.get("apollo")).distinct().toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not load data/industry-map.json", e);
        }
    }
}
