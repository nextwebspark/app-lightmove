package app.lightmove.api.enrichment.sourcing.service;

import static app.lightmove.api.enrichment.sourcing.service.ExecutiveRerankerTest.person;
import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("the same question in another order or case is one key; another size, country or exclusion is not")
    void queryKeysAreCanonical() {
        String key = CachedPeopleSearch.queryKeyOf("DP-World",
                spec(List.of("Chief", "Head"), List.of("Finance"), List.of("Assistant")), List.of("SA", "AE"), 10);

        assertThat(CachedPeopleSearch.queryKeyOf("dp-world",
                spec(List.of("head", "chief"), List.of("FINANCE"), List.of("assistant")), List.of("AE", "SA"), 10))
                .isEqualTo(key);
        assertThat(CachedPeopleSearch.queryKeyOf("dp-world",
                spec(List.of("Chief", "Head"), List.of("Finance"), List.of("Assistant")), List.of("AE", "SA"), 5))
                .isNotEqualTo(key);
        assertThat(CachedPeopleSearch.queryKeyOf("dp-world",
                spec(List.of("Chief", "Head"), List.of("Finance"), List.of("Assistant")), List.of(), 10))
                .isNotEqualTo(key);
        assertThat(CachedPeopleSearch.queryKeyOf("dp-world",
                spec(List.of("Chief", "Head"), List.of("Finance"), List.of()), List.of("AE", "SA"), 10))
                .isNotEqualTo(key);
    }

    private static SourcingSpec spec(List<String> seniority, List<String> function, List<String> excluded) {
        return new SourcingSpec(seniority, function, excluded, null);
    }
}
