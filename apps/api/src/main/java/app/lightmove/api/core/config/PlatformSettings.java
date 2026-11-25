package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationStyle;

/** The platform console's settings — {@code lightmove.platform.*}. */
public record PlatformSettings(
        /** Off until every super admin of the deployment has enrolled: on, one without a factor reaches no platform route. */
        @DefaultValue("false") boolean requireTwoFactor,

        /** How long a right code lets high-impact actions through before the next one is asked for. */
        @DefaultValue(PlatformSettings.DEFAULT_STEP_UP_WINDOW) Duration stepUpWindow,

        PlatformEvalSettings eval
) {

    static final String DEFAULT_STEP_UP_WINDOW = "10m";

    public PlatformSettings {
        eval = eval == null ? PlatformEvalSettings.defaults() : eval;
    }

    public static PlatformSettings defaults() {
        return new PlatformSettings(false, DurationStyle.detectAndParse(DEFAULT_STEP_UP_WINDOW), null);
    }
}
