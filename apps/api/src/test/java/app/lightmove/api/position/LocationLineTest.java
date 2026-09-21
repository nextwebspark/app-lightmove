package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.position.model.LocationLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The one line a document writes, read into the two halves the brief stores. */
class LocationLineTest {

    @Nested
    @DisplayName("a line with a comma")
    class WithComma {

        @Test
        @DisplayName("splits on the tail when the catalog knows that country")
        void splitsOnAKnownTail() {
            LocationLine line = LocationLine.of("Abu Dhabi, United Arab Emirates");
            assertThat(line.city()).isEqualTo("Abu Dhabi");
            assertThat(line.country()).isEqualTo("United Arab Emirates");
        }

        @Test
        @DisplayName("spells the country as the catalog does, whatever the document called it")
        void canonicalisesTheCountry() {
            assertThat(LocationLine.of("London, UK").country()).isEqualTo("United Kingdom");
            assertThat(LocationLine.of("London, UK").city()).isEqualTo("London");
        }

        @Test
        @DisplayName("keeps the whole line as the city when the tail names no country")
        void keepsAnUnplaceableTailWhole() {
            // "IL" is Israel's ISO code and Illinois' abbreviation; resolveSpelling refuses the bare
            // code for exactly this reason, so the line stays one city for a person to finish.
            LocationLine line = LocationLine.of("Chicago, IL");
            assertThat(line.city()).isEqualTo("Chicago, IL");
            assertThat(line.country()).isNull();
        }

        @Test
        @DisplayName("splits on the last comma, so a region between city and country stays with the city")
        void splitsOnTheLastComma() {
            LocationLine line = LocationLine.of("Al Khobar, Eastern Province, Saudi Arabia");
            assertThat(line.city()).isEqualTo("Al Khobar, Eastern Province");
            assertThat(line.country()).isEqualTo("Saudi Arabia");
        }
    }

    @Nested
    @DisplayName("a line with no comma")
    class WithoutComma {

        @Test
        @DisplayName("is the country when the catalog places it, with no city invented")
        void readsACountryAlone() {
            LocationLine line = LocationLine.of("Saudi Arabia");
            assertThat(line.city()).isNull();
            assertThat(line.country()).isEqualTo("Saudi Arabia");
        }

        @Test
        @DisplayName("is the city when the catalog does not, with no country guessed")
        void readsACityAlone() {
            LocationLine line = LocationLine.of("Dubai");
            assertThat(line.city()).isEqualTo("Dubai");
            assertThat(line.country()).isNull();
        }

        @Test
        @DisplayName("takes the catalog's own casing for a city it carries")
        void canonicalisesTheCity() {
            assertThat(LocationLine.of("khobar").city()).isEqualTo("Al Khobar");
        }
    }

    @Test
    @DisplayName("nothing in, nothing out — a blank line proposes neither half")
    void readsNothingFromNothing() {
        assertThat(LocationLine.of(null).isEmpty()).isTrue();
        assertThat(LocationLine.of("   ").isEmpty()).isTrue();
        assertThat(LocationLine.of(",").isEmpty()).isTrue();
    }
}
