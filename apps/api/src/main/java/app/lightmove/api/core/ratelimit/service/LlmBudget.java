package app.lightmove.api.core.ratelimit.service;

import app.lightmove.api.core.config.LlmRateLimitSettings;
import java.util.function.ToIntFunction;

/**
 * One per-user, per-minute meter {@link LlmBudgetGuard} counts a billed model call against. The meter
 * name and which setting sizes it are data, not a method each — a sixth extraction step wanting its own
 * budget is a new constant here, not a new method on the guard.
 */
public enum LlmBudget {

    /** The demo shortlist endpoint's own meter. */
    SHORTLIST("shortlist", LlmRateLimitSettings::shortlistRequestsPerMinute),

    /** The demo embedding endpoint's own meter. */
    EMBED("embed", LlmRateLimitSettings::embedRequestsPerMinute),

    /** The import's column-mapping call — its own meter, so a large import cannot eat the shortlist a
     *  consultant is about to run. */
    IMPORT_COLUMN_MAPPING("import-column-mapping", LlmRateLimitSettings::defaultRequestsPerMinute),

    /** Step one's "Read from document" — position details extraction. */
    POSITION_EXTRACT("position-extract", LlmRateLimitSettings::defaultRequestsPerMinute),

    /** Step two's "Read from document" — mandate context extraction. */
    CONTEXT_EXTRACT("context-extract", LlmRateLimitSettings::defaultRequestsPerMinute),

    /** Step four's "Read from document" — compensation extraction. */
    COMPENSATION_EXTRACT("compensation-extract", LlmRateLimitSettings::defaultRequestsPerMinute),

    /** Step five's "Read from document" — assessment criteria and competency extraction. */
    ASSESSMENT_EXTRACT("assessment-extract", LlmRateLimitSettings::defaultRequestsPerMinute),

    /** Step three's "Read from document" — reporting-structure extraction. */
    REPORTING_EXTRACT("reporting-extract", LlmRateLimitSettings::defaultRequestsPerMinute);

    private final String meter;
    private final ToIntFunction<LlmRateLimitSettings> callsPerMinute;

    LlmBudget(String meter, ToIntFunction<LlmRateLimitSettings> callsPerMinute) {
        this.meter = meter;
        this.callsPerMinute = callsPerMinute;
    }

    String meter() {
        return meter;
    }

    int callsPerMinute(LlmRateLimitSettings settings) {
        return callsPerMinute.applyAsInt(settings);
    }
}
