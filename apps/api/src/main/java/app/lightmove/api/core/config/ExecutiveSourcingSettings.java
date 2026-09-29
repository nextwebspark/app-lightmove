package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Find executives — {@code lightmove.enrichment.sourcing.*}. Every hit the people search returns is
 * billed, so the two per-company numbers here are the run's cost ceiling:
 * {@code maxCompaniesPerRun × hitsPerCompany} records at most.
 */
public record ExecutiveSourcingSettings(

        /** Companies one run may take; the screen offers that many when nothing is ticked. */
        @DefaultValue("5") int maxCompaniesPerRun,

        /** Records bought per company at most. */
        @DefaultValue("10") int hitsPerCompany,

        /** Executives filed per company at most, in the order the search returned them. */
        @DefaultValue("3") int picksPerCompany,

        /** Companies searched at once — each holds a vendor call and then a model call. */
        @DefaultValue("4") int parallelism,

        /** Searches per company at most: the first, and each rewording while a search finds nobody. */
        @DefaultValue("3") int maxSearchRounds,

        /** How long the whole run may take before the companies not reached are reported as such. */
        @DefaultValue("180s") Duration runDeadline,

        /**
         * ISO-2 codes searched beside the position's own country — the Gulf six, since an executive
         * for a Dubai seat is as often in Riyadh. Empty means the position's country alone. Nobody
         * living outside them is searched for.
         */
        @DefaultValue({"AE", "SA", "QA", "KW", "BH", "OM"}) List<String> neighbourCountryCodes
) {

    /** Past this a run still marked in progress was lost with its instance: the deadline, and a minute's grace. */
    public Duration lostAfter() {
        return runDeadline.plusMinutes(1);
    }

    public ExecutiveSourcingSettings {
        if (maxCompaniesPerRun < 1 || hitsPerCompany < 1 || picksPerCompany < 1 || parallelism < 1
                || maxSearchRounds < 1) {
            throw new IllegalArgumentException(
                    "lightmove.enrichment.sourcing's company, hit, pick, parallelism and round limits must be positive");
        }
        if (picksPerCompany > hitsPerCompany) {
            throw new IllegalArgumentException(
                    "lightmove.enrichment.sourcing.picks-per-company cannot exceed hits-per-company");
        }
        if (runDeadline == null || runDeadline.isNegative() || runDeadline.isZero()) {
            throw new IllegalArgumentException("lightmove.enrichment.sourcing.run-deadline must be positive");
        }
        neighbourCountryCodes = neighbourCountryCodes == null ? List.of()
                : neighbourCountryCodes.stream().filter(code -> code != null && !code.isBlank()).toList();
    }
}
