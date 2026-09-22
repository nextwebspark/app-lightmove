package app.lightmove.api.companydiscovery.model;

/**
 * What the port is asked. Free text plus a couple of constraints, and deliberately nothing shaped
 * like the saved Apollo filter: bands, sector groups and market segments are the universe's own
 * vocabulary, and a question put to a web search is a sentence. Conflating the two is the trap.
 */
public record DiscoveryQuery(String question, String country, int limit) {}
