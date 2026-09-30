package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The two local halves of the cache: whether a person on file fits a search, and when two searches are one. */
class CachedPeopleSearchTest {

    @Test
    @DisplayName("a person on file fits when the title holds a seniority and a function word and no excluded one, in a searched country")
    void aPersonOnFileFits() {
        var cfo = person("cfo", "A CFO", "Chief Financial Officer at DP World Jeddah", "SA");
        var assistant = person("pa", "A PA", "Personal Assistant To Chief Financial Officer", "SA");

        assertThat(CachedPeopleSearch.fits(cfo, spec(List.of("chief"), List.of("Finance", "financial"),
                List.of("Assistant")), List.of("AE", "SA"))).isTrue();
        assertThat(CachedPeopleSearch.fits(assistant, spec(List.of("chief"), List.of("Finance", "financial"),
                List.of("Assistant")), List.of("AE", "SA"))).isFalse();
        assertThat(CachedPeopleSearch.fits(cfo, spec(List.of("Chief"), List.of("Marketing"), List.of()), List.of()))
                .isFalse();
        assertThat(CachedPeopleSearch.fits(cfo, spec(List.of("Chief"), List.of("Finance"), List.of()), List.of("AE")))
                .isFalse();
        assertThat(CachedPeopleSearch.fits(person("x", "No Title", null, "AE"),
                spec(List.of("Chief"), List.of("Finance"), List.of()), List.of())).isFalse();
    }

    @Test
    @DisplayName("the same question in another order or case is one key; another size, country, exclusion, provider or seat is not")
    void queryKeysAreCanonical() {
        String key = CachedPeopleSearch.queryKeyOf("brightdata", "DP-World",
                spec(List.of("Chief", "Head"), List.of("Finance"), List.of("Assistant")), null, List.of("SA", "AE"), 10);

        assertThat(CachedPeopleSearch.queryKeyOf("brightdata", "dp-world",
                spec(List.of("head", "chief"), List.of("FINANCE"), List.of("assistant")), null, List.of("AE", "SA"), 10))
                .isEqualTo(key);
        assertThat(CachedPeopleSearch.queryKeyOf("brightdata", "dp-world",
                spec(List.of("Chief", "Head"), List.of("Finance"), List.of("Assistant")), null, List.of("AE", "SA"), 5))
                .isNotEqualTo(key);
        assertThat(CachedPeopleSearch.queryKeyOf("brightdata", "dp-world",
                spec(List.of("Chief", "Head"), List.of("Finance"), List.of("Assistant")), null, List.of(), 10))
                .isNotEqualTo(key);
        assertThat(CachedPeopleSearch.queryKeyOf("brightdata", "dp-world",
                spec(List.of("Chief", "Head"), List.of("Finance"), List.of()), null, List.of("AE", "SA"), 10))
                .isNotEqualTo(key);
        assertThat(CachedPeopleSearch.queryKeyOf("contactout", "dp-world",
                spec(List.of("Chief", "Head"), List.of("Finance"), List.of("Assistant")), null, List.of("AE", "SA"), 10))
                .isNotEqualTo(key);
        assertThat(CachedPeopleSearch.queryKeyOf("brightdata", "dp-world",
                spec(List.of("Chief", "Head"), List.of("Finance"), List.of("Assistant")), Seniority.N_MINUS_1,
                List.of("AE", "SA"), 10)).isNotEqualTo(key);
    }

    private static SourcingSpec spec(List<String> seniority, List<String> function, List<String> excluded) {
        return new SourcingSpec(seniority, function, excluded, null);
    }

    private static BrightDataPerson person(String slug, String name, String position, String countryCode) {
        return new BrightDataPerson(slug, slug, name, "https://www.linkedin.com/in/" + slug, "About " + name,
                position, "Dubai", "Dubai, United Arab Emirates", countryCode, "DP World",
                new BrightDataPerson.BrightDataCurrentCompany("DP World", "dp-world", null), null, true,
                List.of(new BrightDataExperience("DP World", position, null, null, null, "2020", null, null, null)),
                List.of(), List.of(), List.of());
    }
}
