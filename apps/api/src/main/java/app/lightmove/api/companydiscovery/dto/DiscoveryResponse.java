package app.lightmove.api.companydiscovery.dto;

import java.util.List;

/**
 * What one search answered.
 *
 * <p>{@code mode} and {@code provider} are on the wire rather than only in the log because a
 * grounded answer and a fallback are different products, and an empty list from an unreachable
 * provider is a different fact from an empty market. The panel says which.
 *
 * <p>{@code searchesLeftToday} is what the workspace has not yet spent, so the screen can warn
 * before a consultant presses the button that will be refused.
 */
public record DiscoveryResponse(List<DiscoveredCompanyDto> companies, String mode, String provider,
                                int searchesLeftToday) {}
