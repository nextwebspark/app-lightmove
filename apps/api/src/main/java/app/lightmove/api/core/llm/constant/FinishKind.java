package app.lightmove.api.core.llm.constant;

import java.util.Locale;

/** Why a model stopped, folded to the columns {@code app_lm_platform_prompt_call_daily} counts. */
public enum FinishKind {
    STOP,
    MAX_TOKENS,
    SAFETY,
    OTHER;

    public static FinishKind of(String finishReason) {
        if (finishReason == null) {
            return OTHER;
        }
        return switch (finishReason.toUpperCase(Locale.ROOT)) {
            case "STOP" -> STOP;
            case "MAX_TOKENS" -> MAX_TOKENS;
            case "SAFETY" -> SAFETY;
            default -> OTHER;
        };
    }
}
