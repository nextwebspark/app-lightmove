package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How much one read of a mandate's talent map may carry and cost — {@code lightmove.talent-map.*}.
 *
 * <p>The map is unpaged on purpose — a globe with page two is no globe — so the caps here are what a
 * pager would otherwise be. Both are stated back in the response as totals, so a mandate past them is
 * told "showing 2000 of 2314" rather than shown a map that looks complete and is not.
 */
public record TalentMapSettings(
        @DefaultValue("2000") int maxCompanies,
        @DefaultValue("5000") int maxCandidates,

        /**
         * Vendor calls one read may spend on places nobody has geocoded yet. The rest is reported as
         * pending and polled in, rather than the read waiting on all of it.
         */
        @DefaultValue("50") int geocodesPerRead,

        /**
         * How long those calls may take in total. The count above bounds a vendor that <i>fails</i>;
         * this bounds one that is merely slow, which never throws and would otherwise run the full
         * count of read timeouts past the gateway's own.
         */
        @DefaultValue("20s") Duration geocodingDeadline
) {}
