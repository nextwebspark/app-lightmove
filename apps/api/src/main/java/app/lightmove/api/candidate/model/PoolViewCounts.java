package app.lightmove.api.candidate.model;

/** How many people each quick view holds under the other filters, and how many the workspace has. */
public record PoolViewCounts(long all, long mine, long active, long unplaced, long pool) {}
