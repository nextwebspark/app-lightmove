package app.lightmove.api.outreach.model;

import app.lightmove.api.common.note.NoteMentionTokens;
import app.lightmove.api.core.storage.service.EmailImageService;
import app.lightmove.api.outreach.constant.SignaturePictureSize;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.UrlSanitizer;

/**
 * An email a consultant wrote in the composer, or a Custom signature, both in the notes' Markdown, as the HTML a mailbox
 * sends. Nothing typed becomes markup but what Markdown means — a raw HTML block or tag is text — a link goes only to
 * http, https or mailto, an image is its alt text, and a mention is the person's name: the id behind it is the team's,
 * never the executive's. The only pictures an email Uncava sends carries are ones Uncava serves: a signature's photo and
 * logo, and in a Custom signature the signer's own uploaded pictures, which {@link #signatureHtmlOf} draws.
 */
public final class OutreachMarkdownEmail {

    private static final List<Extension> EXTENSIONS = List.of(StrikethroughExtension.create(),
            InsExtension.create(), TaskListItemsExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final Set<String> LINK_SCHEMES = Set.of("http", "https", "mailto");
    private static final Pattern PICTURE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{20,64}");
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

    /**
     * A Custom signature's {@code markdown}, drawing each picture that names one of {@code pictures} — the signer's own,
     * keyed by token — at the size its title asks, up to {@link SignaturePicture#MAX_PER_SIGNATURE}. Any other picture,
     * someone else's or another host's, is its alt text as in {@link #htmlOf}.
     */
    public static String signatureHtmlOf(String markdown, Map<String, SignaturePicture> pictures) {
        if (pictures.isEmpty()) {
            return htmlOf(markdown);
        }
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(EXTENSIONS)
                .escapeHtml(true)
                .sanitizeUrls(true)
                .urlSanitizer(new LinkSchemes())
                .nodeRendererFactory(context -> new OwnPictures(context, pictures))
                .build();
        return renderer.render(PARSER.parse(wordsOf(markdown))).strip();
    }

    /**
     * The picture tokens {@code markdown} names, in order, once each, counted as pictures: a picture of ours is one whose
     * address is the email-image route, wherever it is hosted. Another host's picture names no token.
     */
    public static List<String> pictureTokensIn(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        PARSER.parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(Image image) {
                String token = pictureTokenOf(image.getDestination());
                if (token != null) {
                    tokens.add(token);
                }
                visitChildren(image);
            }
        });
        return List.copyOf(tokens);
    }

    /** The number of pictures {@code markdown} draws or names, ours or not: what a signature's limit counts. */
    public static int pictureCountIn(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        int[] count = {0};
        PARSER.parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(Image image) {
                count[0]++;
                visitChildren(image);
            }
        });
        return count[0];
    }

    private static String pictureTokenOf(String destination) {
        if (destination == null) {
            return null;
        }
        int route = destination.indexOf(EmailImageService.PATH);
        if (route < 0) {
            return null;
        }
        String token = destination.substring(route + EmailImageService.PATH.length());
        return PICTURE_TOKEN.matcher(token).matches() ? token : null;
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

    /** The signer's own pictures at a fixed size, as mail clients need; anything else, and past the limit, alt text. */
    private static final class OwnPictures implements NodeRenderer {

        private final HtmlNodeRendererContext context;
        private final Map<String, SignaturePicture> pictures;
        private int drawn;

        OwnPictures(HtmlNodeRendererContext context, Map<String, SignaturePicture> pictures) {
            this.context = context;
            this.pictures = pictures;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Image.class);
        }

        @Override
        public void render(Node node) {
            Image image = (Image) node;
            String token = pictureTokenOf(image.getDestination());
            SignaturePicture picture = token == null ? null : pictures.get(token);
            if (picture == null || drawn >= SignaturePicture.MAX_PER_SIGNATURE) {
                for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                    context.render(child);
                }
                return;
            }
            drawn++;
            int[] size = SignaturePictureSize.fromTitle(image.getTitle()).fit(picture.width(), picture.height());
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("src", picture.url());
            attributes.put("width", String.valueOf(size[0]));
            attributes.put("height", String.valueOf(size[1]));
            attributes.put("alt", altTextOf(image));
            attributes.put("style", "display:inline-block;vertical-align:middle;border:0;margin:0 12px 6px 0;width:"
                    + size[0] + "px;height:" + size[1] + "px");
            context.getWriter().tag("img", attributes, true);
        }

        private static String altTextOf(Image image) {
            StringBuilder alt = new StringBuilder();
            image.accept(new AbstractVisitor() {
                @Override
                public void visit(Text text) {
                    alt.append(text.getLiteral());
                }
            });
            return alt.toString();
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
