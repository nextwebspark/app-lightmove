package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** How a super admin's overrides reach every instance — {@code lightmove.platform.*}. */
public record PlatformConfigSettings(

        /**
         * How long an instance trusts the overrides it loaded before asking the stored version again. A save's
         * NOTIFY reloads them at once; this is the floor for one lost while the listener was reconnecting.
         */
        @DefaultValue("30s") Duration configCheckInterval
) {

    public PlatformConfigSettings {
        if (configCheckInterval == null || configCheckInterval.isNegative()) {
            throw new IllegalArgumentException("lightmove.platform.config-check-interval must not be negative");
        }
    }
}
