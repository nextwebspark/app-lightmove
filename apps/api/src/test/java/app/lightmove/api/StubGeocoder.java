package app.lightmove.api;

import app.lightmove.api.geocoding.constant.GeoPrecision;
import app.lightmove.api.geocoding.model.GeoPoint;
import app.lightmove.api.geocoding.service.Geocoder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link Geocoder} that places whatever the test scripted and remembers what it was asked — the
 * geocoding twin of {@link RecordingCompanyEnricher}. Unscripted places are misses, so a test that
 * wants a point says so.
 */
public class StubGeocoder implements Geocoder {

    private final Map<String, GeoPoint> cities = new ConcurrentHashMap<>();
    private final Map<String, GeoPoint> countries = new ConcurrentHashMap<>();
    private final List<String> asked = new CopyOnWriteArrayList<>();

    @Override
    public Optional<GeoPoint> city(String city, String country) {
        asked.add("city:" + city);
        return Optional.ofNullable(cities.get(city.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Optional<GeoPoint> country(String country) {
        asked.add("country:" + country);
        return Optional.ofNullable(countries.get(country.toLowerCase(Locale.ROOT)));
    }

    public void placeCity(String city, double latitude, double longitude) {
        cities.put(city.toLowerCase(Locale.ROOT), new GeoPoint(latitude, longitude, GeoPrecision.CITY));
    }

    public void placeCountry(String country, double latitude, double longitude) {
        countries.put(country.toLowerCase(Locale.ROOT), new GeoPoint(latitude, longitude, GeoPrecision.COUNTRY));
    }

    public List<String> asked() {
        return List.copyOf(asked);
    }

    public void clear() {
        cities.clear();
        countries.clear();
        asked.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public StubGeocoder stubGeocoder() {
            return new StubGeocoder();
        }
    }
}
