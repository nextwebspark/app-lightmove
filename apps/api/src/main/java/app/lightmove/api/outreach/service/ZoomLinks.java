package app.lightmove.api.outreach.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A Zoom join link found in an event's location, where Book a call puts it: Zoom is no calendar's own video. */
final class ZoomLinks {

    static final String PROVIDER = "Zoom";

    private static final Pattern JOIN_URL = Pattern.compile("https://(?:[A-Za-z0-9-]+\\.)*zoom\\.us/[^\\s\"<>]+");

    private ZoomLinks() {
    }

    static String joinUrlIn(String text) {
        if (text == null) {
            return null;
        }
        Matcher found = JOIN_URL.matcher(text);
        return found.find() ? found.group() : null;
    }
}
