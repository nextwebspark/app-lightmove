package app.lightmove.api.common.time.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CountryTimeZonesTest {

    @Test
    @DisplayName("a country reads as its own listed zone, a multi-zone one as its capital's")
    void readsTheCountrysZone() {
        assertThat(CountryTimeZones.zoneOf("United Arab Emirates")).contains(ZoneId.of("Asia/Dubai"));
        assertThat(CountryTimeZones.zoneOf("UAE")).contains(ZoneId.of("Asia/Dubai"));
        assertThat(CountryTimeZones.zoneOf("Qatar")).contains(ZoneId.of("Asia/Qatar"));
        assertThat(CountryTimeZones.zoneOf("Oman")).contains(ZoneId.of("Asia/Muscat"));
        assertThat(CountryTimeZones.zoneOf("United Kingdom")).contains(ZoneId.of("Europe/London"));
        assertThat(CountryTimeZones.zoneOf("United States")).contains(ZoneId.of("America/New_York"));
        assertThat(CountryTimeZones.zoneOf("Canada")).contains(ZoneId.of("America/New_York"));
        assertThat(CountryTimeZones.zoneOf("Russia")).contains(ZoneId.of("Europe/Moscow"));
    }

    @Test
    @DisplayName("a country without a listed zone of its own takes the listed one keeping its offsets")
    void readsAListedZoneForAnUnlistedCountry() {
        assertThat(CountryTimeZones.zoneOf("Algeria")).contains(ZoneId.of("Africa/Lagos"));
        assertThat(CountryTimeZones.zoneOf("Spain")).contains(ZoneId.of("Europe/Paris"));
        assertThat(CountryTimeZones.zoneOf("Yemen")).contains(ZoneId.of("Asia/Riyadh"));
    }

    @Test
    @DisplayName("no country, or one nobody knows, has no zone")
    void knowsNothingOfAnUnknownCountry() {
        assertThat(CountryTimeZones.zoneOf(null)).isEmpty();
        assertThat(CountryTimeZones.zoneOf("Atlantis")).isEmpty();
    }
}
