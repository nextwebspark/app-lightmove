package app.lightmove.api.report.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts things by key and answers the leaders. Every chapter ranks something — sectors, hubs,
 * nationalities, employers — and folds the tail into "Other"; this is that one operation, so each
 * ranks the same way: most first, first-seen first among equals.
 */
final class Tally<K> {

    private final Map<K, Integer> counts = new LinkedHashMap<>();

    void add(K key) {
        counts.merge(key, 1, Integer::sum);
    }

    int of(K key) {
        return counts.getOrDefault(key, 0);
    }

    /** The {@code limit} most-counted keys, in rank order. */
    List<K> top(int limit) {
        List<Map.Entry<K, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort(Comparator.comparingInt((Map.Entry<K, Integer> entry) -> entry.getValue()).reversed());
        return ranked.stream().limit(limit).map(Map.Entry::getKey).toList();
    }

    /** How much of the total sits outside the keys kept. */
    int outside(Collection<K> kept) {
        return total() - kept.stream().mapToInt(this::of).sum();
    }

    private int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
