package app.lightmove.api.core.email.render;

import java.util.List;

/** What an email says, with no opinion about how it looks. */
public record EmailContent(String heading, List<EmailBlock> blocks) {

    public EmailContent {
        blocks = List.copyOf(blocks);
    }

    public static EmailContent of(String heading, EmailBlock... blocks) {
        return new EmailContent(heading, List.of(blocks));
    }
}
