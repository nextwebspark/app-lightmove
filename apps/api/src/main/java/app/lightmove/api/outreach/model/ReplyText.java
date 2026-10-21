package app.lightmove.api.outreach.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * What someone wrote in a reply, without the history their client quoted: the conversation already holds that
 * history as our own lines. Never markup — an HTML body is read down to its words — and held to
 * {@link OutreachMessage#MAX_TEXT}. What is quoted is removed, not everything after it, so an answer typed between
 * quoted lines stays; a reply that writes nothing of its own is null, never our own email read back as theirs.
 */
public final class ReplyText {

    /** The quoted message itself and the line that introduces it, in Gmail, Apple Mail, Outlook and Thunderbird. */
    private static final String QUOTED_HTML = "blockquote, .gmail_attr, #divRplyFwdMsg, #appendonsend ~ *, "
            + ".moz-cite-prefix";

    /** "On Mon, 20 Oct 2026 at 10:00, Priya <priya@…> wrote:", which Gmail wraps onto a second line when long. */
    private static final Pattern ATTRIBUTION = Pattern.compile("(?m)^On .{1,300}?(\\n.{0,300}?)?wrote:\\s*$");

    private static final Pattern ORIGINAL_MESSAGE = Pattern.compile(
            "(?im)^-{2,}\\s*(original message|ursprüngliche nachricht|message d'origine|messaggio originale)\\s*-{2,}");

    /**
     * Outlook's header block above the message it forwards or answers: a From line with a Sent or Date line right
     * beneath it, in the languages Gulf mailboxes are set to. Outlook quotes without {@code >}, so this is a cut.
     */
    private static final Pattern FORWARD_HEADER = Pattern.compile("(?im)^\\*?(from|von|de|da|van|من)\\s?:\\*?\\s.+\\n"
            + "\\*?(sent|date|gesendet|datum|envoyé|inviato|enviado|verzonden|التاريخ|تاريخ الإرسال|أرسلت)\\s?:\\*?\\s");

    private static final Pattern MARKUP = Pattern.compile("<(html|body|div|p|br|span|table|blockquote)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SPARE_BLANK_LINES = Pattern.compile("\\n{3,}");

    private ReplyText() {
    }

    /** A body a service sent without saying whether it is HTML: read as what it looks like. */
    public static String ofBody(String body) {
        if (body == null) {
            return null;
        }
        return MARKUP.matcher(body).find() ? ofHtml(body) : ofPlainText(body);
    }

    /** Null when nothing of their own is written. */
    public static String ofPlainText(String text) {
        if (text == null) {
            return null;
        }
        return heldToLength(withoutQuotedHistory(text.replace("\r\n", "\n").replace('\r', '\n')));
    }

    /** Null when nothing of their own is written. */
    public static String ofHtml(String html) {
        if (html == null) {
            return null;
        }
        Document document = Jsoup.parse(html);
        document.select("head, script, style").remove();
        document.select(QUOTED_HTML).remove();
        return heldToLength(withoutQuotedHistory(textOf(document)));
    }

    private static String withoutQuotedHistory(String text) {
        int cut = Math.min(firstMatch(ORIGINAL_MESSAGE, text), firstMatch(FORWARD_HEADER, text));
        String kept = ATTRIBUTION.matcher(text.substring(0, cut)).replaceAll("");
        List<String> lines = new ArrayList<>();
        for (String line : kept.split("\n", -1)) {
            if (!line.stripLeading().startsWith(">")) {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    private static int firstMatch(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        return matcher.find() ? matcher.start() : text.length();
    }

    private static String heldToLength(String reply) {
        List<String> lines = new ArrayList<>();
        for (String line : reply.replace(' ', ' ').split("\n", -1)) {
            lines.add(line.strip());
        }
        String text = SPARE_BLANK_LINES.matcher(String.join("\n", lines)).replaceAll("\n\n").strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() <= OutreachMessage.MAX_TEXT) {
            return text;
        }
        int end = OutreachMessage.MAX_TEXT;
        // Never half a character: a cut between a surrogate pair stores a string Postgres refuses.
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private static String textOf(Document document) {
        StringBuilder text = new StringBuilder();
        Element body = document.body();
        if (body == null) {
            return "";
        }
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode words) {
                    text.append(words.text());
                } else if (node instanceof Element element) {
                    if ("br".equals(element.normalName())) {
                        text.append('\n');
                    } else if (element.isBlock()) {
                        endLine(text);
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && element.isBlock()) {
                    endLine(text);
                }
            }
        }, body);
        return text.toString();
    }

    private static void endLine(StringBuilder text) {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
    }
}
