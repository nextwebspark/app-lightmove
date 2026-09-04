package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.enrichment.candidate.service.HarvestApiProfileEnricher.HarvestApiEnvelope;
import app.lightmove.api.enrichment.candidate.service.HarvestApiProfileEnricher.HarvestApiProfile;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The provider payload → {@link EnrichedProfile} translation, against a recorded response shaped by
 * HarvestAPI's published schema. The fixture deliberately carries fields this feature never reads
 * (ids, follower counts, endorsements, the headline), because surviving them is part of the contract.
 */
class HarvestApiProfileEnricherTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("a full payload maps into the profile a researcher would have typed")
    void aFullPayloadMaps() {
        EnrichedProfile enriched = HarvestApiProfileEnricher.toEnrichedProfile(fixture().element());

        // The current position, never the profile headline — a headline is self-marketing prose.
        assertThat(enriched.title()).isEqualTo("Group CFO");
        assertThat(enriched.about()).isEqualTo("Finance leader across GCC retail and FMCG.");
        assertThat(enriched.employerName()).isEqualTo("Al Rawabi Dairy");
        assertThat(enriched.employerLinkedinUrl()).isEqualTo("https://www.linkedin.com/company/example");
        assertThat(enriched.employerLogoUrl()).isEqualTo("https://media.example.com/alrawabi.png");
        assertThat(enriched.locationCity()).isEqualTo("Dubai");
        assertThat(enriched.locationCountry()).isEqualTo("United Arab Emirates");

        assertThat(enriched.career()).hasSize(3);
        // A range from the dates; the provider's `duration` is a tenure length, kept only as the
        // fallback when no dates arrived at all.
        assertThat(enriched.career().get(0).period()).isEqualTo("Jan 2021 – Present");
        assertThat(enriched.career().get(1).company()).isEqualTo("Regional Foods Co.");
        assertThat(enriched.career().get(1).period()).isEqualTo("2017 – 2021");
        assertThat(enriched.career().get(2).period()).isEqualTo("3 yrs");

        assertThat(enriched.education()).hasSize(1);
        assertThat(enriched.education().getFirst().school()).isEqualTo("American University in Cairo");
        assertThat(enriched.education().getFirst().degree()).isEqualTo("MBA, Finance");
        assertThat(enriched.education().getFirst().period()).isEqualTo("2010 - 2012");

        assertThat(enriched.skills()).containsExactly("Financial Planning", "M&A");
        assertThat(enriched.languages()).containsExactly("English", "Arabic");
    }

    @Test
    @DisplayName("a profile the provider found nothing on maps to an empty answer, not a crash")
    void aBareProfileMapsToEmpty() {
        HarvestApiProfile bare =
                new HarvestApiProfile(null, null, null, null, null, null, null, null, null);

        EnrichedProfile enriched = HarvestApiProfileEnricher.toEnrichedProfile(bare);

        assertThat(enriched.title()).isNull();
        assertThat(enriched.employerName()).isNull();
        assertThat(enriched.career()).isEmpty();
        assertThat(enriched.education()).isEmpty();
        assertThat(enriched.skills()).isEmpty();
        assertThat(enriched.languages()).isEmpty();
    }

    private static HarvestApiEnvelope fixture() {
        InputStream recorded =
                HarvestApiProfileEnricherTest.class.getResourceAsStream("/harvestapi/linkedin-profile.json");
        return JSON.readValue(recorded, HarvestApiEnvelope.class);
    }
}
