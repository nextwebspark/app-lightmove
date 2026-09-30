package app.lightmove.api.enrichment.peoplesearch.service;

import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.geocoding.constant.PlaceKind;
import app.lightmove.api.geocoding.model.PlaceSuggestion;
import app.lightmove.api.geocoding.service.Geocoder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The People sidebar's Location box. ContactOut matches LinkedIn's own place names and publishes no list
 * of them, so three sources are merged, surest first: countries from our catalog; places people on file
 * live in, which are LinkedIn's spellings, metro areas included; then Mapbox for a place nobody on file
 * lives in yet. Mapbox answers are held briefly in memory and never stored.
 */
@Slf4j
@Service
public class LocationSuggestions {

    static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 80;
    private static final int MAX_SUGGESTIONS = 10;
    private static final int MAX_COUNTRIES = 3;
    private static final int MAX_FROM_PROFILES = 5;
    private static final int MAX_FROM_MAPBOX = 5;
    private static final Duration MAPBOX_TTL = Duration.ofHours(1);

    private final CachedPeopleStore people;
    private final Geocoder geocoder;
    private final Cache<String, List<PlaceSuggestion>> mapboxAnswers;

    public LocationSuggestions(CachedPeopleStore people, Geocoder geocoder) {
        this.people = people;
        this.geocoder = geocoder;
        this.mapboxAnswers = Caffeine.newBuilder().expireAfterWrite(MAPBOX_TTL).maximumSize(5_000).build();
    }

    public List<PlaceSuggestion> suggest(String query) {
        String prefix = query == null ? "" : query.strip();
        if (prefix.length() < MIN_QUERY_LENGTH || prefix.length() > MAX_QUERY_LENGTH) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        List<PlaceSuggestion> merged = new ArrayList<>();
        Stream.of(countries(prefix), fromProfiles(prefix), fromMapbox(prefix))
                .flatMap(List::stream)
                .filter(place -> seen.add(place.value().toLowerCase(Locale.ROOT)))
                .limit(MAX_SUGGESTIONS)
                .forEach(merged::add);
        return merged;
    }

    private static List<PlaceSuggestion> countries(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return Countries.all().stream()
                .filter(country -> startsWith(country.name(), lower)
                        || country.spellings().stream().anyMatch(spelling -> startsWith(spelling, lower)))
                .limit(MAX_COUNTRIES)
                .map(Country::name)
                .map(name -> new PlaceSuggestion(name, name, PlaceKind.COUNTRY))
                .toList();
    }

    private List<PlaceSuggestion> fromProfiles(String prefix) {
        return people.placesStartingWith(prefix, MAX_FROM_PROFILES).stream()
                .map(place -> new PlaceSuggestion(place, place, kindOf(place)))
                .toList();
    }

    /** Typing must never fail: a Mapbox that cannot answer leaves the surer sources to answer alone. */
    private List<PlaceSuggestion> fromMapbox(String prefix) {
        try {
            return mapboxAnswers.get(prefix.toLowerCase(Locale.ROOT),
                    ignored -> geocoder.suggest(prefix, MAX_FROM_MAPBOX));
        } catch (VendorException failed) {
            log.warn("Mapbox place suggestions failed: {}", failed.getKind());
            return List.of();
        }
    }

    /** LinkedIn's own spellings: a metro area names itself, and a bare country is one. */
    private static PlaceKind kindOf(String place) {
        if (Countries.resolveSpelling(place).isPresent() && !place.contains(",")) {
            return PlaceKind.COUNTRY;
        }
        return place.endsWith(" Area") ? PlaceKind.AREA : PlaceKind.CITY;
    }

    private static boolean startsWith(String value, String lowerPrefix) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix);
    }
}
