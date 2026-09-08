package app.lightmove.api.triagecompany.dto;

/**
 * What a bulk add actually did — "Add all to Universe", and the selection bar's three buttons.
 *
 * <p>Two numbers, because a success can no longer be partial: more companies than the bulk-add limit
 * is refused outright rather than truncated. {@code added} is new rows and {@code skipped} is the
 * rest of what was asked for — companies the mandate already held, including ones it had declined and
 * deliberately does not resurrect, plus, for a named selection, any id the universe no longer carries
 * or the mandate has since ruled off-limits.
 */
public record TriageBulkAddResponse(int added, int skipped) {}
