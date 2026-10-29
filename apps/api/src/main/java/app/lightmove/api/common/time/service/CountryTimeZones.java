package app.lightmove.api.common.time.service;

import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.common.service.ClasspathJsonLoader;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * A country's time zone, for a workspace whose admin has chosen none: always one of {@link StandardTimeZones}, the
 * listed zone keeping the country's own offsets (Oman's own, Algeria's West Africa Time as Lagos). Derived from IANA's
 * {@code zone.tab}, a multi-zone country taking its capital's.
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
