package app.lightmove.api.common.time.service;

import app.lightmove.api.common.service.ClasspathJsonLoader;
import app.lightmove.api.common.time.model.OfferedTimeZone;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The fixed list of time zones a workspace or a person chooses from ({@code data/time-zones.json}, which the SPA's
 * {@code lib/datetime/timeZones.ts} copies). A zone saved before the list keeps working; only a new choice is held to
 * it. Static, like {@code Countries}.
 */
public final class StandardTimeZones {

    private static final String RESOURCE = "data/time-zones.json";

    private static final List<OfferedTimeZone> OFFERED;
    private static final Set<String> IDS;

    static {
        OFFERED = List.copyOf(ClasspathJsonLoader.load(new ObjectMapper(), RESOURCE,
                new TypeReference<List<OfferedTimeZone>>() {}));
        OFFERED.forEach(offered -> ZoneId.of(offered.zone()));
        IDS = OFFERED.stream().map(OfferedTimeZone::zone).collect(Collectors.toUnmodifiableSet());
    }

    private StandardTimeZones() {}

    public static boolean isOffered(String zone) {
        return zone != null && IDS.contains(zone);
    }

    public static List<OfferedTimeZone> all() {
        return OFFERED;
    }
}
