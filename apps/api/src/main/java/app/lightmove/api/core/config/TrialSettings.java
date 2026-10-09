package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The trial a newly founded workspace starts on — {@code lightmove.billing.trial.*}: how long, and the contact credits
 * it may spend in that time; {@code 0} credits gives none. Off, a new workspace has no subscription until one is
 * bought or invoiced, and nothing locks.
 */
public record TrialSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("14d") Duration length,
        @DefaultValue("50") long credits
) {}
