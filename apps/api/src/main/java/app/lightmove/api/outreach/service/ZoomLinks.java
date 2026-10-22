package app.lightmove.api.outreach.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A Zoom join link found in an event's location, where Book a call puts it: Zoom is no calendar's own video. */
final class ZoomLinks {

    static final String PROVIDER = "Zoom";

    private static final Pattern JOIN_URL = Pattern.compile("https://(?:[A-Za-z0-9-]+\\.)*zoom\\.us/[^\\s\"<>]+");
    /** A join link's meeting number: {@code /j/<n>}, {@code /w/<n>} or the web client's {@code /wc/join/<n>}. */
    private static final Pattern MEETING_NUMBER = Pattern.compile("/(?:j|w|wc/join|s)/(\\d{9,12})(?:[/?#]|$)");

    private ZoomLinks() {
    }

    static Optional<String> meetingIdOf(String joinUrl) {
        if (joinUrl == null) {
            return Optional.empty();
        }
        Matcher found = MEETING_NUMBER.matcher(joinUrl);
        return found.find() ? Optional.of(found.group(1)) : Optional.empty();
    }

    static String joinUrlIn(String text) {
        if (text == null) {
            return null;
        }
        Matcher found = JOIN_URL.matcher(text);
        return found.find() ? found.group() : null;
    }
}
