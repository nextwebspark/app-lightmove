package app.lightmove.api.common.time.service;

import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.common.service.ClasspathJsonLoader;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * A country's zone, always one of {@link StandardTimeZones} with the country's own offsets (Algeria's as Lagos); a
 * multi-zone country takes its capital's.
 */
public final class CountryTimeZones {

    private static final String RESOURCE = "data/country-time-zones.json";

    private static final Map<String, String> ZONE_BY_CODE;

    static {
        ZONE_BY_CODE = Map.copyOf(ClasspathJsonLoader.load(new ObjectMapper(), RESOURCE,
                new TypeReference<Map<String, String>>() {}));
        ZONE_BY_CODE.forEach((code, zone) -> {
            if (!StandardTimeZones.isOffered(zone)) {
                throw new IllegalStateException("%s gives %s %s, which is not on the list".formatted(RESOURCE, code, zone));
            }
        });
    }

    private CountryTimeZones() {}

    /** Empty where the country is unknown or unspelled. */
    public static Optional<ZoneId> zoneOf(String countrySpelling) {
        String code = countrySpelling == null ? null : Countries.codeOf(countrySpelling);
        return Optional.ofNullable(code == null ? null : ZONE_BY_CODE.get(code)).map(ZoneId::of);
    }
}
