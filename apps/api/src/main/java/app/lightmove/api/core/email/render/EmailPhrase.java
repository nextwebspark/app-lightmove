package app.lightmove.api.core.email.render;

/**
 * A value dropped into a sentence — a name, a workspace, a mandate's title.
 *
 * <p>This type is why an email cannot leak markup by accident: user-supplied text reaches a template
 * only as a phrase, and {@link EmailRenderer} escapes every phrase for the HTML half while leaving it
 * raw for the text half. Neither is a judgement call a template makes any more.
 */
public record EmailPhrase(String text, boolean emphasised) {

    public static EmailPhrase plain(String text) {
        return new EmailPhrase(text, false);
    }

    /** Rendered {@code <strong>} in HTML and unmarked in plain text. */
    public static EmailPhrase strong(String text) {
        return new EmailPhrase(text, true);
    }
}
