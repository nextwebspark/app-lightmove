package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Find executives — {@code lightmove.enrichment.sourcing.*}. A live profile is the only thing a run buys
 * by the profile, so {@code maxCompaniesPerRun × profilesPerCompany} is one press's ceiling of them (the
 * profile index bills a search, about $0.002 a company, and a title check is a free count).
 */
public record ExecutiveSourcingSettings(

        /** Companies one run may take; the screen offers that many when nothing is ticked. */
        @DefaultValue("5") int maxCompaniesPerRun,

        /** Live profiles bought per company at most, the role's own level first. */
        @DefaultValue("3") int profilesPerCompany,

        /** Executives filed per company at most, best fit first. */
        @DefaultValue("3") int picksPerCompany,

        /** People the profile index returns per company to be checked. */
        @DefaultValue("100") int indexPeoplePerCompany,

        /** Free name-narrowed live counts per company, each confirming one index person's title. */
        @DefaultValue("100") int titleChecksPerCompany,

        /** Companies searched at once. */
        @DefaultValue("4") int parallelism,

        /** How long the whole run may take before the companies not reached are reported as such. */
        @DefaultValue("180s") Duration runDeadline,

        /**
         * ISO-2 codes searched beside the position's own country and each company's own — the Gulf six,
         * since an executive for a Dubai seat is as often in Riyadh.
         */
        @DefaultValue({"AE", "SA", "QA", "KW", "BH", "OM"}) List<String> neighbourCountryCodes,

        /** How long a run's live call queues for its turn behind the run's others before it gives up. */
        @DefaultValue("30s") Duration livePermitWait,

        @DefaultValue BusinessSearchSettings businessSearch
) {

    /** Past this a run still marked in progress was lost with its instance: the deadline, and a minute's grace. */
    public Duration lostAfter() {
        return runDeadline.plusMinutes(1);
    }

    public ExecutiveSourcingSettings {
        if (maxCompaniesPerRun < 1 || picksPerCompany < 1 || parallelism < 1 || indexPeoplePerCompany < 1) {
            throw new IllegalArgumentException(
                    "lightmove.enrichment.sourcing's company, pick, index and parallelism limits must be positive");
        }
        if (profilesPerCompany < 0 || titleChecksPerCompany < 0) {
            throw new IllegalArgumentException(
                    "lightmove.enrichment.sourcing's profile and title-check budgets cannot be negative");
        }
        if (runDeadline == null || runDeadline.isNegative() || runDeadline.isZero()) {
            throw new IllegalArgumentException("lightmove.enrichment.sourcing.run-deadline must be positive");
        }
        neighbourCountryCodes = neighbourCountryCodes == null ? List.of()
                : neighbourCountryCodes.stream().filter(code -> code != null && !code.isBlank()).toList();
    }
}
