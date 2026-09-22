package app.lightmove.api.companydiscovery.dto;

/**
 * Whether this deployment offers AI Research at all, read before the CTA is drawn so an unconfigured
 * deployment shows a disabled button rather than one that fails when pressed.
 */
public record DiscoveryConfigResponse(boolean offered, int dailySearchLimit) {}
