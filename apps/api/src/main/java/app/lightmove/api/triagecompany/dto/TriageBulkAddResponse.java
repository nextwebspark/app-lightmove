package app.lightmove.api.triagecompany.dto;

/**
 * What "Add all to Universe" actually did.
 *
 * <p>Two numbers, because a success can no longer be partial: a filter matching more than the bulk-add
 * limit is refused outright. {@code added} is new rows, {@code skipped} is companies the mandate
 * already held — including ones it had declined, which are deliberately not resurrected.
 */
public record TriageBulkAddResponse(int added, int skipped) {}
