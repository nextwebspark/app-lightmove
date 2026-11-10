package app.lightmove.api.outreach.model;

import java.util.Arrays;
import java.util.regex.Matcher;
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
     * The signature goes where {@code {{signature}}} stands, between paragraphs, or closes a text placing none. Where
     * there is no signature, {@link SequenceTokens#signedOff} first.
     */
    public static String htmlOf(String plainText, String bookingLink, String signatureHtml) {
        String text = plainText == null ? "" : plainText;
        String signature = signatureHtml == null ? "" : signatureHtml;
        Matcher token = SequenceTokens.SIGNATURE_TOKEN.matcher(text);
        StringBuilder html = new StringBuilder();
        int from = 0;
        while (token.find()) {
            html.append(htmlOf(text.substring(from, token.start()), bookingLink)).append(signature);
            from = token.end();
        }
        html.append(htmlOf(text.substring(from), bookingLink));
        return from == 0 ? html.append(signature).toString() : html.toString();
    }

    /**
     * The words of a signed text as the conversation keeps them: the signature is HTML, and never words, and the gap it
     * stood in closes to one blank line.
     */
    public static String withoutSignatures(String plainText) {
        return plainText == null ? "" : SequenceTokens.SIGNATURE_TOKEN.matcher(plainText).replaceAll("")
                .replaceAll("(\\r?\\n[ \\t]*){3,}", "\n\n").strip();
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
