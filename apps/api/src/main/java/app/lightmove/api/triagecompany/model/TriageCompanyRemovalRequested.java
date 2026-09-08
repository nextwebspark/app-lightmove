package app.lightmove.api.triagecompany.model;

import java.util.UUID;

/**
 * A mandate is about to drop its decision about a company — <b>a question, not an announcement</b>.
 *
 * <p>Published synchronously inside the removing transaction and before the delete, so a listener that
 * still holds rows pointing at the company refuses by throwing: the exception reaches the caller as
 * the removal's own failure and nothing is deleted. {@code candidate} is the one listener today, and
 * it refuses while any executive is mapped there.
 *
 * <p>An event rather than a call because the direction of the dependency is load-bearing:
 * {@code candidate} depends on {@code triagecompany} and never the other way round, so the removal
 * asks in primitives and never learns who answered. The same shape {@code workspace} uses to tell
 * {@code project} an invitation was accepted.
 */
public record TriageCompanyRemovalRequested(UUID projectId, UUID triageCompanyId, String companyName) {}
