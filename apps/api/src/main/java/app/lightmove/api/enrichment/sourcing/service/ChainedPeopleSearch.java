package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Asks each provider of the {@link PeopleSearchChain} in turn, each through the people cache, until one
 * finds somebody: finding nobody or failing — out of credits, refused, down — hands the company on.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChainedPeopleSearch {

    private final PeopleSearchChain providers;
    private final CachedPeopleSearch cache;

    /**
     * The spend of every provider asked is summed. When the last provider fails after an earlier one found
     * nobody, that empty answer stands; only a company no provider could answer at all throws.
     */
    public PeopleFound currentEmployeesTitled(SearchedEmployer employer, SourcingSpec spec, Seniority seat,
                                              List<String> countryCodes, int size) {
        List<PeopleSearch> order = providers.inOrder();
        int billed = 0;
        int cached = 0;
        PeopleFound answeredEmpty = null;
        RuntimeException lastFailure = null;
        for (PeopleSearch provider : order) {
            try {
                PeopleFound found = cache.currentEmployeesTitled(provider, employer, spec, seat, countryCodes, size);
                billed += found.billed();
                cached += found.cached();
                if (!found.people().isEmpty()) {
                    return found.spending(billed, cached);
                }
                answeredEmpty = found;
                log.info("{} found nobody at {}", provider.provider(), employer.linkedinSlug());
            } catch (RuntimeException failed) {
                lastFailure = failed;
                log.warn("{} people search at {} failed: {}", provider.provider(), employer.linkedinSlug(),
                        describe(failed));
            }
        }
        if (answeredEmpty != null) {
            return answeredEmpty.spending(billed, cached);
        }
        throw lastFailure;
    }

    /** A vendor's message names the call and kind, never its body; anything else is named by type alone. */
    private static String describe(RuntimeException failed) {
        return failed instanceof VendorException ? failed.getMessage() : failed.getClass().getSimpleName();
    }
}
