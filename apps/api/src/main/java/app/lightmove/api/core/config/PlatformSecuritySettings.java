package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** What the platform routes ask of a super admin beyond the role — {@code lightmove.platform.*}. */
public record PlatformSecuritySettings(
        /** Off until every super admin of the deployment has enrolled: on, one without a factor reaches no platform route. */
        @DefaultValue("false") boolean requireTwoFactor,

        /** How long a right code lets high-impact actions through before the next one is asked for. */
        @DefaultValue("10m") Duration stepUpWindow
) {

    public static PlatformSecuritySettings defaults() {
        return new PlatformSecuritySettings(false, Duration.ofMinutes(10));
    }
}
