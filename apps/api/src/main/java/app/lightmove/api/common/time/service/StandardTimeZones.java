package app.lightmove.api.common.time.service;

import app.lightmove.api.common.service.ClasspathJsonLoader;
import app.lightmove.api.common.time.model.OfferedTimeZone;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** The fixed zone list ({@code data/time-zones.json}, copied by the SPA); only a new choice is held to it. */
public final class StandardTimeZones {

    private static final String RESOURCE = "data/time-zones.json";

    private static final Pattern REGION_ZONE = Pattern.compile(
            "UTC|(Africa|America|Antarctica|Arctic|Asia|Atlantic|Australia|Europe|Indian|Pacific)/[A-Za-z0-9_+\\-/]+");
    private static final Set<String> JDK_ZONES = Set.copyOf(ZoneId.getAvailableZoneIds());

    private static final List<OfferedTimeZone> OFFERED;
    private static final Set<String> IDS;

    static {
        OFFERED = List.copyOf(ClasspathJsonLoader.load(new ObjectMapper(), RESOURCE,
                new TypeReference<List<OfferedTimeZone>>() {}));
        OFFERED.forEach(offered -> ZoneId.of(offered.zone()));
        IDS = OFFERED.stream().map(OfferedTimeZone::zone).collect(Collectors.toUnmodifiableSet());
    }

    private StandardTimeZones() {}

    /**
     * Any region's zone or UTC, listed or not (a Madrid laptop is Madrid), never an alias like {@code EST}, which the
     * JDK and the browser read differently. A person or admin picks only from {@link #isOffered}.
     */
    public static boolean isReportableDeviceZone(String zone) {
        return zone != null && REGION_ZONE.matcher(zone).matches() && JDK_ZONES.contains(zone);
    }

    public static boolean isOffered(String zone) {
        return zone != null && IDS.contains(zone);
    }

    public static List<OfferedTimeZone> all() {
        return OFFERED;
    }
}
