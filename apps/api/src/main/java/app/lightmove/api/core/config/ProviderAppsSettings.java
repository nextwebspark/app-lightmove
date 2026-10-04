package app.lightmove.api.core.config;

/** Uncava's shared OAuth apps — {@code lightmove.outreach.providers.*}, one per provider. */
public record ProviderAppsSettings(
        ProviderAppSettings google,
        ProviderAppSettings microsoft,
        ProviderAppSettings zoom
) {}
