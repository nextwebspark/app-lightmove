package app.lightmove.api.core.platformconfig.model;

/** The {@code lm_platform_config} payload: a wake-up naming the version, never a value or a prompt's body. */
public record PlatformConfigChanged(long version) {}
