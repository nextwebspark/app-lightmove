package app.lightmove.api.common.time.service;

import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * The time zones a person chooses from: a region's own ({@code Europe/London}) or {@code UTC}, never a legacy alias
 * like {@code EST}, whose meaning the JDK and the browser do not agree on. Static, like {@code Countries}.
 */
public final class RegionZones {

    private static final Pattern REGION_ZONE = Pattern.compile(
            "UTC|(Africa|America|Antarctica|Arctic|Asia|Atlantic|Australia|Europe|Indian|Pacific)/[A-Za-z0-9_+\\-/]+");

    private RegionZones() {}

    public static boolean isRegionZone(String zone) {
        return REGION_ZONE.matcher(zone).matches() && ZoneId.getAvailableZoneIds().contains(zone);
    }
}
