package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.enrichment.candidate.service.BrightDataProfileEnricher.BrightDataExperience;
import app.lightmove.api.enrichment.candidate.service.BrightDataProfileEnricher.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataProfileEnricher.BrightDataSearchResult;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The dataset record → {@link EnrichedProfile} translation, against a fixture shaped on real search
 * hits (anonymised): a flat experience entry, a company-grouped entry with nested positions, a
 * fully-masked entry, mixed-shape languages, and snake_case keys throughout.
 */
class BrightDataProfileEnricherTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("a dataset hit maps into the profile a researcher would have typed")
    void aDatasetHitMaps() {
        EnrichedProfile enriched = BrightDataProfileEnricher.toEnrichedProfile(fixturePerson());

        // The current position, never the "position" field's "Title - Company" mashup.
        assertThat(enriched.title()).isEqualTo("Chief Executive Officer");
        assertThat(enriched.about()).isEqualTo("Retail leader across the GCC.");
        assertThat(enriched.employerName()).isEqualTo("RetailCo");
        assertThat(enriched.employerLinkedinUrl()).isEqualTo("https://www.linkedin.com/company/retailco/");
        assertThat(enriched.employerLogoUrl()).isEqualTo("https://media.example.com/retailco.png");
        assertThat(enriched.locationCity()).isEqualTo("Dubai");
        assertThat(enriched.locationCountry()).isEqualTo("United Arab Emirates");

        // Flat entry + two nested positions; the fully-masked entry maps away entirely.
        assertThat(enriched.career()).hasSize(3);
        assertThat(enriched.career().get(0).title()).isEqualTo("Chief Executive Officer");
        assertThat(enriched.career().get(0).period()).isEqualTo("Feb 2025 – Present");
        assertThat(enriched.career().get(1).company()).isEqualTo("Amazing Corp");
        assertThat(enriched.career().get(1).title()).isEqualTo("General Manager");
        assertThat(enriched.career().get(1).period()).isEqualTo("Feb 2023 – Jan 2025");
        assertThat(enriched.career().get(2).title()).isEqualTo("Senior Manager");

        assertThat(enriched.education()).hasSize(2);
        assertThat(enriched.education().get(0).school()).isEqualTo("Sample Institute of Management");
        assertThat(enriched.education().get(0).degree()).isEqualTo("MBA, Finance");
        assertThat(enriched.education().get(0).period()).isEqualTo("2005 – 2007");
        assertThat(enriched.education().get(1).degree()).isNull();

        // Languages arrive as an object and a bare string in the same list; skills as null.
        assertThat(enriched.languages()).containsExactly("English", "Arabic");
        assertThat(enriched.skills()).isEmpty();
    }

    @Test
    @DisplayName("a masked partial record degrades to absent fields, never to star-soup")
    void aMaskedRecordDegradesToThin() {
        BrightDataPerson masked = new BrightDataPerson(null, null, null, null, null,
                "Known Employer", null, null, null,
                List.of(new BrightDataExperience("******* ***", "******* ***", "******",
                        null, null, null, null, null)),
                null, null, null);

        EnrichedProfile enriched = BrightDataProfileEnricher.toEnrichedProfile(masked);

        assertThat(enriched.employerName()).isEqualTo("Known Employer");
        assertThat(enriched.title()).isNull();
        // Thin: the whole career masked away — exactly what FallbackProfileEnricher treats as a miss.
        assertThat(enriched.career()).isEmpty();
    }

    private static BrightDataPerson fixturePerson() {
        InputStream recorded = BrightDataProfileEnricherTest.class
                .getResourceAsStream("/brightdata/linkedin-profile.json");
        return JSON.readValue(recorded, BrightDataSearchResult.class).hits().getFirst();
    }
}
