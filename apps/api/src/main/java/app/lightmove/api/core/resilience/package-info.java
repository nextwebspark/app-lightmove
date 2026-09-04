/**
 * <b>Resilience — the one disciplined way to call somebody else's API.</b> Every outbound call to a
 * paid vendor goes through {@code VendorClientFactory} (timeouts, auth header, outbound correlation
 * id, and every non-2xx classified into a
 * {@link app.lightmove.api.core.resilience.constant.VendorFailureKind}) and
 * {@code VendorCallGuard} (the rate-limit permit, and the transport failures no status handler can
 * see). Adapters throw exactly one exception type,
 * {@link app.lightmove.api.core.resilience.model.VendorException}, and never a Spring one.
 *
 * <p>Retry is Spring Framework's own {@code @Retryable} with
 * {@link app.lightmove.api.core.resilience.service.VendorRetryPredicate} — no retry library, because
 * the framework already ships one. The predicate is what stops the annotation retrying a 401 three
 * times over: only {@code RATE_LIMITED}, {@code UNAVAILABLE} and {@code TIMEOUT} are worth paying for
 * twice.
 *
 * <p>The ordering that matters: <b>the permit is taken per attempt, inside the retry.</b> Taken
 * outside it, three attempts spend one permit and burst past the cap the permit exists to respect.
 * And no vendor call belongs inside a transaction — a permit wait plus backoff would hold a database
 * connection for seconds. The enrichment workers keep that split: the worker calls the vendor, and a
 * separate transactional method writes what came back.
 */
package app.lightmove.api.core.resilience;
