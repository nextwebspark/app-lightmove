/**
 * <b>Resilience.</b> Every vendor call goes through {@code VendorClientFactory} and {@code VendorCallGuard}; adapters
 * throw {@link app.lightmove.api.core.resilience.model.VendorException}; retry is {@code @Retryable} narrowed by
 * {@code VendorRetryPredicate}. The permit is taken per attempt, inside the retry; no vendor call inside a transaction.
 */
package app.lightmove.api.core.resilience;
