package app.uncava.api.core.config;

/** Uncava's shared OAuth apps — {@code uncava.outreach.providers.*}, one per provider. */
public record ProviderAppsSettings(
        ProviderAppSettings google,
        ProviderAppSettings microsoft,
        ProviderAppSettings zoom
) {}
