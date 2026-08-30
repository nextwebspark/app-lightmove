package app.lightmove.api.core.vendor.service;

import app.lightmove.api.core.vendor.model.VendorAttempt;
import app.lightmove.api.core.vendor.model.VendorAttemptResult;
import app.lightmove.api.core.vendor.model.VendorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Asks a vendor the same question several ways, stopping at the first way that answers.
 *
 * <p>The pattern behind "if that endpoint gives us nothing, try the other one" — resolve a company's
 * people by its LinkedIn URL, and failing that by its domain. What makes it worth a type rather than
 * a loop is the rule it enforces, which is not obvious and is expensive to get wrong:
 *
 * <ul>
 *   <li><b>Only "no answer" moves on.</b> An empty result or a 404 means this way of asking found
 *       nobody, so another way might. Everything else stops the chain.</li>
 *   <li><b>A hard failure stops it immediately.</b> A bad key, an empty credit balance or a malformed
 *       request will fail identically at every remaining step. Falling through them turns one failure
 *       into as many failures as there are attempts, each one billable and each one slower.</li>
 *   <li><b>So does being rate limited.</b> Being throttled on the first attempt is evidence the
 *       second would be throttled too; that case is for the retry to absorb, not the cascade.</li>
 * </ul>
 *
 * <p>Retry belongs <i>inside</i> an attempt and the cascade <i>outside</i> it, and the order is not
 * interchangeable. Retrying around the whole chain re-pays for every step that already succeeded in
 * saying nothing; moving on instead of retrying abandons a step that a 200 ms wait would have fixed.
 *
 * <p>The result names the step that answered, which is not bookkeeping: how a match was found is
 * evidence about how good it is, and belongs beside whatever is stored.
 */
@Slf4j
public final class VendorAttemptChain<T> {

    private final String description;
    private final List<VendorAttempt<T>> attempts = new ArrayList<>();

    private VendorAttemptChain(String description) {
        this.description = description;
    }

    /** @param description what is being looked for, for the one log line a give-up produces */
    public static <T> VendorAttemptChain<T> forLookup(String description) {
        return new VendorAttemptChain<>(description);
    }

    public VendorAttemptChain<T> attempt(String name, Supplier<Optional<T>> call) {
        attempts.add(new VendorAttempt<>(name, call));
        return this;
    }

    public VendorAttemptResult<T> run() {
        List<String> made = new ArrayList<>(attempts.size());

        for (VendorAttempt<T> attempt : attempts) {
            made.add(attempt.name());
            Optional<T> found = invoke(attempt, made);
            if (found.isPresent()) {
                return VendorAttemptResult.answered(found.get(), attempt.name(), made);
            }
        }

        log.debug("No attempt answered for {} (tried {})", description, made);
        return VendorAttemptResult.unanswered(made);
    }

    private Optional<T> invoke(VendorAttempt<T> attempt, List<String> made) {
        try {
            return attempt.call().get();
        } catch (VendorException failed) {
            if (!failed.getFailure().kind().advancesCascade()) {
                throw failed;
            }
            log.debug("Attempt '{}' for {} found nothing ({}); trying the next",
                    attempt.name(), description, failed.getFailure().describe());
            return Optional.empty();
        }
    }
}
