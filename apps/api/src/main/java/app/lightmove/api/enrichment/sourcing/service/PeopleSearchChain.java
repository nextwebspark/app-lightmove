package app.lightmove.api.enrichment.sourcing.service;

import java.util.List;

/**
 * The people searches a run asks in turn at each company: the configured index first, then the one it
 * falls back to when that finds nobody or fails — ContactOut, then Bright Data's dataset.
 */
public record PeopleSearchChain(List<PeopleSearch> inOrder) {

    public PeopleSearchChain {
        if (inOrder == null || inOrder.isEmpty()) {
            throw new IllegalArgumentException("A people search chain needs at least one search");
        }
        inOrder = List.copyOf(inOrder);
    }
}
