package app.lightmove.api.geocoding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.MapboxSettings;
import app.lightmove.api.core.config.TalentMapSettings;
import app.lightmove.api.geocoding.model.GeocodingResult;
import app.lightmove.api.geocoding.model.PlaceKey;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a read is worth when no geocoding vendor is configured. */
class GeocodingServiceTest {

    @Test
    @DisplayName("with geocoding off nothing is stored as a miss, so a token set later still places it")
    void geocodingOffRemembersNothing() {
        GeocodedPlaceStore store = mock(GeocodedPlaceStore.class);
        when(store.find(any())).thenReturn(Map.of());
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.mapbox())
                .thenReturn(new MapboxSettings(null, null, "https://api.mapbox.com", 10, false, Duration.ofDays(30)));
        when(properties.talentMap()).thenReturn(new TalentMapSettings(2000, 5000, 50, Duration.ofSeconds(20)));
        GeocodingService service = new GeocodingService(new LogGeocoder(), store, properties);

        GeocodingResult result = service.resolve(Set.of(PlaceKey.of(null, "France").orElseThrow()));

        assertThat(result.points()).isEmpty();
        assertThat(result.pending()).isZero();
        verify(store, never()).remember(any(), any());
    }
}
