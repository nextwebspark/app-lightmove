/**
 * <b>Resilience — the one disciplined way to call somebody else's API.</b> Every outbound vendor call
 * goes through {@code VendorClientFactory} (timeouts, auth, correlation id, and every non-2xx
 * classified into a {@link app.lightmove.api.core.resilience.constant.VendorFailureKind}) and
 * {@code VendorCallGuard} (the rate-limit permit, and the transport failures no status handler sees).
 * Adapters throw {@link app.lightmove.api.core.resilience.model.VendorException} and never a Spring
 * one. Retry is Spring's own {@code @Retryable}, narrowed by
 * {@link app.lightmove.api.core.resilience.service.VendorRetryPredicate} so a 401 is not paid for
 * three times.
 *
 * <p>The ordering that matters: <b>the permit is taken per attempt, inside the retry.</b> Taken
 * outside it, three attempts spend one permit and burst past the cap the permit exists to respect.
 * And no vendor call belongs inside a transaction — a permit wait plus backoff would hold a database
 * connection for seconds.
 */
package app.lightmove.api.core.resilience;
