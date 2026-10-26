package app.lightmove.api.outreach.model;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * Outlook names a mailbox's zone the Windows way ("Arabian Standard Time"), which {@link ZoneId} cannot read. The
 * zones our firms and the people they hire keep, from CLDR's windowsZones table; a zone outside it, or Outlook's
 * "Customized Time Zone", answers empty and the consultant's own profile zone is used instead.
 */
public final class WindowsZones {

    private static final Map<String, String> IANA_BY_WINDOWS_NAME = Map.ofEntries(
            Map.entry("UTC", "UTC"),
            Map.entry("Arabian Standard Time", "Asia/Dubai"),
            Map.entry("Arab Standard Time", "Asia/Riyadh"),
            Map.entry("Arabic Standard Time", "Asia/Baghdad"),
            Map.entry("Egypt Standard Time", "Africa/Cairo"),
            Map.entry("Jordan Standard Time", "Asia/Amman"),
            Map.entry("Middle East Standard Time", "Asia/Beirut"),
            Map.entry("Syria Standard Time", "Asia/Damascus"),
            Map.entry("Israel Standard Time", "Asia/Jerusalem"),
            Map.entry("Iran Standard Time", "Asia/Tehran"),
            Map.entry("Turkey Standard Time", "Europe/Istanbul"),
            Map.entry("Morocco Standard Time", "Africa/Casablanca"),
            Map.entry("South Africa Standard Time", "Africa/Johannesburg"),
            Map.entry("E. Africa Standard Time", "Africa/Nairobi"),
            Map.entry("W. Central Africa Standard Time", "Africa/Lagos"),
            Map.entry("GMT Standard Time", "Europe/London"),
            Map.entry("Greenwich Standard Time", "Atlantic/Reykjavik"),
            Map.entry("W. Europe Standard Time", "Europe/Berlin"),
            Map.entry("Romance Standard Time", "Europe/Paris"),
            Map.entry("Central Europe Standard Time", "Europe/Budapest"),
            Map.entry("Central European Standard Time", "Europe/Warsaw"),
            Map.entry("GTB Standard Time", "Europe/Bucharest"),
            Map.entry("FLE Standard Time", "Europe/Kiev"),
            Map.entry("Russian Standard Time", "Europe/Moscow"),
            Map.entry("Pakistan Standard Time", "Asia/Karachi"),
            Map.entry("India Standard Time", "Asia/Calcutta"),
            Map.entry("Sri Lanka Standard Time", "Asia/Colombo"),
            Map.entry("Bangladesh Standard Time", "Asia/Dhaka"),
            Map.entry("SE Asia Standard Time", "Asia/Bangkok"),
            Map.entry("Singapore Standard Time", "Asia/Singapore"),
            Map.entry("China Standard Time", "Asia/Shanghai"),
            Map.entry("Taipei Standard Time", "Asia/Taipei"),
            Map.entry("Tokyo Standard Time", "Asia/Tokyo"),
            Map.entry("Korea Standard Time", "Asia/Seoul"),
            Map.entry("AUS Eastern Standard Time", "Australia/Sydney"),
            Map.entry("New Zealand Standard Time", "Pacific/Auckland"),
            Map.entry("Eastern Standard Time", "America/New_York"),
            Map.entry("Central Standard Time", "America/Chicago"),
            Map.entry("Mountain Standard Time", "America/Denver"),
            Map.entry("Pacific Standard Time", "America/Los_Angeles"),
            Map.entry("E. South America Standard Time", "America/Sao_Paulo"));

    private WindowsZones() {}

    /** An IANA name as itself (Graph answers one where the mailbox was set up that way), a Windows one mapped. */
    public static Optional<ZoneId> zoneOf(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String iana = IANA_BY_WINDOWS_NAME.get(name.trim());
        try {
            return Optional.of(ZoneId.of(iana == null ? name.trim() : iana));
        } catch (DateTimeException unreadable) {
            return Optional.empty();
        }
    }
}
