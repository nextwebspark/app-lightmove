package app.lightmove.api.triagecompany.dto;

/**
 * What "Add all to Universe" actually did.
 *
 * <p>Three numbers rather than one, because all three are things the consultant would otherwise have
 * to discover: {@code added} is new rows, {@code skipped} is companies the mandate already held —
 * including ones it had declined, which are deliberately not resurrected — and {@code capped} says
 * the filter matched more than {@code limit} and only the first {@code limit} were taken. A toast
 * claiming the whole universe was added when 500 of 71,822 were is the failure this prevents.
 */
public record TriageBulkAddResponse(int added, int skipped, boolean capped, int limit) {}
