package app.lightmove.api.company.model;

import java.util.List;

/**
 * A CoreSignal search answered: the company ids we kept, in the provider's sort order
 * (revenue-desc — fixed here, which is what lets collected results stream into the UI without
 * reshuffling), and the provider's total match count beyond the ids kept.
 */
public record CoreSignalSearchResult(List<Long> ids, long totalMatched) {}
