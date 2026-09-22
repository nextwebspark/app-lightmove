package app.lightmove.api.companydiscovery.service;

import app.lightmove.api.companydiscovery.model.DiscoveryAnswer;
import app.lightmove.api.companydiscovery.model.DiscoveryQuery;

/**
 * Where candidate companies come from. The provider is a yml block and never a branch on a provider
 * name, the same shape as {@code LinkedInCompanyEnricher} and {@code ContactFinder}.
 *
 * <p>An implementation answers <b>identities</b> — a name, a LinkedIn page, a homepage, a reason.
 * It never answers a figure, and nothing downstream will read one from it.
 */
public interface CompanyDiscovery {

    /** Never null. An empty list is a legitimate answer; the mode says whether anyone was asked. */
    DiscoveryAnswer discover(DiscoveryQuery query);

    /** For the log and the audit trail. */
    String provider();

    /**
     * Whether this deployment offers discovery at all.
     *
     * <p>Exists for {@code LinkedInCompanyEnricher}'s documented reason: without it, every question
     * asked before a provider is configured would be charged against the workspace's day and
     * answered with nothing.
     */
    default boolean isEnabled() {
        return true;
    }
}
