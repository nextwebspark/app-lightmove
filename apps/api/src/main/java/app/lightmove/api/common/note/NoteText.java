package app.lightmove.api.common.note;

import app.lightmove.api.common.constant.NoteFormat;
import java.util.List;
import java.util.Set;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.text.TextContentNodeRendererContext;
import org.commonmark.renderer.text.TextContentRenderer;

/**
 * A note's words for a reader that is not the editor. Plain text keeps the words and drops the formatting — a
 * timeline excerpt, a spreadsheet cell; the prompt keeps the Markdown, which a model reads as written. Both name a
 * mention as {@code @Name}, never the id behind it.
 */
public final class NoteText {

    private static final List<Extension> EXTENSIONS = List.of(StrikethroughExtension.create(),
            InsExtension.create(), TaskListItemsExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    /** Registered ahead of the extensions, so it is the one asked for the three nodes it names. */
    private static final TextContentRenderer RENDERER = TextContentRenderer.builder()
            .nodeRendererFactory(PlainWords::new)
            .extensions(EXTENSIONS)
            .build();

    private NoteText() {}

    public static String plainTextOf(String body, NoteFormat format) {
        if (body == null) {
            return null;
        }
        String words = NoteMentionTokens.plainTextOf(body);
        return format == NoteFormat.MARKDOWN ? RENDERER.render(PARSER.parse(words)).strip() : words;
    }

    public static String forPrompt(String body, NoteFormat format) {
        return body == null ? null : NoteMentionTokens.plainTextOf(body);
    }

    /** Struck words as words, a checklist's box as {@code [ ]} or {@code [x]}, a quote as its lines. */
    private record PlainWords(TextContentNodeRendererContext context) implements NodeRenderer {

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Strikethrough.class, TaskListItemMarker.class, BlockQuote.class);
        }

        @Override
        public void render(Node node) {
            if (node instanceof TaskListItemMarker marker) {
                context.getWriter().write(marker.isChecked() ? "[x] " : "[ ] ");
                return;
            }
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                context.render(child);
            }
        }
    }
}
