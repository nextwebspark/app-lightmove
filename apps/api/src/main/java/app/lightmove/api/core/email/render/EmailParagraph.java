package app.lightmove.api.core.email.render;

import java.util.List;

/**
 * A sentence of body copy: a format string carrying {@code %s} for each phrase that fills it.
 *
 * <p>The format string is always a literal written here in Java — only the phrases carry anything a
 * user typed. That split is what lets one sentence render into both halves of the email without being
 * written twice, and it is what makes escaping the renderer's business rather than each template's.
 */
public record EmailParagraph(String sentence, List<EmailPhrase> values) implements EmailBlock {

    public EmailParagraph {
        values = List.copyOf(values);
    }

    public static EmailParagraph of(String sentence, EmailPhrase... values) {
        return new EmailParagraph(sentence, List.of(values));
    }
}
