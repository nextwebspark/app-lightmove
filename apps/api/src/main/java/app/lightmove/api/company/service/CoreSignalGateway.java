package app.lightmove.api.company.service;

import app.lightmove.api.company.model.CoreSignalCompanyRecord;
import app.lightmove.api.company.model.CoreSignalSearchCriteria;
import app.lightmove.api.company.model.CoreSignalSearchResult;
import java.util.Optional;

/**
 * The CoreSignal Multi-source Company API, as the two calls the sourcing flow needs. An interface
 * for the same reason {@code EmailSender} is one: the real adapter talks HTTP and spends money, so
 * tests swap in a recording stub, and a missing API key swaps in an implementation that fails
 * honestly (see {@code CoreSignalConfig}).
 */
public interface CoreSignalGateway {

    /**
     * Search companies matching the criteria, revenue-desc. Costs search credits per call.
     *
     * @throws CoreSignalUnavailableException on any provider failure — search failures are always
     *         fatal to a run, there is nothing to skip past
     */
    CoreSignalSearchResult searchCompanyIds(CoreSignalSearchCriteria criteria, int limit);

    /**
     * Collect one company's full record. Costs collect credits on success.
     *
     * @return empty when CoreSignal no longer knows the id (their 404) — the caller skips it
     * @throws CoreSignalUnavailableException fatal for bad-key/no-credits, non-fatal for
     *         rate-limits and transient server errors
     */
    Optional<CoreSignalCompanyRecord> collect(long coresignalId);
}
