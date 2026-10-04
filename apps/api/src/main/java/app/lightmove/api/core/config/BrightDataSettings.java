package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bright Data Marketplace credentials and datasets — {@code lightmove.enrichment.brightdata.*}. The
 * timeout and retries are configuration because the vendor's latency moves (measured at 8–25s; it
 * closes the connection itself past 60s).
 */
public record BrightDataSettings(
        String apiKey,
        @DefaultValue("https://api.brightdata.com") String baseUrl,
        @DefaultValue("gd_l1viktl72bvl7bjuj0") String datasetId,
        @DefaultValue("gd_l1vikfnt1wgvvqz95w") String companyDatasetId,

        /**
         * The search answers in well under a second when it is healthy, so this is a ceiling on a bad
         * minute rather than a budget to spend: past it the HarvestAPI fallback is the faster answer.
         * Raise it through the property when the dataset is degraded but still answering.
         */
        @DefaultValue("15s") Duration readTimeout,

        /**
         * None: the HarvestAPI fallback is the second attempt, and asking a search that just took 45
         * seconds the same question again is the worst way to spend the next 45. Nor is a budget here
         * what it reads as — Spring 7.0.8's retry interceptor re-enters itself, so attempts land at
         * {@code (1 + maxRetries)²} (see {@code BrightDataRetryBudgetTest}).
         */
        @DefaultValue("0") int maxRetries,

        /** No cap is published, so calls are paced conservatively rather than discovered as 429s. */
        @DefaultValue("10") int requestsPerSecond
) {}
