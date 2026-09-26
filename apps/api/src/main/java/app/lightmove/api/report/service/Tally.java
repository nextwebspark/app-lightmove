package app.lightmove.api.report.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts by key and ranks the leaders — most first, first-seen first among equals — so every chapter
 * ranks and folds its tail into "Other" the same way.
 */
final class Tally<K> {

    private final Map<K, Integer> counts = new LinkedHashMap<>();

    void add(K key) {
        counts.merge(key, 1, Integer::sum);
    }

    int of(K key) {
        return counts.getOrDefault(key, 0);
    }

    List<K> top(int limit) {
        List<Map.Entry<K, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort(Comparator.comparingInt((Map.Entry<K, Integer> entry) -> entry.getValue()).reversed());
        return ranked.stream().limit(limit).map(Map.Entry::getKey).toList();
    }

    int outside(Collection<K> kept) {
        return total() - kept.stream().mapToInt(this::of).sum();
    }

    private int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
