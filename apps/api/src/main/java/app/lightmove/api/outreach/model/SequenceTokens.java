package app.lightmove.api.outreach.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The values a sequence's {@code {{token}}}s are filled with for one person. A token nobody offers is
 * left exactly as typed, so a misspelling shows in the review rather than vanishing from the email.
 */
public record SequenceTokens(String firstName, String currentTitle, String currentCompany, String positionTitle,
                             String location, String senderFirstName, String opener, String bookingLink) {

    public static final String BOOKING_LINK = "bookingLink";

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z]+)\\s*}}");

    public String render(String template) {
        if (template == null) {
            return "";
        }
        Map<String, String> values = values();
        Matcher token = TOKEN.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (token.find()) {
            String value = values.get(token.group(1));
            token.appendReplacement(rendered, Matcher.quoteReplacement(value == null ? token.group() : value));
        }
        token.appendTail(rendered);
        return rendered.toString();
    }

    private Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("firstName", orBlank(firstName));
        values.put("currentTitle", orBlank(currentTitle));
        values.put("currentCompany", orBlank(currentCompany));
        values.put("positionTitle", orBlank(positionTitle));
        values.put("location", orBlank(location));
        values.put("senderFirstName", orBlank(senderFirstName));
        values.put("opener", orBlank(opener));
        values.put(BOOKING_LINK, orBlank(bookingLink));
        return values;
    }

    /** Whether {@code template} asks for {@code token}, however it is spaced inside the braces. */
    public static boolean uses(String template, String token) {
        if (template == null) {
            return false;
        }
        Matcher found = TOKEN.matcher(template);
        while (found.find()) {
            if (found.group(1).equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static String orBlank(String value) {
        return value == null ? "" : value.trim();
    }

    /** The first word of a full name, which is what a greeting uses. */
    public static String firstNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        return fullName.trim().split("\\s+", 2)[0];
    }
}
