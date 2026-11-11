package app.lightmove.api.outreach.model;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.web.util.HtmlUtils;

/**
 * A sequence is written as plain text; a mailbox sends HTML. Everything is escaped — a token's value
 * came from a profile, and a profile came from the web — and only the paragraph breaks, and the sender's
 * own booking link, become markup.
 */
public final class OutreachEmailBody {

    private OutreachEmailBody() {
    }

    public static String htmlOf(String plainText) {
        return htmlOf(plainText, null);
    }

    /**
     * {@code bookingLink}, where it appears, becomes the one anchor in the email. Only that exact link —
     * a URL that came in through a profile stays text, since nobody chose to send it.
     */
    public static String htmlOf(String plainText, String bookingLink) {
        String normalised = plainText == null ? "" : plainText.replace("\r\n", "\n").strip();
        String escapedLink = bookingLink == null || bookingLink.isBlank() ? null : HtmlUtils.htmlEscape(bookingLink);
        return Arrays.stream(normalised.split("\n\\s*\n"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .map(paragraph -> {
                    String html = HtmlUtils.htmlEscape(paragraph).replace("\n", "<br>");
                    if (escapedLink != null) {
                        html = html.replace(escapedLink, "<a href=\"" + escapedLink + "\">" + escapedLink + "</a>");
                    }
                    return "<p>" + html + "</p>";
                })
                .collect(Collectors.joining());
    }
}
