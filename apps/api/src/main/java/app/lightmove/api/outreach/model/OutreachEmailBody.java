package app.lightmove.api.outreach.model;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.web.util.HtmlUtils;

/**
 * A sequence is written as plain text; a mailbox sends HTML. Everything is escaped — a token's value
 * came from a profile, and a profile came from the web — and only the paragraph breaks become markup.
 */
public final class OutreachEmailBody {

    private OutreachEmailBody() {
    }

    public static String htmlOf(String plainText) {
        String normalised = plainText == null ? "" : plainText.replace("\r\n", "\n").strip();
        return Arrays.stream(normalised.split("\n\\s*\n"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .map(paragraph -> "<p>" + HtmlUtils.htmlEscape(paragraph).replace("\n", "<br>") + "</p>")
                .collect(Collectors.joining());
    }
}
