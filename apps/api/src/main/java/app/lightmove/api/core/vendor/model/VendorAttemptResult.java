package app.lightmove.api.core.vendor.model;

import java.util.List;
import java.util.Optional;

/**
 * What a cascade produced, and which step produced it.
 *
 * <p>{@code answeredBy} is not bookkeeping. Which lookup found somebody is evidence about how good
 * the match is — a hit on an exact company identifier is worth more than a hit on a fuzzy one — so it
 * is provenance the caller is expected to store beside the result, not a debug field.
 *
 * @param value       what was found, or empty if no step answered
 * @param answeredBy  the name of the step that answered, or null if none did
 * @param attemptsMade every step actually invoked, in order — shorter than the chain when a hard
 *                     failure stopped it early
 */
public record VendorAttemptResult<T>(Optional<T> value, String answeredBy, List<String> attemptsMade) {

    public static <T> VendorAttemptResult<T> answered(T value, String answeredBy, List<String> attemptsMade) {
        return new VendorAttemptResult<>(Optional.of(value), answeredBy, List.copyOf(attemptsMade));
    }

    public static <T> VendorAttemptResult<T> unanswered(List<String> attemptsMade) {
        return new VendorAttemptResult<>(Optional.empty(), null, List.copyOf(attemptsMade));
    }

    public boolean isAnswered() {
        return value.isPresent();
    }
}
