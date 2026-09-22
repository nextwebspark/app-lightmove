package app.lightmove.api.companydiscovery.model;

import app.lightmove.api.companydiscovery.constant.DiscoveryMode;
import java.util.List;

/**
 * What a provider answered, and how. An empty list is a legitimate answer and is never a failure;
 * the mode says whether it is an empty market or an absent provider.
 */
public record DiscoveryAnswer(List<DiscoveredCandidate> candidates, DiscoveryMode mode) {

    public static DiscoveryAnswer none(DiscoveryMode mode) {
        return new DiscoveryAnswer(List.of(), mode);
    }
}
