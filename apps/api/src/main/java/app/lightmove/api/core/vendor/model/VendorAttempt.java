package app.lightmove.api.core.vendor.model;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * One named step of a cascade — a way of asking a vendor the same question, and what to call it in
 * the record of which way worked.
 *
 * @param name what to record when this step is the one that answered; it becomes provenance
 * @param call the lookup, returning empty when the vendor had no answer for it
 */
public record VendorAttempt<T>(String name, Supplier<Optional<T>> call) {
}
