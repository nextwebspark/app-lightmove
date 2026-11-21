package app.lightmove.api.core.email.render;

import app.lightmove.api.core.email.constant.EmailCardStyle;
import java.util.List;

/**
 * A bordered group of rows. A {@code PLAIN} or {@code QUIET} card may carry a header (title, subtitle and a link on
 * the right); a {@code HIGHLIGHT} card shows its title as a label and numbers its rows. Title, subtitle, link and
 * footnote may each be null.
 */
public record EmailCard(EmailCardStyle style, String title, String subtitle, EmailAction link, List<EmailRow> rows,
                        String footnote) implements EmailBlock {

    public EmailCard {
        rows = List.copyOf(rows);
    }
}
