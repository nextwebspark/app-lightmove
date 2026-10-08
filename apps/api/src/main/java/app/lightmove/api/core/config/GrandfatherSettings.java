package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The one-off promotional contact credits every workspace that exists when enforcement first boots is given —
 * {@code lightmove.billing.grandfather.*}; {@code 0} credits gives none.
 */
public record GrandfatherSettings(
        @DefaultValue("200") long credits,
        @DefaultValue("90d") Duration validFor
) {}
