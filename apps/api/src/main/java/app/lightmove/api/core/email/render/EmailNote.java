package app.lightmove.api.core.email.render;

import java.util.List;

/** The muted line under the body — how long a link lasts, what to do if the email was not expected. */
public record EmailNote(String sentence, List<EmailPhrase> values) implements EmailBlock {

    public EmailNote {
        values = List.copyOf(values);
    }

    public static EmailNote of(String sentence, EmailPhrase... values) {
        return new EmailNote(sentence, List.of(values));
    }
}
