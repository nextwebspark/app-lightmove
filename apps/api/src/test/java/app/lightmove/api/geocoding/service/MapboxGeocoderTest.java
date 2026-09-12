package app.lightmove.api.geocoding.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.geocoding.constant.GeoPrecision;
import app.lightmove.api.geocoding.model.GeoPoint;
import app.lightmove.api.geocoding.service.MapboxGeocoder.MapboxContext;
import app.lightmove.api.geocoding.service.MapboxGeocoder.MapboxCoordinates;
import app.lightmove.api.geocoding.service.MapboxGeocoder.MapboxCountry;
import app.lightmove.api.geocoding.service.MapboxGeocoder.MapboxFeature;
import app.lightmove.api.geocoding.service.MapboxGeocoder.MapboxFeatureCollection;
import app.lightmove.api.geocoding.service.MapboxGeocoder.MapboxFeatureProperties;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * The v6 answer → {@link GeoPoint} translation against a fixture shaped on a live forward lookup,
 * and the request's shape. The query-parameter token is the vendor client factory's and is proved in
 * {@code VendorCallResilienceTest}; nothing here builds a URI with a key in it.
 */
class MapboxGeocoderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("a hit's named coordinates become a point at city precision, latitude first")
    void aHitBecomesAPoint() {
        GeoPoint point = MapboxGeocoder.firstPoint(fixture(), "AE", GeoPrecision.CITY).orElseThrow();

        // The named pair, never the positional [lng, lat] geometry — Dubai is at 25°N, not 55°N.
        assertThat(point.latitude()).isEqualTo(25.276987);
        assertThat(point.longitude()).isEqualTo(55.296249);
        assertThat(point.precision()).isEqualTo(GeoPrecision.CITY);
    }

    @Test
    @DisplayName("a hit in the wrong country is no hit: the filter was a request, the answer is the evidence")
    void aHitInAnotherCountryIsIgnored() {
        assertThat(MapboxGeocoder.firstPoint(fixture(), "OM", GeoPrecision.CITY)).isEmpty();
    }

    @Test
    @DisplayName("no expected country accepts whatever came back")
    void noExpectedCountryAcceptsTheHit() {
        assertThat(MapboxGeocoder.firstPoint(fixture(), null, GeoPrecision.CITY)).isPresent();
    }

    @Test
    @DisplayName("an empty answer, a null answer and a hit without coordinates are all no answer")
    void thinAnswersAreEmpty() {
        assertThat(MapboxGeocoder.firstPoint(null, "AE", GeoPrecision.CITY)).isEmpty();
        assertThat(MapboxGeocoder.firstPoint(new MapboxFeatureCollection(List.of()), "AE", GeoPrecision.CITY))
                .isEmpty();
        MapboxFeatureCollection noCoordinates = new MapboxFeatureCollection(List.of(new MapboxFeature(
                new MapboxFeatureProperties("Dubai", "place", null,
                        new MapboxContext(new MapboxCountry("United Arab Emirates", "AE"))))));
        assertThat(MapboxGeocoder.firstPoint(noCoordinates, "AE", GeoPrecision.CITY)).isEmpty();
    }

    @Test
    @DisplayName("a country hit carries no context country and is still accepted at country precision")
    void aCountryHitIsAccepted() {
        MapboxFeatureCollection country = new MapboxFeatureCollection(List.of(new MapboxFeature(
                new MapboxFeatureProperties("Oman", "country", new MapboxCoordinates(57.0, 21.0), null))));

        GeoPoint point = MapboxGeocoder.firstPoint(country, "OM", GeoPrecision.COUNTRY).orElseThrow();
        assertThat(point.precision()).isEqualTo(GeoPrecision.COUNTRY);
        assertThat(point.latitude()).isEqualTo(21.0);
    }

    @Test
    @DisplayName("the request names the place types, the country code and — only when entitled — permanence")
    void theRequestIsShapedForTheVendor() {
        URI temporary = MapboxGeocoder.forwardUri(UriComponentsBuilder.fromUriString("https://api.mapbox.com"),
                "Dubai", "place,locality,district", "AE", false);
        assertThat(temporary.toString())
                .startsWith("https://api.mapbox.com/search/geocode/v6/forward?")
                .contains("q=Dubai")
                .contains("types=place,locality,district")
                .contains("country=AE")
                .contains("limit=1")
                .doesNotContain("permanent")
                .doesNotContain("access_token");

        URI permanent = MapboxGeocoder.forwardUri(UriComponentsBuilder.fromUriString("https://api.mapbox.com"),
                "Oman", "country", null, true);
        assertThat(permanent.toString()).contains("permanent=true").doesNotContain("country=");
    }

    private static MapboxFeatureCollection fixture() {
        InputStream recorded = MapboxGeocoderTest.class.getResourceAsStream("/mapbox/forward-dubai.json");
        return JSON.readValue(recorded, MapboxFeatureCollection.class);
    }
}
