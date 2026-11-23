package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationStyle;

/** What one eval run in the platform console may spend — {@code lightmove.platform.eval.*}. */
public record PlatformEvalSettings(
        /** The model calls one run may make. A start planning more is refused, and a run stops on reaching it. */
        @DefaultValue(PlatformEvalSettings.DEFAULT_MAX_CALLS_PER_RUN) int maxCallsPerRun,

        /**
         * How long a running run may go without a sign of life before another instance takes it over. Longer than
         * one ask can take, its repair included: the sign is raised before each.
         */
        @DefaultValue(PlatformEvalSettings.DEFAULT_STALE_AFTER) Duration staleAfter,

        /**
         * The model a version of the eval judge naming none is answered by. Blank: the deployment's own, as for
         * any other prompt.
         */
        @DefaultValue(PlatformEvalSettings.DEFAULT_JUDGE_MODEL) String judgeModel
) {

    static final String DEFAULT_MAX_CALLS_PER_RUN = "200";
    static final String DEFAULT_STALE_AFTER = "10m";
    static final String DEFAULT_JUDGE_MODEL = "gemini-3.8-flash";

    public PlatformEvalSettings {
        if (maxCallsPerRun < 2) {
            throw new IllegalArgumentException(
                    "lightmove.platform.eval.max-calls-per-run must allow a case both its answers, but was "
                            + maxCallsPerRun);
        }
    }

    public static PlatformEvalSettings defaults() {
        return new PlatformEvalSettings(Integer.parseInt(DEFAULT_MAX_CALLS_PER_RUN),
                DurationStyle.detectAndParse(DEFAULT_STALE_AFTER), DEFAULT_JUDGE_MODEL);
    }

    /** Null where {@code judge-model} is blank. */
    public String pinnedJudgeModel() {
        return judgeModel == null || judgeModel.isBlank() ? null : judgeModel.strip();
    }
}
