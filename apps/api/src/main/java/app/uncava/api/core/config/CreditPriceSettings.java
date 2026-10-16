package app.uncava.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** What each paid action costs in contact credits — {@code uncava.billing.prices.*}. */
public record CreditPriceSettings(
        @DefaultValue("1") long emailFound,
        @DefaultValue("5") long phoneFound
) {}
