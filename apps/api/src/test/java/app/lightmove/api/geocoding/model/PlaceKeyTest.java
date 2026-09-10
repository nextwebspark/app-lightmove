package app.lightmove.api.geocoding.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceKeyTest {

    @Test
    @DisplayName("spelling, casing and stray whitespace collapse into one key")
    void normalisesIntoOneKey() {
        PlaceKey typed = PlaceKey.of("  Dubai ", "United  Arab Emirates").orElseThrow();
        PlaceKey exported = PlaceKey.of("dubai", "united arab emirates").orElseThrow();

        assertThat(typed).isEqualTo(exported);
        assertThat(typed.key()).isEqualTo("dubai|united arab emirates");
    }

    @Test
    @DisplayName("a country alone is a place; nothing at all is not")
    void halvesMayBeAbsentButNotBoth() {
        PlaceKey countryOnly = PlaceKey.of(null, "Oman").orElseThrow();
        assertThat(countryOnly.hasCity()).isFalse();
        assertThat(countryOnly.hasCountry()).isTrue();
        assertThat(countryOnly.key()).isEqualTo("|oman");

        assertThat(PlaceKey.of("", "  ")).isEmpty();
        assertThat(PlaceKey.of(null, null)).isEmpty();
    }
}
