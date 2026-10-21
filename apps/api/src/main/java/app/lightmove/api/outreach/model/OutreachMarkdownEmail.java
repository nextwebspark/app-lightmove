package app.lightmove.api.outreach.model;

import app.lightmove.api.common.note.NoteMentionTokens;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.UrlSanitizer;

/**
 * An email a consultant wrote in the composer, which writes the notes' Markdown, as the HTML a mailbox sends. Nothing
 * typed becomes markup but what Markdown means — a raw HTML block or tag is text — a link goes only to http, https or
 * mailto, an image is its alt text (an email Uncava sends carries no picture from anywhere), and a mention is the
 * person's name: the id behind it is the team's, never the executive's.
 */
public final class OutreachMarkdownEmail {

    private static final List<Extension> EXTENSIONS = List.of(StrikethroughExtension.create(),
            InsExtension.create(), TaskListItemsExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final Set<String> LINK_SCHEMES = Set.of("http", "https", "mailto");
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .urlSanitizer(new LinkSchemes())
            .nodeRendererFactory(AltTextOnly::new)
            .build();

    private OutreachMarkdownEmail() {
    }

    /** {@code markdown} with its tokens already filled in. */
    public static String htmlOf(String markdown) {
        return RENDERER.render(PARSER.parse(wordsOf(markdown))).strip();
    }

    /** What is kept and searched as the email's text: its Markdown, a mention spelled as the name it shows. */
    public static String wordsOf(String markdown) {
        return markdown == null ? "" : NoteMentionTokens.plainTextOf(markdown).replace("\r\n", "\n").strip();
    }

    private static final class LinkSchemes implements UrlSanitizer {

        @Override
        public String sanitizeLinkUrl(String url) {
            int colon = url.indexOf(':');
            String scheme = colon < 0 ? "" : url.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            return LINK_SCHEMES.contains(scheme) ? url : "";
        }

        @Override
        public String sanitizeImageUrl(String url) {
            return "";
        }
    }

    private record AltTextOnly(HtmlNodeRendererContext context) implements NodeRenderer {

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Image.class);
        }

        @Override
        public void render(Node node) {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                context.render(child);
            }
        }
    }
}
