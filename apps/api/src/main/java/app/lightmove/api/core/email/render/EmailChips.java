package app.lightmove.api.core.email.render;

import java.util.List;

/** A row of headline counts under the opening sentence. */
public record EmailChips(List<EmailChip> chips) implements EmailBlock {

    public EmailChips {
        chips = List.copyOf(chips);
    }
}
