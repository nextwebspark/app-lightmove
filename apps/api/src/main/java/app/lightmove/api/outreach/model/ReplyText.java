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
 * What someone wrote in a reply, without the history their client quoted beneath it: the conversation already holds
 * that history as our own lines. Never markup — an HTML body is read down to its words — and held to
 * {@link OutreachMessage#MAX_TEXT}. A reply that is nothing but a quote keeps the whole, rather than reading as empty.
 */
public final class ReplyText {

    /** Where Gmail, Apple Mail, Outlook on the web and Thunderbird put the message being answered. */
    private static final String QUOTED_HTML = ".gmail_quote, blockquote, #divRplyFwdMsg, #appendonsend ~ *, "
            + ".moz-cite-prefix";

    /** "On Mon, 20 Oct 2026 at 10:00, Priya <priya@…> wrote:", which Gmail wraps onto a second line when long. */
    private static final Pattern ATTRIBUTION = Pattern.compile("(?m)^On .{1,300}?(\\n.{0,300}?)?wrote:\\s*$");

    private static final Pattern ORIGINAL_MESSAGE = Pattern.compile("(?im)^-{2,}\\s*original message\\s*-{2,}");

    /** Outlook's header block: a From line with a Sent or Date line right beneath it. */
    private static final Pattern FORWARD_HEADER = Pattern.compile("(?im)^\\*?from:\\*?\\s.+\\n\\*?(sent|date):\\*?\\s");

    private static final Pattern SPARE_BLANK_LINES = Pattern.compile("\\n{3,}");

    private ReplyText() {
    }

    /** Null when nothing is written. */
    public static String ofPlainText(String text) {
        if (text == null) {
            return null;
        }
        String whole = text.replace("\r\n", "\n").replace('\r', '\n');
        return heldToLength(withoutQuotedHistory(whole), whole);
    }

    /** Null when nothing is written. */
    public static String ofHtml(String html) {
        if (html == null) {
            return null;
        }
        Document document = Jsoup.parse(html);
        document.select("head, script, style").remove();
        String whole = textOf(document);
        document.select(QUOTED_HTML).remove();
        return heldToLength(withoutQuotedHistory(textOf(document)), whole);
    }

    private static String withoutQuotedHistory(String text) {
        int cut = text.length();
        cut = Math.min(cut, firstMatch(ATTRIBUTION, text));
        cut = Math.min(cut, firstMatch(ORIGINAL_MESSAGE, text));
        cut = Math.min(cut, firstMatch(FORWARD_HEADER, text));
        List<String> kept = new ArrayList<>();
        for (String line : text.substring(0, cut).split("\n", -1)) {
            if (!line.stripLeading().startsWith(">")) {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
    }

    private static int firstMatch(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        return matcher.find() ? matcher.start() : text.length();
    }

    private static String heldToLength(String reply, String whole) {
        String text = tidied(reply);
        if (text.isEmpty()) {
            text = tidied(whole);
        }
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= OutreachMessage.MAX_TEXT ? text : text.substring(0, OutreachMessage.MAX_TEXT);
    }

    private static String tidied(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            lines.add(line.strip());
        }
        return SPARE_BLANK_LINES.matcher(String.join("\n", lines)).replaceAll("\n\n").strip();
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
