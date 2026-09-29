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

        /** Executives filed per company at most; the model may pick fewer. */
        @DefaultValue("3") int picksPerCompany,

        /**
         * The lowest rerank score (1–10) a pick is filed at. Below it the model is saying "nearest
         * available", not "fits" — a divisional finance head scored 6 for a group CFO seat.
         */
        @DefaultValue("7") int minPickScore,

        /** Companies searched at once — each holds a vendor call and then a model call. */
        @DefaultValue("4") int parallelism,

        /** How long the whole run may take before the companies not reached are reported as such. */
        @DefaultValue("180s") Duration runDeadline,

        /**
         * ISO-2 codes searched beside the position's own country — the Gulf six, since an executive
         * for a Dubai seat is as often in Riyadh. Empty means the position's country alone. Nobody
         * living outside them is searched for.
         */
        @DefaultValue({"AE", "SA", "QA", "KW", "BH", "OM"}) List<String> neighbourCountryCodes
) {

    public ExecutiveSourcingSettings {
        if (maxCompaniesPerRun < 1 || hitsPerCompany < 1 || picksPerCompany < 1 || parallelism < 1) {
            throw new IllegalArgumentException(
                    "lightmove.enrichment.sourcing's company, hit, pick and parallelism limits must be positive");
        }
        if (picksPerCompany > hitsPerCompany) {
            throw new IllegalArgumentException(
                    "lightmove.enrichment.sourcing.picks-per-company cannot exceed hits-per-company");
        }
        if (minPickScore < 1 || minPickScore > 10) {
            throw new IllegalArgumentException("lightmove.enrichment.sourcing.min-pick-score must be 1 to 10");
        }
        if (runDeadline == null || runDeadline.isNegative() || runDeadline.isZero()) {
            throw new IllegalArgumentException("lightmove.enrichment.sourcing.run-deadline must be positive");
        }
        neighbourCountryCodes = neighbourCountryCodes == null ? List.of()
                : neighbourCountryCodes.stream().filter(code -> code != null && !code.isBlank()).toList();
    }
}
